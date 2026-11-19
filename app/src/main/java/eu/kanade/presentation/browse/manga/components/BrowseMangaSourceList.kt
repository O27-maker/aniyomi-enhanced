package eu.kanade.presentation.browse.manga.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.entries.manga.model.copyFrom
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseMangaSourceList(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    entries: Int,
    topBarHeight: Int,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    source: CatalogueSource?,
) {
    val sourceListState = rememberLazyListState()
    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }

        LazyColumn(
            state = sourceListState,
            contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
        ) {
            item {
                if (mangaList.loadState.prepend is LoadState.Loading) {
                    BrowseSourceLoadingItem()
                }
            }

            items(count = mangaList.itemCount) { index ->
                val manga by mangaList[index]?.collectAsState() ?: return@items
                BrowseMangaSourceListItem(
                    manga = manga,
                    source = source,
                    onClick = { onMangaClick(manga) },
                    onLongClick = { onMangaLongClick(manga) },
                    entries = entries,
                    containerHeight = containerHeightPx - topBarHeight,
                )
            }

            item {
                if (mangaList.loadState.refresh is LoadState.Loading ||
                    mangaList.loadState.append is LoadState.Loading
                ) {
                    BrowseSourceLoadingItem()
                }
            }
        }
    }
}

@Composable
private fun BrowseMangaSourceListItem(
    manga: Manga,
    source: CatalogueSource?,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    entries: Int,
    containerHeight: Int,
) {
    var displayManga by remember(manga.url, manga.source) { mutableStateOf(manga) }
    LaunchedEffect(manga.url, manga.source) {
        if (source != null) {
            displayManga = try {
                displayManga.copyFrom(source.getMangaDetails(displayManga.toSManga()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                displayManga
            }
        }
    }
    EntryListItem(
        title = displayManga.title,
        author = displayManga.author ?: displayManga.artist,
        description = displayManga.description,
        status = displayManga.status,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
        entries = entries,
        containerHeight = containerHeight,
    )
}
