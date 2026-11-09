package mihon.domain.extensionrepo.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extensionrepo.model.ExtensionRepo

@Serializable
data class ExtensionRepoMetaDto(
    val meta: ExtensionRepoDto,
)

@Serializable
data class ExtensionRepoDto(
    val name: String,
    val shortName: String? = null,
    val website: String,
    val signingKeyFingerprint: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MangaExtensionStoreIndex(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact = Contact(),
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )
}

fun ExtensionRepoMetaDto.toExtensionRepo(baseUrl: String): ExtensionRepo {
    return ExtensionRepo(
        baseUrl = baseUrl,
        name = meta.name,
        shortName = meta.shortName,
        website = meta.website,
        signingKeyFingerprint = meta.signingKeyFingerprint,
    )
}

internal fun MangaExtensionStoreIndex.toExtensionRepo(baseUrl: String): ExtensionRepo {
    require(name.isNotBlank()) { "Extension store index has no name" }
    require(signingKey.isNotBlank()) { "Extension store index has no signing key" }
    return ExtensionRepo(
        baseUrl = baseUrl,
        name = name,
        shortName = badgeLabel.ifBlank { null },
        website = contact.website,
        signingKeyFingerprint = signingKey,
    )
}
