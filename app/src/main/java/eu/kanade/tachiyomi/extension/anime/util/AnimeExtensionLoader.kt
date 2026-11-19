package eu.kanade.tachiyomi.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * Class that handles the loading of the extensions installed in the system.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object AnimeExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
    private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
    private const val METADATA_HAS_README = "tachiyomi.animeextension.hasReadme"
    private const val METADATA_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
    private const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"

    private const val METADATA_NAME = "aniyomix.name"
    private const val METADATA_EXTENSION_LIB = "aniyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "aniyomix.contentWarning"
    private const val METADATA_IS_TORRENT = "aniyomix.torrent"

    private val SUPPORTED_LIB_VERSIONS = listOf(14.0, 16.0, 17.0)

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getAnimeExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val target = File(
            getPrivateExtensionDir(context),
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                AnimeExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                AnimeExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadExtensions(context: Context): List<AnimeLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { AnimeExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { AnimeExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtensionSafely(context, it) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * A broken installed APK must not abort discovery of every other anime extension.
     * Preserve its package metadata so it remains visible in the Installed tab.
     */
    private suspend fun loadExtensionSafely(
        context: Context,
        extensionInfo: AnimeExtensionInfo,
    ): AnimeLoadResult {
        return try {
            loadExtension(context, extensionInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val pkgInfo = extensionInfo.packageInfo
            val appInfo = pkgInfo.applicationInfo
            val pkgManager = context.packageManager
            val pkgName = pkgInfo.packageName
            val name = appInfo?.metaData?.getString(METADATA_NAME)
                ?: appInfo?.let {
                    runCatching {
                        pkgManager.getApplicationLabel(it).toString().substringAfter("Aniyomi: ")
                    }.getOrNull()
                }
                ?: pkgName
            val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: "Unknown"
            val libVersion = appInfo?.metaData?.getInt(METADATA_EXTENSION_LIB, 0)
                ?.takeIf { it > 0 }?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
                ?: 0.0
            val isNsfw = (appInfo?.metaData?.getInt(METADATA_CONTENT_WARNING, 0) ?: 0) > 0 ||
                (appInfo?.metaData?.getInt(METADATA_NSFW, 0) ?: 0) == 1
            val isTorrent = appInfo?.metaData?.getBoolean(METADATA_IS_TORRENT, false) == true ||
                appInfo?.metaData?.getInt(METADATA_TORRENT, 0) == 1

            logcat(LogPriority.ERROR, e) { "Anime extension load error: $name ($pkgName)" }
            Log.e("AnimeExtensionLoader", "Anime extension load error: $name ($pkgName)", e)
            AnimeLoadResult.NotLoaded(
                AnimeExtension.Installed(
                    name = name,
                    pkgName = pkgName,
                    versionName = versionName,
                    versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                    libVersion = libVersion,
                    lang = "",
                    isNsfw = isNsfw,
                    isTorrent = isTorrent,
                    pkgFactory = appInfo?.metaData?.getString(METADATA_SOURCE_FACTORY),
                    sources = emptyList(),
                    icon = appInfo?.let { runCatching { it.loadIcon(pkgManager) }.getOrNull() },
                    isShared = extensionInfo.isShared,
                    loadError = e.message ?: e::class.java.simpleName,
                ),
            )
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): AnimeLoadResult {
        val extensionPackage = getAnimeExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return AnimeLoadResult.Error
        }
        return loadExtensionSafely(context, extensionPackage)
    }

    fun getAnimeExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getAnimeExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getAnimeExtensionInfoFromPkgName(context: Context, pkgName: String): AnimeExtensionInfo? {
        val privateExtensionFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(
                privateExtensionFile.absolutePath,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(context: Context, extensionInfo: AnimeExtensionInfo): AnimeLoadResult {
        val pkgManager = context.packageManager

        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = appInfo.metaData?.getString(METADATA_NAME)
            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        fun notLoaded(reason: String, libVersion: Double? = null, isNsfw: Boolean = false, isTorrent: Boolean = false) =
            AnimeLoadResult.NotLoaded(
                AnimeExtension.Installed(
                    name = extName,
                    pkgName = pkgName,
                    versionName = versionName?.takeIf { it.isNotBlank() } ?: "Unknown",
                    versionCode = versionCode,
                    libVersion = libVersion ?: 0.0,
                    lang = "",
                    isNsfw = isNsfw,
                    isTorrent = isTorrent,
                    pkgFactory = appInfo.metaData?.getString(METADATA_SOURCE_FACTORY),
                    sources = emptyList(),
                    icon = runCatching { appInfo.loadIcon(pkgManager) }.getOrNull(),
                    isShared = extensionInfo.isShared,
                    loadError = reason,
                ),
            )

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return notLoaded("Missing version name")
        }

        // Validate lib version
        val libVersion = appInfo.metaData?.getInt(METADATA_EXTENSION_LIB, 0)
            .takeUnless { it == 0 }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only version(s) ${SUPPORTED_LIB_VERSIONS.joinToString()} are supported"
            }
            return notLoaded("Unsupported extension library version: $libVersion", libVersion)
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return notLoaded("Extension package is not signed", libVersion)
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = AnimeExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            logcat(LogPriority.WARN, message = { "Extension $pkgName isn't trusted" })
            return AnimeLoadResult.Untrusted(extension)
        }

        val isNsfw = (appInfo.metaData?.getInt(METADATA_CONTENT_WARNING, 0) ?: 0) > 0 ||
            (appInfo.metaData?.getInt(METADATA_NSFW, 0) ?: 0) == 1
        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return notLoaded("NSFW extension is disabled in source settings", libVersion, isNsfw)
        }

        val isTorrent = appInfo.metaData?.getBoolean(METADATA_IS_TORRENT, false) == true ||
            appInfo.metaData?.getInt(METADATA_TORRENT, 0) == 1
        val classLoader = try {
            AnimeDelegateLastPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return notLoaded(e.message ?: "Unable to create extension class loader", libVersion, isNsfw, isTorrent)
        }

        val sourceClasses = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
        if (sourceClasses.isNullOrBlank()) {
            logcat(LogPriority.ERROR) { "Missing anime source class metadata for $pkgName" }
            return notLoaded("Missing source class metadata", libVersion, isNsfw, isTorrent)
        }

        val sources = sourceClasses.split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is AnimeSource -> listOf(obj)
                        is AnimeSourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: LinkageError) {
                    val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                    try {
                        when (
                            val obj = Class.forName(
                                it,
                                false,
                                fallBackClassLoader,
                            ).getDeclaredConstructor().newInstance()
                        ) {
                            is AnimeSource -> {
                                listOf(obj)
                            }
                            is AnimeSourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                        Log.e(
                            "AnimeExtensionLoader",
                            "Extension source class load failed: $extName ($pkgName), class=$it, loader=${fallBackClassLoader.javaClass.name}",
                            e,
                        )
                        return notLoaded(e.message ?: "Unable to load source class $it", libVersion, isNsfw, isTorrent)
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    Log.e(
                        "AnimeExtensionLoader",
                        "Extension source class load failed: $extName ($pkgName), class=$it, loader=${classLoader.javaClass.name}",
                        e,
                    )
                    return notLoaded(e.message ?: "Unable to load source class $it", libVersion, isNsfw, isTorrent)
                }
            }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            isTorrent = isTorrent,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return AnimeLoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: AnimeExtensionInfo?, private: AnimeExtensionInfo?): AnimeExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class AnimeExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/**
 * Mihon-compatible parent-last loader for anime extension APKs.
 *
 * The boot class loader is checked first, then the extension APK, then the app.
 * This prevents app classes from shadowing classes bundled by an extension.
 */
private class AnimeDelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        val fromExtension = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }

        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromExtension
        }
    }

    override fun getResource(name: String?): URL? {
        return bootClassLoader?.getResource(name)
            ?: findResource(name)
            ?: parent?.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
