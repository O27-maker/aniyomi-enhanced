package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.util.IdentityHashMap

/**
 * RecyclerView projection adapter for continuous-reader Spread.
 *
 * The original WebtoonAdapter remains authoritative. Page holders are cached by
 * ReaderPage identity during regrouping so decoded images, zoom, pan, gestures,
 * and in-flight page state survive orientation-driven projection refreshes.
 */
internal class WebtoonSpreadAdapter(
    private val viewer: WebtoonViewer,
    private val sourceItems: () -> List<Any>,
    private val plan: () -> WebtoonSpreadPlan<Any>,
) : RecyclerView.Adapter<WebtoonSpreadAdapter.RowHolder>() {

    private val pageHolderCache = IdentityHashMap<ReaderPage, WebtoonPageHolder>()
    private var preservingHoldersForProjectionRefresh = false

    internal class RowHolder(
        val container: LinearLayout,
        private val adapter: WebtoonSpreadAdapter,
    ) : RecyclerView.ViewHolder(container) {
        private var transitionHolder: WebtoonTransitionHolder? = null
        private val boundPages = mutableListOf<ReaderPage>()

        fun bind(unit: WebtoonSpreadPlan.Unit<Any>) {
            if (!adapter.isPreservingHolders()) releaseCachedPages()
            clearRow()
            container.orientation = LinearLayout.HORIZONTAL
            when (unit) {
                is WebtoonSpreadPlan.Unit.Single -> addPage(unit.item as ReaderPage, paired = false)
                is WebtoonSpreadPlan.Unit.Pair -> {
                    val ordered = if (adapter.viewer.config.dualPageInvert) {
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
                    val holder = WebtoonTransitionHolder(transition, adapter.viewer)
                    holder.bind(unit.item as ChapterTransition)
                    transitionHolder = holder
                    container.addView(transition)
                }
            }
        }

        private fun addPage(page: ReaderPage, paired: Boolean) {
            val holder = adapter.obtainPageHolder(page)
            boundPages += page
            (holder.itemView.parent as? ViewGroup)?.removeView(holder.itemView)
            holder.itemView.layoutParams = LinearLayout.LayoutParams(
                if (paired) 0 else MATCH_PARENT,
                WRAP_CONTENT,
                if (paired) 1f else 0f,
            )
            container.addView(holder.itemView)
        }

        private fun clearRow() {
            transitionHolder?.recycle()
            transitionHolder = null
            container.removeAllViews()
            boundPages.clear()
        }

        fun releaseCachedPages() {
            boundPages.forEach(adapter::releasePageHolder)
            boundPages.clear()
        }

        fun recycleChildren() = clearRow()
    }

    private fun obtainPageHolder(page: ReaderPage): WebtoonPageHolder {
        return pageHolderCache[page] ?: viewer.createSpreadPageHolder().also {
            it.bind(page)
            pageHolderCache[page] = it
        }
    }

    private fun releasePageHolder(page: ReaderPage) {
        pageHolderCache.remove(page)?.recycle()
    }

    fun isPreservingHolders(): Boolean = preservingHoldersForProjectionRefresh

    fun beginProjectionRefresh() {
        preservingHoldersForProjectionRefresh = true
    }

    fun endProjectionRefreshAndPrune() {
        preservingHoldersForProjectionRefresh = false
        val iterator = pageHolderCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.itemView.parent == null) {
                entry.value.recycle()
                iterator.remove()
            }
        }
    }

    /** Release holders for pages that left the authoritative source sequence. */
    fun retainSourcePages(items: List<Any>) {
        val retained = items.filterIsInstance<ReaderPage>().toSet()
        val iterator = pageHolderCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in retained) {
                entry.value.recycle()
                iterator.remove()
            }
        }
    }

    fun clear() {
        pageHolderCache.values.forEach(WebtoonPageHolder::recycle)
        pageHolderCache.clear()
    }

    override fun getItemCount(): Int = plan().units.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val row = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
        }
        return RowHolder(row, this)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(plan().units[position])
    }

    override fun onViewRecycled(holder: RowHolder) {
        if (!preservingHoldersForProjectionRefresh) holder.releaseCachedPages()
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
