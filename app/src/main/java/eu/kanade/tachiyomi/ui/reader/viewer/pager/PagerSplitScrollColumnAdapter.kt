package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.system.createReaderThemeContext

/**
 * RecyclerView adapter for one independently scrolling pager Split Scroll column.
 *
 * PagerViewerAdapter.items remains authoritative. Placements retain its source indices,
 * while each row owns a native PagerPageHolder so image loading, zoom and gestures use
 * the same implementation as the ordinary paged reader.
 *
 * The adapter owns holders it creates and permanently disposes them when recycled.
 * It must not be attached at the same time as the ViewPager adapter.
 */
internal class PagerSplitScrollColumnAdapter(
    private val viewer: PagerViewer,
    private val placements: List<PagerSplitScrollPlan.Placement<Any>>,
    private val onPageBound: (ReaderPage, Int) -> Unit = { _, _ -> },
) : RecyclerView.Adapter<PagerSplitScrollColumnAdapter.PageHolder>() {

    init {
        require(placements.all { it.item is ReaderPage }) {
            "Chapter transitions must be hosted as full-width boundaries"
        }
        require(placements.all {
            it.column == PagerSplitScrollPlan.Column.LEFT ||
                it.column == PagerSplitScrollPlan.Column.RIGHT
        }) {
            "A column adapter cannot contain full-width placements"
        }
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = placements.size

    override fun getItemId(position: Int): Long = placements[position].sourceIndex.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        // PagerPageHolder is a FrameLayout whose image child fills its bounds. Unlike
        // ViewPager, a vertical RecyclerView cannot infer a useful WRAP_CONTENT height
        // for it, so give each page one column viewport of vertical reading space.
        val pageHeight = parent.measuredHeight
            .takeIf { it > 0 }
            ?: parent.resources.displayMetrics.heightPixels
        val container = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight,
            )
        }
        return PageHolder(container) { pageHolder ->
            pageHolder.setOnLongClickListener(null)
            pageHolder.dispose()
        }
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val placement = placements[position]
        val page = placement.item as ReaderPage
        val longClickListener = View.OnLongClickListener {
            if (viewer.activity.viewModel.state.value.menuVisible || viewer.config.longTapEnabled) {
                viewer.activity.onPageLongTap(page)
                true
            } else {
                false
            }
        }
        holder.container.setOnLongClickListener(longClickListener)
        if (holder.pageHolder?.page !== page) {
            holder.clearPageHolder()
            val pageHolder = PagerPageHolder(
                viewer.activity.createReaderThemeContext(),
                viewer,
                page,
            )
            holder.pageHolder = pageHolder
            pageHolder.setOnLongClickListener(longClickListener)
            holder.container.addView(
                pageHolder,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            viewer.onSplitScrollPageHolderBound(page, pageHolder)
        }
        onPageBound(page, placement.sourceIndex)
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.clearPageHolder()
    }

    fun sourceIndexAt(columnPosition: Int): Int? =
        placements.getOrNull(columnPosition)?.sourceIndex

    fun pageAt(columnPosition: Int): ReaderPage? =
        placements.getOrNull(columnPosition)?.item as? ReaderPage

    class PageHolder(
        val container: FrameLayout,
        private val onDispose: (PagerPageHolder) -> Unit,
    ) : RecyclerView.ViewHolder(container) {
        var pageHolder: PagerPageHolder? = null

        fun clearPageHolder() {
            container.setOnLongClickListener(null)
            pageHolder?.let(onDispose)
            pageHolder = null
            container.removeAllViews()
        }
    }
}
