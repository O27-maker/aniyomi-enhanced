package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.entries.anime.model.copyFrom
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAnimeSourceList(
    animeList: LazyPagingItems<StateFlow<Anime>>,
    entries: Int,
    topBarHeight: Int,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
    source: AnimeSource?,
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
                if (animeList.loadState.prepend is LoadState.Loading) {
                    BrowseSourceLoadingItem()
                }
            }

            items(count = animeList.itemCount) { index ->
                val anime by animeList[index]?.collectAsState() ?: return@items
                BrowseAnimeSourceListItem(
                    anime = anime,
                    source = source,
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onAnimeLongClick(anime) },
                    entries = entries,
                    containerHeight = containerHeightPx - topBarHeight,
                )
            }

            item {
                if (animeList.loadState.refresh is LoadState.Loading ||
                    animeList.loadState.append is LoadState.Loading
                ) {
                    BrowseSourceLoadingItem()
                }
            }
        }
    }
}

@Composable
private fun BrowseAnimeSourceListItem(
    anime: Anime,
    source: AnimeSource?,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    entries: Int,
    containerHeight: Int,
) {
    var displayAnime by remember(anime.url, anime.source) { mutableStateOf(anime) }
    LaunchedEffect(anime.url, anime.source) {
        if (source != null) {
            displayAnime = try {
                displayAnime.copyFrom(source.getAnimeDetails(displayAnime.toSAnime()))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                displayAnime
            }
        }
    }
    EntryListItem(
        title = displayAnime.title,
        author = displayAnime.author ?: displayAnime.artist,
        description = displayAnime.description,
        status = displayAnime.status,
        coverData = AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = anime.favorite,
            url = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        ),
        coverAlpha = if (anime.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = anime.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
        entries = entries,
        containerHeight = containerHeight,
    )
}
