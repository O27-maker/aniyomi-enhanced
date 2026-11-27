package eu.kanade.tachiyomi.source.model

/**
 * Updated manga information returned by [eu.kanade.tachiyomi.source.MangaSource.getMangaUpdate].
 */
class SMangaUpdate(
    val manga: SManga,
    val chapters: List<SChapter>,
)
