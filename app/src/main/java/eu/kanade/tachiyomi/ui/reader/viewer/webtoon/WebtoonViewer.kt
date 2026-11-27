package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.DualPagePresentationMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageZoomStateOwner
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageViewportAnchor
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageViewportState
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageViewportStateOwner
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionView
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min
import java.util.IdentityHashMap

/**
 * Implementation of a [Viewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(val activity: ReaderActivity, val isContinuous: Boolean = true) : Viewer, ReaderPageZoomStateOwner, ReaderPageViewportStateOwner {

    val downloadManager: MangaDownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /** Stable root lets the viewer swap its internal presentation after setChapters(). */
    private val root = FrameLayout(activity)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private val scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity, scrollDistance)

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    /**
     * Adapter of the recycler view.
     */
    internal val adapter = WebtoonAdapter(this)

    /** Experimental Spread projection; adapter.items remains the source of truth. */
    private var spreadRecycler: WebtoonRecyclerView? = null
    private var spreadLayoutManager: WebtoonLayoutManager? = null
    private var spreadAdapter: WebtoonSpreadAdapter? = null
    private var spreadPlan: WebtoonSpreadPlan<Any>? = null
    private val spreadOrientations = IdentityHashMap<ReaderPage, Boolean?>()
    private var spreadPreferredSourceIndex: Int? = null

    /** Experimental two-column host; the normal reader remains the fallback. */
    private var splitScrollHost: WebtoonSplitScrollHostView? = null
    private var splitScrollController: WebtoonSplitScrollSegmentController? = null
    private var pendingSplitScrollViewportState: ReaderPageViewportState? = null
    private var activeSplitRecycler: WebtoonRecyclerView? = null

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    private data class SplitScrollViewportAnchor(val page: ReaderPage, val topOffset: Int)

    private data class SplitScrollViewportSnapshot(
        val left: SplitScrollViewportAnchor?,
        val right: SplitScrollViewportAnchor?,
    )

    /** Preserve page zoom when RecyclerView recycles a holder or a presentation is rebuilt. */
    private val pageZoomStates = IdentityHashMap<ReaderPage, ReaderPageImageView.ImageZoomState>()

    private val threshold: Int =
        Injekt.get<ReaderPreferences>()
            .readerHideThreshold()
            .get()
            .threshold

    init {
        recycler.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onScrolled()

                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = { event ->
            val viewPosition = IntArray(2)
            recycler.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            recycler.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / recycler.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / recycler.originalHeight,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
            }
        }
        recycler.longTapListener = f@{ event ->
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        activity.onPageLongTap(item)
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            when (config.dualPagePresentationMode) {
                DualPagePresentationMode.SPLIT_SCROLL -> {
                    if (adapter.items.isNotEmpty()) {
                        installSplitScrollHost(adapter.items.indexOfFirst { it === currentPage }.takeIf { it >= 0 })
                    }
                }
                DualPagePresentationMode.SPREAD -> {
                    if (adapter.items.isNotEmpty()) {
                        spreadOrientations.keys.removeAll { cachedPage -> adapter.items.none { it === cachedPage } }
                        installSpreadHost(adapter.items.indexOfFirst { it === currentPage }.takeIf { it >= 0 })
                    }
                }
                DualPagePresentationMode.NONE -> refreshAdapter()
            }
        }

        config.themeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
            spreadRecycler?.doubleTapZoom = it
            splitScrollHost?.setDoubleTapZoomEnabled(it)
        }

        config.zoomPropertyChangedListener = {
            frame.zoomOutDisabled = it
            spreadRecycler?.zoomOutDisabled = it
            splitScrollHost?.setZoomOutDisabled(it)
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }

        // Recreate the active viewer so ReaderActivity obtains the correct root view
        // when the user changes between the standard and experimental presentation.
        config.dualPagePresentationModeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
        root.addView(frame, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }



    internal fun savePageZoomState(page: ReaderPage, state: ReaderPageImageView.ImageZoomState) {
        if (adapter.items.any { it === page }) pageZoomStates[page] = state
    }

    override fun exportPageViewportState(): ReaderPageViewportState? {
        if (config.dualPagePresentationMode != DualPagePresentationMode.SPLIT_SCROLL) return null
        val snapshot = captureSplitScrollViewport() ?: return null
        return ReaderPageViewportState(
            left = snapshot.left?.let { ReaderPageViewportAnchor(it.page, it.topOffset) },
            right = snapshot.right?.let { ReaderPageViewportAnchor(it.page, it.topOffset) },
        )
    }

    override fun importPageViewportState(state: ReaderPageViewportState?) {
        pendingSplitScrollViewportState = state
    }

    internal fun restorePageZoomState(page: ReaderPage, holder: WebtoonPageHolder) {
        pageZoomStates.remove(page)?.let(holder::restoreZoomState)
    }

    override fun exportPageZoomStates(): IdentityHashMap<ReaderPage, ReaderPageImageView.ImageZoomState> =
        IdentityHashMap(pageZoomStates).also { pageZoomStates.clear() }

    override fun importPageZoomStates(
        states: IdentityHashMap<ReaderPage, ReaderPageImageView.ImageZoomState>,
    ) {
        pageZoomStates.putAll(states)
    }

    /** Create a page holder using the reader theme and the active Spread RecyclerView. */
    internal fun createSpreadPageHolder(): WebtoonPageHolder =
        adapter.createPageHolder(requireNotNull(spreadRecycler))

    private fun installSpreadHost(initialSourceIndex: Int?) {
        val host = spreadRecycler ?: WebtoonRecyclerView(activity).also { spreadRecycler = it }
        val manager = spreadLayoutManager
            ?: WebtoonLayoutManager(activity, scrollDistance).also { spreadLayoutManager = it }
        host.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        host.itemAnimator = null
        host.isFocusable = false
        host.doubleTapZoom = config.doubleTapZoom
        host.zoomOutDisabled = config.zoomOutDisabled
        host.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        host.layoutManager = manager
        spreadOrientations.keys.removeAll { cachedPage -> adapter.items.none { it === cachedPage } }
        spreadPlan = createSpreadPlan()
        val projection = spreadAdapter ?: WebtoonSpreadAdapter(
            viewer = this,
            sourceItems = { adapter.items },
            plan = { requireNotNull(spreadPlan) },
        ).also { spreadAdapter = it }
        projection.retainSourcePages(adapter.items)
        host.adapter = projection
        host.clearOnScrollListeners()
        host.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onSpreadScrolled()
                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        activity.hideMenu()
                    }
                    if (dy < 0) {
                        val first = manager.findFirstVisibleItemPosition()
                        val item = projection.sourceItemAt(first) as? ChapterTransition.Prev
                        item?.to?.let(activity::requestPreloadChapter)
                    }
                    val last = manager.findLastEndVisibleItemPosition()
                    val item = projection.sourceItemAt(last)
                    if (item is ChapterTransition.Next && item.to == null) activity.showMenu()
                }
            },
        )
        host.tapListener = { event ->
            val position = PointF(
                event.x / host.width.coerceAtLeast(1),
                event.y / host.originalHeight.coerceAtLeast(host.height).coerceAtLeast(1),
            )
            when (config.navigator.getAction(position)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> host.scrollBy(0, scrollDistance)
                NavigationRegion.PREV, NavigationRegion.LEFT -> host.scrollBy(0, -scrollDistance)
            }
        }
        host.longTapListener = { event ->
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val child = host.findChildViewUnder(event.x, event.y)
                val displayIndex = child?.let(host::getChildAdapterPosition) ?: RecyclerView.NO_POSITION
                val sourceIndex = projection.sourceIndexAt(displayIndex)
                val page = sourceIndex?.let { adapter.items.getOrNull(it) as? ReaderPage }
                if (page != null) {
                    activity.onPageLongTap(page)
                    true
                } else false
            } else false
        }
        frame.isVisible = false
        recycler.isVisible = false
        splitScrollHost?.let(root::removeView)
        splitScrollHost = null
        if (host.parent !== root) root.addView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        host.isVisible = true
        initialSourceIndex?.let { source ->
            val display = projection.displayIndexForSource(source)
            if (display >= 0) {
                spreadPreferredSourceIndex = source
                manager.scrollToPositionWithOffset(display, 0)
                onSpreadScrolled(display, source)
            }
        }
    }

    private fun createSpreadPlan(): WebtoonSpreadPlan<Any> {
        val firstPage = adapter.currentChapter?.pages?.firstOrNull()
        val firstPageIndex = firstPage?.let { page -> adapter.items.indexOfFirst { it === page } } ?: -1
        return WebtoonSpreadPlan.create(
            items = adapter.items,
            isBoundary = { it is ChapterTransition },
            isPortrait = { item -> (item as? ReaderPage)?.let { spreadOrientations[it] } },
            canPair = { it is ReaderPage },
            canPairWithNext = { first, second ->
                first is ReaderPage && second is ReaderPage && first.chapter === second.chapter
            },
            firstPageSingle = config.firstPageSingle,
            firstPageIndex = firstPageIndex,
        )
    }

    /** Called after a page image decodes; rebuild only if its orientation changed. */
    internal fun onSpreadPageOrientationReady(page: ReaderPage, isPortrait: Boolean?) {
        if (config.dualPagePresentationMode != DualPagePresentationMode.SPREAD) return
        if (adapter.items.none { it === page }) return
        if (spreadOrientations[page] == isPortrait) return
        spreadOrientations[page] = isPortrait
        val host = spreadRecycler ?: return
        host.post {
            if (config.dualPagePresentationMode != DualPagePresentationMode.SPREAD) return@post
            if (adapter.items.none { it === page } || spreadOrientations[page] != isPortrait) return@post
            val manager = spreadLayoutManager ?: return@post
            val projection = spreadAdapter ?: return@post
            val firstVisible = manager.findFirstVisibleItemPosition()
            val sourceAnchor = projection.sourceIndexAt(firstVisible)
            val selectedSource = adapter.items.indexOfFirst { it === currentPage }.takeIf { it >= 0 }
            val anchorView = manager.findViewByPosition(firstVisible)
            val offset = anchorView?.top ?: 0
            val nextPlan = createSpreadPlan()
            if (nextPlan == spreadPlan) return@post
            spreadPlan = nextPlan
            projection.beginProjectionRefresh()
            projection.notifyDataSetChanged()
            val newDisplay = sourceAnchor?.let(projection::displayIndexForSource) ?: firstVisible
            if (newDisplay >= 0) manager.scrollToPositionWithOffset(newDisplay, offset)
            selectedSource?.let { onSpreadScrolled(projection.displayIndexForSource(it), it) }
            host.post { projection.endProjectionRefreshAndPrune() }
        }
    }

    private fun onSpreadScrolled(displayPosition: Int? = null, preferredSourceIndex: Int? = null) {
        val manager = spreadLayoutManager ?: return
        val projection = spreadAdapter ?: return
        val position = displayPosition ?: manager.findLastEndVisibleItemPosition()
        val preferred = preferredSourceIndex ?: spreadPreferredSourceIndex
            ?.takeIf { projection.displayIndexForSource(it) == position }
            ?: projection.lastSourceIndexAt(position)
        spreadPreferredSourceIndex = null
        val item = projection.sourceItemAt(position, preferred) ?: return
        if (currentPage === item) return
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        currentPage = item
        when (item) {
            is ReaderPage -> onPageSelected(item, allowPreload)
            is ChapterTransition -> onTransitionSelected(item)
        }
    }

    private fun disposeSpreadHost() {
        spreadRecycler?.apply {
            tapListener = null
            longTapListener = null
            adapter = null
            clearOnScrollListeners()
        }
        spreadAdapter?.clear()
        spreadRecycler?.let(root::removeView)
        spreadAdapter = null
        spreadPlan = null
        spreadOrientations.clear()
        spreadRecycler = null
        spreadLayoutManager = null
    }

    private fun disposeSplitScrollHost() {
        splitScrollController?.dispose()
        splitScrollController = null
        splitScrollHost?.leftRecycler?.apply { tapListener = null; longTapListener = null }
        splitScrollHost?.rightRecycler?.apply { tapListener = null; longTapListener = null }
        splitScrollHost?.setColumnAdapters(null, null)
        splitScrollHost?.let(root::removeView)
        splitScrollHost = null
        activeSplitRecycler = null
    }

    /**
     * Install the experimental Split Scroll projection over the authoritative
     * WebtoonAdapter sequence. The standard RecyclerView and adapter are retained
     * untouched so switching back is a safe fallback.
     */
    private fun installSplitScrollHost(
        initialSourceIndex: Int?,
        restoreViewport: Boolean = true,
    ) {
        val previousViewport = (if (restoreViewport) captureSplitScrollViewport() else null)
            ?: pendingSplitScrollViewportState?.let { state ->
                SplitScrollViewportSnapshot(
                    left = state.left?.let { SplitScrollViewportAnchor(it.page, it.topOffsetPx) },
                    right = state.right?.let { SplitScrollViewportAnchor(it.page, it.topOffsetPx) },
                )
            }
        // Remove the old column adapters before dropping the host. Detaching only
        // the host view leaves its RecyclerViews holding page holders and collectors.
        disposeSplitScrollHost()
        val splitWidthPreference = Injekt.get<ReaderPreferences>().dualPageSplitLeftWidth()
        val host = WebtoonSplitScrollHostView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setLeftWidthPercent(splitWidthPreference.get())
            onLeftWidthChanged = { splitWidthPreference.set(it) }
            setDoubleTapZoomEnabled(config.doubleTapZoom)
            setZoomOutDisabled(config.zoomOutDisabled)
        }
        splitScrollHost = host
        frame.isVisible = false
        root.addView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val plan = WebtoonSplitScrollPlan.create(
            items = adapter.items,
            isBoundary = { it is ChapterTransition },
            firstColumn = WebtoonSplitScrollPlan.Column.LEFT,
        )
        splitScrollController = WebtoonSplitScrollSegmentController(
            viewer = this,
            host = host,
            plan = plan,
            onSelection = ::onSplitScrollSelection,
            onBoundaryReady = { placement ->
                val transition = placement?.item as? ChapterTransition
                if (transition != null) {
                    val transitionView = ReaderTransitionView(activity).apply {
                        bind(transition, downloadManager, activity.viewModel.manga)
                    }
                    host.showBoundary(transitionView) {
                        // The trailing "no next chapter" transition is terminal. Advancing
                        // would enter the deliberately retained empty trailing segment and
                        // leave the reader blank; keep the final transition visible instead.
                        if (transition is ChapterTransition.Next && transition.to == null) {
                            activity.showMenu()
                        } else {
                            splitScrollController?.advancePastBoundary()
                        }
                    }
                }
            },
        )
        bindSplitScrollInteractions(host)
        val leftIndex = previousViewport?.left?.let { anchor ->
            adapter.items.indexOfFirst { it === anchor.page }.takeIf { it >= 0 }
        }
        val rightIndex = previousViewport?.right?.let { anchor ->
            adapter.items.indexOfFirst { it === anchor.page }.takeIf { it >= 0 }
        }
        val controller = splitScrollController
        val anchorsShareSegment = leftIndex == null || rightIndex == null ||
            controller?.segmentIndexForSourceIndex(leftIndex) == controller?.segmentIndexForSourceIndex(rightIndex)
        val restoredViewport = if (anchorsShareSegment) {
            val restoredLeft = leftIndex?.let { index ->
                previousViewport?.left?.let { splitScrollController?.moveToSourceIndex(index, it.topOffset) }
            } == true
            val restoredRight = rightIndex?.let { index ->
                previousViewport?.right?.let { splitScrollController?.moveToSourceIndex(index, it.topOffset) }
            } == true
            restoredLeft || restoredRight
        } else {
            false
        }
        if (restoredViewport) {
            // Restoring the two anchors must not make the last-restored column the
            // active progress page; restore the actual selected page without scrolling.
            initialSourceIndex?.let { splitScrollController?.selectSourceIndex(it) }
        } else {
            initialSourceIndex?.let { splitScrollController?.moveToSourceIndex(it) }
        }
        pendingSplitScrollViewportState = null
    }

    /** Capture independent first-visible page anchors before rebuilding the two-column host. */
    private fun captureSplitScrollViewport(): SplitScrollViewportSnapshot? {
        val host = splitScrollHost ?: return null
        val controller = splitScrollController ?: return null

        fun capture(
            recycler: WebtoonRecyclerView,
            column: WebtoonSplitScrollHostState.Column,
        ): SplitScrollViewportAnchor? {
            val manager = recycler.layoutManager as? WebtoonLayoutManager ?: return null
            val position = manager.findFirstVisibleItemPosition()
            if (position == RecyclerView.NO_POSITION) return null
            val page = controller.pageAt(column, position) ?: return null
            val offset = manager.findViewByPosition(position)?.top ?: 0
            return SplitScrollViewportAnchor(page, offset)
        }

        return SplitScrollViewportSnapshot(
            left = capture(host.leftRecycler, WebtoonSplitScrollHostState.Column.LEFT),
            right = capture(host.rightRecycler, WebtoonSplitScrollHostState.Column.RIGHT),
        ).takeIf { it.left != null || it.right != null }
    }

    /**
     * Install the same tap/long-press navigation semantics as the standard
     * continuous reader, scoped to whichever independent column was touched.
     */
    private fun bindSplitScrollInteractions(host: WebtoonSplitScrollHostView) {
        fun bindColumn(
            recycler: WebtoonRecyclerView,
            column: WebtoonSplitScrollHostState.Column,
        ) {
            recycler.tapListener = { event ->
                activeSplitRecycler = recycler
                val width = recycler.width.coerceAtLeast(1)
                val height = recycler.originalHeight.coerceAtLeast(recycler.height).coerceAtLeast(1)
                val point = PointF(event.x / width, event.y / height)
                when (config.navigator.getAction(point)) {
                    NavigationRegion.MENU -> activity.toggleMenu()
                    NavigationRegion.NEXT, NavigationRegion.RIGHT -> recycler.scrollBy(0, scrollDistance)
                    NavigationRegion.PREV, NavigationRegion.LEFT -> recycler.scrollBy(0, -scrollDistance)
                }
            }
            recycler.longTapListener = { event ->
                if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                    val child = recycler.findChildViewUnder(event.x, event.y)
                    val position = child?.let(recycler::getChildAdapterPosition)
                        ?: RecyclerView.NO_POSITION
                    val page = splitScrollController?.pageAt(column, position)
                    if (page != null) {
                        activeSplitRecycler = recycler
                        activity.onPageLongTap(page)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            recycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                            activity.hideMenu()
                        }
                        if (dy < 0) {
                            val first = (recyclerView.layoutManager as? WebtoonLayoutManager)
                                ?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                            val page = splitScrollController?.pageAt(column, first)
                            if (page != null && page.number <= 2) {
                                val previous = adapter.items.firstOrNull() as? ChapterTransition.Prev
                                previous?.to?.let(activity::requestPreloadChapter)
                            }
                        }
                        val manager = recyclerView.layoutManager as? WebtoonLayoutManager
                        val last = manager?.findLastEndVisibleItemPosition() ?: RecyclerView.NO_POSITION
                        val page = splitScrollController?.pageAt(column, last)
                        if (page != null && page.chapter == adapter.currentChapter) {
                            val pages = page.chapter.pages.orEmpty()
                            if (pages.size - page.number < 5) {
                                val next = adapter.items.lastOrNull()
                                val chapter = (next as? ChapterTransition.Next)?.to
                                    ?: (next as? ReaderPage)?.chapter
                                chapter?.let(activity::requestPreloadChapter)
                            }
                        }
                    }
                },
            )
        }
        bindColumn(host.leftRecycler, WebtoonSplitScrollHostState.Column.LEFT)
        bindColumn(host.rightRecycler, WebtoonSplitScrollHostState.Column.RIGHT)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return root
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        // Gesture callbacks are stored on the RecyclerView independently of the viewer scope.
        // Clear them before teardown so a detached reader cannot retain this viewer/activity.
        recycler.tapListener = null
        recycler.longTapListener = null

        // Preference callbacks also hold viewer references and are not coroutine-scoped.
        config.imagePropertyChangedListener = null
        config.themeChangedListener = null
        config.doubleTapZoomChangedListener = null
        config.zoomPropertyChangedListener = null
        config.navigationModeChangedListener = null
        config.dualPagePresentationModeChangedListener = null

        splitScrollController?.dispose()
        splitScrollController = null
        splitScrollHost?.leftRecycler?.apply { tapListener = null; longTapListener = null }
        splitScrollHost?.rightRecycler?.apply { tapListener = null; longTapListener = null }
        splitScrollHost?.setColumnAdapters(null, null)
        splitScrollHost?.let(root::removeView)
        activeSplitRecycler = null
        splitScrollHost = null
        disposeSpreadHost()

        // Detach first so RecyclerView recycles its holders while the adapter is still valid.
        recycler.adapter = null
        adapter.clear()
        super.destroy()
        scope.cancel()
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val firstLayout = recycler.isGone
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)
        pageZoomStates.keys.removeAll { cachedPage -> adapter.items.none { it === cachedPage } }

        val requestedPage = chapters.currChapter.pages?.let { pages ->
            pages.getOrNull(chapters.currChapter.requestedPage.coerceAtLeast(0)) ?: pages.lastOrNull()
        }
        val requestedSourceIndex = requestedPage?.let { page -> adapter.items.indexOfFirst { it === page } }?.takeIf { it >= 0 }
        when (config.dualPagePresentationMode) {
            DualPagePresentationMode.SPLIT_SCROLL -> {
                disposeSpreadHost()
                installSplitScrollHost(requestedSourceIndex, restoreViewport = false)
                recycler.isVisible = false
                return
            }
            DualPagePresentationMode.SPREAD -> {
                disposeSplitScrollHost()
                installSpreadHost(requestedSourceIndex)
                recycler.isVisible = false
                return
            }
            DualPagePresentationMode.NONE -> {
                disposeSplitScrollHost()
                disposeSpreadHost()
                frame.isVisible = true
                recycler.isVisible = true
            }
        }

        if (firstLayout) {
            logcat { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            val requestedPage = chapters.currChapter.requestedPage.coerceAtLeast(0)
            val page = pages.getOrNull(requestedPage) ?: pages.lastOrNull() ?: return
            moveToPage(page)
            recycler.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOfFirst { it === page }
        if (position != -1 && config.dualPagePresentationMode == DualPagePresentationMode.SPLIT_SCROLL) {
            splitScrollController?.moveToSourceIndex(position)
            return
        }
        if (position != -1 && config.dualPagePresentationMode == DualPagePresentationMode.SPREAD) {
            val displayPosition = spreadAdapter?.displayIndexForSource(position) ?: -1
            if (displayPosition >= 0) {
                spreadPreferredSourceIndex = position
                spreadLayoutManager?.scrollToPositionWithOffset(displayPosition, 0)
                onSpreadScrolled(displayPosition, position)
            }
            return
        }
        if (position != -1) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(pos = position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage !== item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Forward a source-index-resolved Split Scroll selection through the same
     * activity callbacks used by the standard Webtoon viewer.
     *
     * The experimental host reports original source indices through the coordinator.
     * This method validates the authoritative item identity before dispatching the
     * same page/progress/preload callbacks as the standard Webtoon viewer.
     */
    internal fun onSplitScrollSelection(
        result: WebtoonSplitScrollSelectionCoordinator.Result,
    ) {
        if (!result.accepted) return
        val selection = result.selection ?: return

        // A segment plan is only a projection. Before forwarding progress or preload,
        // verify that its original index still resolves to the same authoritative item.
        // This rejects callbacks made stale by chapter replacement or inserted split pages.
        val item = adapter.items.getOrNull(selection.sourceIndex) ?: return
        if (item !== selection.item) return
        if (currentPage === item) return

        val allowPreload = checkAllowPreload(item as? ReaderPage)
        currentPage = item
        when (item) {
            is ReaderPage -> onPageSelected(item, allowPreload)
            is ChapterTransition -> onTransitionSelected(item)
        }
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    private fun scrollUp() {
        val target = activeSplitRecycler
        if (target != null && config.dualPagePresentationMode == DualPagePresentationMode.SPLIT_SCROLL) {
            target.scrollBy(0, -scrollDistance)
            return
        }
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    private fun scrollDown() {
        val target = activeSplitRecycler
        if (target != null && config.dualPagePresentationMode == DualPagePresentationMode.SPLIT_SCROLL) {
            target.scrollBy(0, scrollDistance)
            return
        }
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollDown() else scrollUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollUp() else scrollDown()
                }
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.refresh()
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
    }
}

// Double the cache size to reduce rebinds/recycles incurred by the extra layout space on scroll direction changes
private const val RECYCLER_VIEW_CACHE_SIZE = 4
