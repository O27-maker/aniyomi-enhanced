package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * RecyclerView projection adapter for continuous-reader Spread.
 *
 * It consumes the immutable plan while retaining the original page objects and
 * delegates each image to the existing WebtoonPageHolder implementation. The
 * source WebtoonAdapter remains authoritative and is never reordered or modified.
 */
internal class WebtoonSpreadAdapter(
    private val viewer: WebtoonViewer,
    private val sourceItems: () -> List<Any>,
    private val plan: () -> WebtoonSpreadPlan<Any>,
) : RecyclerView.Adapter<WebtoonSpreadAdapter.RowHolder>() {

    internal class RowHolder(
        val container: LinearLayout,
        private val viewer: WebtoonViewer,
    ) : RecyclerView.ViewHolder(container) {
        private val pageHolders = mutableListOf<WebtoonPageHolder>()
        private var transitionHolder: WebtoonTransitionHolder? = null

        fun bind(unit: WebtoonSpreadPlan.Unit<Any>) {
            recycleChildren()
            container.orientation = LinearLayout.HORIZONTAL
            when (unit) {
                is WebtoonSpreadPlan.Unit.Single -> addPage(unit.item as ReaderPage, paired = false)
                is WebtoonSpreadPlan.Unit.Pair -> {
                    val ordered = if (viewer.config.dualPageInvert) {
                        listOf(unit.second, unit.first)
                    } else {
                        listOf(unit.first, unit.second)
                    }
                    ordered.forEach { addPage(it as ReaderPage, paired = true) }
                }
                is WebtoonSpreadPlan.Unit.Boundary -> {
                    container.orientation = LinearLayout.VERTICAL
                    val transition = LinearLayout(container.context)
                    transition.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    val holder = WebtoonTransitionHolder(transition, viewer)
                    holder.bind(unit.item as ChapterTransition)
                    transitionHolder = holder
                    container.addView(transition)
                }
            }
        }

        private fun addPage(page: ReaderPage, paired: Boolean) {
            val holder = viewer.createSpreadPageHolder()
            holder.bind(page)
            holder.itemView.layoutParams = LinearLayout.LayoutParams(
                if (paired) 0 else MATCH_PARENT,
                WRAP_CONTENT,
                if (paired) 1f else 0f,
            )
            pageHolders += holder
            container.addView(holder.itemView)
        }

        fun recycleChildren() {
            pageHolders.forEach(WebtoonPageHolder::recycle)
            pageHolders.clear()
            transitionHolder?.recycle()
            transitionHolder = null
            container.removeAllViews()
        }
    }

    override fun getItemCount(): Int = plan().units.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val row = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
        }
        return RowHolder(row, viewer)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(plan().units[position])
    }

    override fun onViewRecycled(holder: RowHolder) {
        holder.recycleChildren()
    }

    fun sourceIndexAt(displayIndex: Int): Int? =
        plan().units.getOrNull(displayIndex)?.sourceIndices?.firstOrNull()

    fun displayIndexForSource(sourceIndex: Int): Int =
        plan().unitIndexForSourceIndex(sourceIndex)

    fun sourceItemAt(displayIndex: Int, preferredSourceIndex: Int? = null): Any? =
        plan().sourceItemAt(displayIndex, preferredSourceIndex)

    fun sourceSnapshotMatches(): Boolean = plan().matchesSource(sourceItems())
}
