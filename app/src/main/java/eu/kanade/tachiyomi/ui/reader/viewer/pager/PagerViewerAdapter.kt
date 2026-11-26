package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext: android.content.Context? = viewer.activity.createReaderThemeContext()

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition.
        if (chapters.prevChapter != null) {
            // We only need to add the last few pages of the previous chapter, because it'll be
            // selected as the current chapter when one of those pages is selected.
            val prevPages = chapters.prevChapter.pages
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(2))
            }
        }

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        var insertPageLastPage: InsertPage? = null

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }

            newItems.addAll(pages)
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        if (chapters.nextChapter != null) {
            // Add at most two pages, because this chapter will be selected before the user can
            // swap more pages.
            val nextPages = chapters.nextChapter.pages
            if (nextPages != null) {
                newItems.addAll(nextPages.take(2))
            }
        }

        // Resets double-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        if (viewer is R2LPagerViewer) {
            newItems.reverse()
        }

        clearSpreadProjection()
        preprocessed = mutableMapOf()
        items = newItems
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage!!)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    private var spreadProjection: SpreadAdapterProjection<Any>? = null
    private var spreadIsR2L: Boolean = false

    override fun getCount(): Int = spreadProjection?.units?.size ?: items.size

    /**
     * Install an immutable display projection only while it describes the current source
     * sequence. The source list remains authoritative for chapter and page operations.
     */
    internal fun setSpreadProjection(
        projection: SpreadAdapterProjection<Any>,
        isR2L: Boolean,
    ): Boolean {
        if (!projection.matchesSource(items)) return false
        spreadProjection = projection
        spreadIsR2L = isR2L
        notifyDataSetChanged()
        return true
    }

    internal fun clearSpreadProjection() {
        if (spreadProjection == null) return
        spreadProjection = null
        notifyDataSetChanged()
    }

    internal fun sourceItemAtDisplay(displayIndex: Int): Any? {
        val projection = spreadProjection ?: return items.getOrNull(displayIndex)
        return SpreadAdapterNavigationMap.adapterIndexForDisplay(
            projection.units,
            displayIndex,
        )?.let(items::getOrNull)
    }

    internal fun displayIndexForSource(sourceIndex: Int): Int {
        val projection = spreadProjection ?: return sourceIndex.takeIf { it in items.indices } ?: -1
        if (!projection.matchesSource(items)) return -1
        return SpreadAdapterNavigationMap.displayIndexForAdapter(projection.units, sourceIndex)
    }

    /**
     * Permanently dispose reader holders when the pager adapter removes their views.
     * A presentation container may wrap the original holders, so walk the complete
     * removed view tree rather than checking only its root.
     * Ordinary detach/reattach remains reusable until the adapter destroys the tree.
     */
    override fun destroyView(container: ViewGroup, position: Int, view: View) {
        disposeViewTree(view)
    }

    private fun disposeViewTree(view: View) {
        when (view) {
            is PagerPageHolder -> view.dispose()
            is PagerTransitionHolder -> view.dispose()
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    disposeViewTree(view.getChildAt(index))
                }
            }
        }
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        val projection = spreadProjection
        if (projection != null) {
            check(projection.matchesSource(items)) {
                "Spread projection no longer matches the source adapter sequence"
            }
            return createSpreadView(container, projection.units[position], spreadIsR2L)
        }
        return createItemView(items[position])
    }

    private fun createItemView(item: Any): View {
        return when (item) {
            is ReaderPage -> PagerPageHolder(requireNotNull(readerThemedContext), viewer, item)
            is ChapterTransition -> PagerTransitionHolder(requireNotNull(readerThemedContext), viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Create the view tree for one immutable Spread display unit. Original source items
     * remain authoritative; only regular ReaderPages may be paired by the projection.
     * Transition items keep their full-width transition holder.
     *
     * This factory does not alter adapter count or selection. The owning pager adapter
     * must use the same projection snapshot for its count and item-position mapping.
     */
    internal fun createSpreadView(
        container: ViewGroup,
        unit: SpreadAdapterItemMapper.Unit<Any>,
        isR2L: Boolean,
    ): View {
        return when (unit) {
            is SpreadAdapterItemMapper.Unit.Single -> {
                when (unit.item) {
                    is ReaderPage -> {
                        val holder = createItemView(unit.item) as PagerPageHolder
                        SpreadFrameLayout(container.context).apply {
                            bind(unit, holder, isR2L = isR2L)
                        }
                    }
                    is ChapterTransition -> createItemView(unit.item)
                    else -> throw NotImplementedError("Spread holder for ${unit.item.javaClass} not implemented")
                }
            }
            is SpreadAdapterItemMapper.Unit.Pair -> {
                val first = createItemView(unit.first) as? PagerPageHolder
                    ?: error("The first Spread item is not a reader page")
                val second = createItemView(unit.second) as? PagerPageHolder
                    ?: error("The second Spread item is not a reader page")
                SpreadFrameLayout(container.context).apply {
                    bind(unit, first, second, isR2L)
                }
            }
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val projection = spreadProjection
            if (projection != null) {
                val position = when (val identity = view.item) {
                    is SpreadAdapterItemMapper.Unit<*> ->
                        projection.units.indexOfFirst { it === identity }
                    else -> {
                        val sourceIndex = items.indexOfFirst { it === identity }
                        if (sourceIndex == -1) -1 else
                            SpreadAdapterNavigationMap.displayIndexForAdapter(projection.units, sourceIndex)
                    }
                }
                if (position != -1) return position
            } else {
                val position = items.indexOfFirst { it === view.item }
                if (position != -1) return position
            }
            logcat { "Position for ${view.item} not found" }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        val currentIndex = items.indexOf(currentPage)
        // A page may finish decoding after its chapter has been replaced. Never insert
        // a split page for an item that is no longer part of the effective adapter sequence.
        if (currentIndex == -1) return

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1
            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (items.getOrNull(placeAtIndex) is InsertPage) {
            return
        }

        clearSpreadProjection()
        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance(InsertPage::class.java)
        if (insertPages.isNotEmpty()) clearSpreadProjection()
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }

    /**
     * Drop chapter/page references after the pager has detached this adapter and destroyed
     * its instantiated holder trees.
     */
    fun clear() {
        clearSpreadProjection()
        items.clear()
        preprocessed.clear()
        nextTransition = null
        currentChapter = null
        readerThemedContext = null
    }
}
