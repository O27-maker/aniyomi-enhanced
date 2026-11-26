package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

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

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val page = placements.getOrNull(viewType)?.item as? ReaderPage
            ?: error("Missing page placement for adapter view type $viewType")
        return PageHolder(PagerPageHolder(viewer.activity.createReaderThemeContext(), viewer, page))
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val placement = placements[position]
        val page = placement.item as ReaderPage
        check(holder.pageHolder.page === page) {
            "A pager Split Scroll holder cannot be rebound to a different page identity"
        }
        onPageBound(page, placement.sourceIndex)
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.pageHolder.dispose()
    }

    fun sourceIndexAt(columnPosition: Int): Int? =
        placements.getOrNull(columnPosition)?.sourceIndex

    fun pageAt(columnPosition: Int): ReaderPage? =
        placements.getOrNull(columnPosition)?.item as? ReaderPage

    class PageHolder(
        val pageHolder: PagerPageHolder,
    ) : RecyclerView.ViewHolder(pageHolder)
}
