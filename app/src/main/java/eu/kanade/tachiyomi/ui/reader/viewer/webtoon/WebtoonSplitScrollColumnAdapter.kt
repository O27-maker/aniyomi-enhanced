package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView

/**
 * Page-only adapter projection for one side of one Split Scroll segment.
 *
 * The source WebtoonAdapter.items list remains authoritative. Each displayed row
 * retains its original source index so selection/progress callbacks never use a
 * column-local adapter position as a reader position.
 *
 * ChapterTransition items belong in the host's full-width boundary slot and are
 * intentionally rejected by these column adapters.
 */
internal class WebtoonSplitScrollColumnAdapter(
    private val viewer: WebtoonViewer,
    private val recycler: WebtoonRecyclerView,
    private val column: WebtoonSplitScrollPlan.Column,
    placements: List<WebtoonSplitScrollPlan.Placement<Any>>,
) : RecyclerView.Adapter<WebtoonPageHolder>() {

    private var placements: List<WebtoonSplitScrollPlan.Placement<Any>> = emptyList()

    init {
        require(column != WebtoonSplitScrollPlan.Column.FULL_WIDTH) {
            "A full-width boundary cannot be represented by a column adapter"
        }
        submitPlacements(placements)
    }

    override fun getItemCount(): Int = placements.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebtoonPageHolder {
        val imageView = ReaderPageImageView(parent.context, isWebtoon = true)
        return WebtoonPageHolder(imageView, viewer, recycler)
    }

    override fun onBindViewHolder(holder: WebtoonPageHolder, position: Int) {
        holder.bind(placements[position].item as ReaderPage)
    }

    override fun onViewRecycled(holder: WebtoonPageHolder) {
        holder.recycle()
    }

    /** Resolve a local column position to the original WebtoonAdapter.items index. */
    fun originalIndexAt(columnPosition: Int): Int? =
        placements.getOrNull(columnPosition)?.originalIndex

    /** Resolve a visible local row to its original ReaderPage identity. */
    fun pageAt(columnPosition: Int): ReaderPage? =
        placements.getOrNull(columnPosition)?.item as? ReaderPage

    /** Replace one segment snapshot after chapter/split-page mutations. */
    fun submitPlacements(next: List<WebtoonSplitScrollPlan.Placement<Any>>) {
        require(next.all { it.column == column }) {
            "A Split Scroll column adapter cannot contain placements from another column"
        }
        require(next.all { it.item is ReaderPage }) {
            "Chapter transitions must be rendered in the full-width host boundary slot"
        }
        placements = next.toList()
        notifyDataSetChanged()
    }
}
