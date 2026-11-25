package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Lifecycle-safe state model for the future two-RecyclerView Split Scroll host.
 *
 * This class deliberately owns no Android Views. Each column must eventually have
 * its own RecyclerView, LayoutManager, adapter/holder lifecycle and gesture target.
 * A column's generation token prevents a delayed callback from a recycled or
 * detached host instance from overwriting the current viewport state.
 */
internal data class WebtoonSplitScrollHostState(
    val left: ColumnState = ColumnState(),
    val right: ColumnState = ColumnState(),
) {
    internal data class ColumnState(
        val widthPercent: Int = DEFAULT_WIDTH_PERCENT,
        val zoom: Float = DEFAULT_ZOOM,
        val scrollOffsetPx: Int = 0,
        val generation: Long = 0,
        val attached: Boolean = false,
    )

    internal enum class Column {
        LEFT,
        RIGHT,
    }

    internal data class Binding(
        val column: Column,
        val generation: Long,
    )

    fun stateFor(column: Column): ColumnState =
        when (column) {
            Column.LEFT -> left
            Column.RIGHT -> right
        }

    /** Start/restart one column's holder lifecycle and return its callback token. */
    fun attach(column: Column): Pair<WebtoonSplitScrollHostState, Binding> {
        val current = stateFor(column)
        val next = current.copy(
            generation = current.generation + 1,
            attached = true,
        )
        val updated = withColumn(column, next)
        return updated to Binding(column, next.generation)
    }

    /** Invalidate callbacks from the detached host without affecting the other side. */
    fun detach(column: Column): WebtoonSplitScrollHostState {
        val current = stateFor(column)
        return withColumn(
            column,
            current.copy(generation = current.generation + 1, attached = false),
        )
    }

    fun resize(column: Column, widthPercent: Int): WebtoonSplitScrollHostState =
        withColumn(column, stateFor(column).copy(widthPercent = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)))

    fun setZoom(column: Column, zoom: Float): WebtoonSplitScrollHostState =
        withColumn(column, stateFor(column).copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)))

    /**
     * Store a column's own scroll offset. A callback is accepted only for the exact
     * currently attached binding; left and right offsets are never synchronized.
     */
    fun recordScroll(binding: Binding, offsetPx: Int): WebtoonSplitScrollHostState {
        val current = stateFor(binding.column)
        if (!current.attached || current.generation != binding.generation) return this
        return withColumn(binding.column, current.copy(scrollOffsetPx = offsetPx.coerceAtLeast(0)))
    }

    private fun withColumn(column: Column, state: ColumnState): WebtoonSplitScrollHostState =
        when (column) {
            Column.LEFT -> copy(left = state)
            Column.RIGHT -> copy(right = state)
        }

    companion object {
        const val DEFAULT_WIDTH_PERCENT = 50
        const val MIN_WIDTH_PERCENT = 20
        const val MAX_WIDTH_PERCENT = 80
        const val DEFAULT_ZOOM = 1f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3f
    }
}
