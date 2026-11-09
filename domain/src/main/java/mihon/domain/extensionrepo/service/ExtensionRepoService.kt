package mihon.domain.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client
    private val protoBuf = ProtoBuf {}

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            try {
                with(json) {
                    client.newCall(GET("$repo/repo.json"))
                        .awaitSuccess()
                        .parseAs<ExtensionRepoMetaDto>()
                        .toExtensionRepo(baseUrl = repo)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }

    /**
     * Manga stores publish their trust metadata in index.pb itself. Do not use
     * repo.json here: that is the legacy anime repository metadata format.
     */
    suspend fun fetchMangaRepoDetails(repo: String): ExtensionRepo? {
        return withIOContext {
            try {
                val response = client.newCall(GET("$repo/index.pb")).awaitSuccess()
                response.use {
                    val raw = it.body.bytes()
                    val bytes = if (
                        raw.size >= 2 &&
                        raw[0] == 0x1f.toByte() &&
                        raw[1] == 0x8b.toByte()
                    ) {
                        okio.Buffer().write(raw).let { buffer ->
                            (buffer as okio.Source).gzip().buffer().readByteArray()
                        }
                    } else {
                        raw
                    }
                    val index = if (bytes.firstOrNull() == 0x7B.toByte()) {
                        json.decodeFromString<MangaExtensionStoreIndex>(bytes.decodeToString())
                    } else {
                        protoBuf.decodeFromByteArray<MangaExtensionStoreIndex>(bytes)
                    }
                    index.toExtensionRepo(repo)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch manga store index from $repo" }
                null
            }
        }
    }
}
