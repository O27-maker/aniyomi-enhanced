package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import eu.kanade.tachiyomi.extension.manga.api.NetworkMangaExtensionStore
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val protoBuf: ProtoBuf = ProtoBuf {}

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.pb"))
                .awaitSuccess()

            response.body.source().decompressIfGzipped().use { source ->
                val index = when (source.peek().readByte()) {
                    // JSON-encoded protobuf-compatible index.
                    0x7B.toByte() -> json.decodeFromJsonElement<NetworkMangaExtensionStore>(
                        source.readUtf8().let(json::parseToJsonElement).normalizeNumericStringFields(),
                    )
                    else -> protoBuf.decodeFromByteArray<NetworkMangaExtensionStore>(source.readByteArray())
                }
                val extensionList = index.extensionList ?: index.extensionListUrl?.let { url ->
                    val listResponse = networkService.client.newCall(GET(url)).awaitSuccess()
                    listResponse.body.source().decompressIfGzipped().use { listSource ->
                        when (listSource.peek().readByte()) {
                            // JSON-encoded protobuf-compatible extension list.
                            0x7B.toByte() -> json.decodeFromJsonElement<NetworkMangaExtensionStore.ExtensionList>(
                                listSource.readUtf8().let(json::parseToJsonElement).normalizeNumericStringFields(),
                            )
                            else -> protoBuf.decodeFromByteArray<NetworkMangaExtensionStore.ExtensionList>(
                                listSource.readByteArray(),
                            )
                        }
                    }
                } ?: throw IllegalArgumentException("Repository index has no extension list")

                extensionList.toMangaExtensions(repoBaseUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()
        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }
        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val availableExt = extensions.find { it.pkgName == installedExt.pkgName } ?: continue
            if (availableExt.versionCode > installedExt.versionCode ||
                availableExt.libVersion > installedExt.libVersion
            ) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }
        return extensionsWithUpdate
    }

    /**
     * Protobuf JSON represents 64-bit integer fields as quoted decimal strings.
     * Normalize those values before decoding into Kotlin Long properties.
     */
    private fun JsonElement.normalizeNumericStringFields(): JsonElement = when (this) {
        is JsonObject -> JsonObject(
            mapValues { (key, value) ->
                if (key == "id" || key == "versionCode") {
                    val primitive = value as? JsonPrimitive
                    primitive?.takeIf { it.isString }?.content?.toLongOrNull()
                        ?.let(::JsonPrimitive)
                        ?: value.normalizeNumericStringFields()
                } else {
                    value.normalizeNumericStringFields()
                }
            },
        )
        is JsonArray -> JsonArray(map { it.normalizeNumericStringFields() })
        else -> this
    }

    private fun NetworkMangaExtensionStore.ExtensionList.toMangaExtensions(
        repoUrl: String,
    ): List<MangaExtension.Available> {
        return extensions.mapNotNull { extension ->
            val libVersion = extension.extensionLib.toDoubleOrNull() ?: return@mapNotNull null
            if (libVersion < MangaExtensionLoader.LIB_VERSION_MIN ||
                libVersion > MangaExtensionLoader.LIB_VERSION_MAX
            ) {
                return@mapNotNull null
            }

            val languages = extension.sources.map { it.language }.toSet()
            MangaExtension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                versionName = extension.versionName,
                versionCode = extension.versionCode,
                libVersion = libVersion,
                lang = if (languages.size == 1) languages.first() else "all",
                isNsfw = extension.contentWarning >= NetworkMangaExtensionStore.ContentWarning.MIXED,
                sources = extension.sources.map { source ->
                    MangaExtension.Available.MangaSource(
                        id = source.id,
                        lang = source.language,
                        name = source.name,
                        baseUrl = source.homeUrl,
                    )
                },
                // The protobuf format supplies absolute resource URLs.
                apkName = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl,
                repoUrl = repoUrl,
            )
        }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return if (extension.apkName.startsWith("https://") || extension.apkName.startsWith("http://")) {
            extension.apkName
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }
        return if (isGzip) gzip().buffer() else this
    }
}
