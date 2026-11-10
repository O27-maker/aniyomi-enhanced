package eu.kanade.tachiyomi.extension.manga.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
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
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi/Aniyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object MangaExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustMangaExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_EXTENSION_NAME = "tachiyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    private val SUPPORTED_LIB_VERSIONS = setOf(1.4, 1.6)
    const val LIB_VERSION_MIN = 1.4
    const val LIB_VERSION_MAX = 1.6

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
        val currentExtension = getMangaExtensionPackageInfoFromPkgName(
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
                MangaExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                MangaExtensionInstallReceiver.notifyAdded(context, extension.packageName)
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
    fun loadMangaExtensions(context: Context): List<MangaLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        // Keep package discovery diagnosable: packages can be installed but omitted
        // from the extension list when their manifest does not declare the feature.
        installedPkgs
            .filter { it.packageName.contains(".extension.") }
            .forEach { pkgInfo ->
                logPackageDiscovery(pkgInfo)
            }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { MangaExtensionInfo(packageInfo = it, isShared = true) }

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
            ?.map { MangaExtensionInfo(packageInfo = it, isShared = false) }
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
                async { loadMangaExtensionSafely(context, it) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadMangaExtensionFromPkgName(context: Context, pkgName: String): MangaLoadResult {
        val extensionPackage = getMangaExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return MangaLoadResult.Error
        }
        return loadMangaExtensionSafely(context, extensionPackage)
    }

    fun getMangaExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getMangaExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getMangaExtensionInfoFromPkgName(context: Context, pkgName: String): MangaExtensionInfo? {
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
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .also { logPackageDiscovery(it) }
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            logcat(LogPriority.WARN) { "Manga extension package is not installed: $pkgName" }
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * One malformed installed APK must not abort discovery of every other extension.
     * Keep it visible as installed-but-not-loaded, matching Mihon's NotLoaded behavior.
     */
    private suspend fun loadMangaExtensionSafely(
        context: Context,
        extensionInfo: MangaExtensionInfo,
    ): MangaLoadResult {
        return try {
            loadMangaExtension(context, extensionInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val pkgInfo = extensionInfo.packageInfo
            val appInfo = pkgInfo.applicationInfo
            val pkgName = pkgInfo.packageName
            val pkgManager = context.packageManager
            val name = appInfo?.metaData?.getString(METADATA_EXTENSION_NAME)
                ?: appInfo?.let {
                    runCatching {
                        pkgManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ")
                    }.getOrNull()
                }
                ?: pkgName
            val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: "Unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
            val libVersion = appInfo?.metaData
                ?.getFloat(METADATA_EXTENSION_LIB, 0f)
                ?.takeIf { it > 0f }
                ?.toString()
                ?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
                ?: 0.0
            val contentWarning = appInfo?.metaData?.getInt(METADATA_CONTENT_WARNING, -1) ?: -1
            val isNsfw = if (contentWarning >= 0) {
                contentWarning >= 1
            } else {
                appInfo?.metaData?.getInt(METADATA_NSFW, 0) == 1
            }

            logcat(LogPriority.ERROR, e) { "Extension load error: $name ($pkgName)" }
            MangaLoadResult.NotLoaded(
                MangaExtension.Installed(
                    name = name,
                    pkgName = pkgName,
                    versionName = versionName,
                    versionCode = versionCode,
                    libVersion = libVersion,
                    lang = "",
                    isNsfw = isNsfw,
                    sources = emptyList(),
                    pkgFactory = appInfo?.metaData?.getString(METADATA_SOURCE_FACTORY),
                    icon = appInfo?.let { runCatching { it.loadIcon(pkgManager) }.getOrNull() },
                    isShared = extensionInfo.isShared,
                    loadError = e.message ?: e::class.java.simpleName,
                ),
            )
        }
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadMangaExtension(context: Context, extensionInfo: MangaExtensionInfo): MangaLoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = appInfo.metaData?.getString(METADATA_EXTENSION_NAME)
            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        fun notLoaded(
            reason: String,
            libVersion: Double? = null,
            isNsfw: Boolean = false,
        ): MangaLoadResult.NotLoaded {
            val extension = MangaExtension.Installed(
                name = extName,
                pkgName = pkgName,
                versionName = versionName?.takeIf { it.isNotBlank() } ?: "Unknown",
                versionCode = versionCode,
                libVersion = libVersion ?: 0.0,
                lang = "",
                isNsfw = isNsfw,
                sources = emptyList(),
                pkgFactory = appInfo.metaData?.getString(METADATA_SOURCE_FACTORY),
                icon = runCatching { appInfo.loadIcon(pkgManager) }.getOrNull(),
                isShared = extensionInfo.isShared,
                loadError = reason,
            )
            return MangaLoadResult.NotLoaded(extension)
        }

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return notLoaded("Missing version name")
        }

        // Validate lib version
        val metadataLibVersion = appInfo.metaData?.getFloat(METADATA_EXTENSION_LIB, 0f)?.takeIf { it > 0f }?.toString()?.toDouble()
        val libVersion = metadataLibVersion ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return notLoaded("Unsupported extension library version: $libVersion", libVersion)
        }

        val contentWarning = appInfo.metaData?.getInt(METADATA_CONTENT_WARNING, -1) ?: -1
        val isNsfw = if (contentWarning >= 0) {
            contentWarning >= 1
        } else {
            appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return notLoaded("Extension package is not signed", libVersion, isNsfw)
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = MangaExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
            return MangaLoadResult.Untrusted(extension)
        }

        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return notLoaded("NSFW extension is disabled in source settings", libVersion, isNsfw)
        }

        val classLoader = try {
            MangaDelegateLastPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return notLoaded(e.message ?: "Unable to create extension class loader", libVersion, isNsfw)
        }

        val sourceClasses = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
        if (sourceClasses.isNullOrBlank()) {
            logcat(LogPriority.ERROR) { "Missing source class metadata for $pkgName" }
            return notLoaded("Missing source class metadata", libVersion, isNsfw)
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
                        is MangaSource -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: LinkageError) {
                    try {
                        val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                        when (
                            val obj = Class.forName(
                                it,
                                false,
                                fallBackClassLoader,
                            ).getDeclaredConstructor().newInstance()
                        ) {
                            is MangaSource -> {
                                listOf(obj)
                            }
                            is SourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                        return notLoaded(e.message ?: "Unable to load source class $it", libVersion, isNsfw)
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    return notLoaded(e.message ?: "Unable to load source class $it", libVersion, isNsfw)
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = MangaExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return MangaLoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: MangaExtensionInfo?, private: MangaExtensionInfo?): MangaExtensionInfo? {
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
     * Logs the manifest information used by manga extension discovery.
     * This is intentionally diagnostic only; it does not relax validation.
     */
    private fun logPackageDiscovery(pkgInfo: PackageInfo) {
        val features = pkgInfo.reqFeatures.orEmpty().mapNotNull { it.name }
        val isExtension = EXTENSION_FEATURE in features
        val message = "Manga extension discovery: package=${pkgInfo.packageName}, " +
            "featurePresent=$isExtension, reqFeatures=$features, " +
            "metadataKeys=${pkgInfo.applicationInfo?.metaData?.keySet()?.sorted()}"
        Log.w("MangaExtensionLoader", message)
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
            val signingInfo = pkgInfo.signingInfo ?: return null
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

    private data class MangaExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/**
 * Mihon-compatible parent-last loader for manga extension APKs.
 *
 * The boot class loader is checked first, then the extension APK, then the app.
 * Using the app/system class loader before the APK can shadow classes bundled by an
 * extension and cause its source classes to fail verification or instantiation.
 */
private class MangaDelegateLastPathClassLoader(
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
