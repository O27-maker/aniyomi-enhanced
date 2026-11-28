package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Independent sizing state for the experimental Split Scroll reader.
 *
 * Width is expressed as a percentage of the available column area. Zoom is a
 * separate multiplier and never changes the width value. This is intentionally a
 * small value model; persistence and UI controls are handled by the reader layer.
 */
internal data class SplitScrollColumnState(
    val widthPercent: Int = DEFAULT_WIDTH_PERCENT,
    val zoom: Float = DEFAULT_ZOOM,
) {
    fun withWidth(percent: Int): SplitScrollColumnState =
        copy(widthPercent = percent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT))

    fun withZoom(value: Float): SplitScrollColumnState =
        copy(zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun resetWidth(): SplitScrollColumnState = copy(widthPercent = DEFAULT_WIDTH_PERCENT)

    fun resetZoom(): SplitScrollColumnState = copy(zoom = DEFAULT_ZOOM)

    fun reset(): SplitScrollColumnState = DEFAULT_STATE

    companion object {
        const val MIN_WIDTH_PERCENT = 20
        const val MAX_WIDTH_PERCENT = 80
        const val DEFAULT_WIDTH_PERCENT = 50

        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3.0f
        const val DEFAULT_ZOOM = 1.0f

        val DEFAULT_STATE = SplitScrollColumnState()
    }
}

/**
 * Holds the two columns independently. Updating or resetting one side does not
 * affect the other side.
 */
internal data class SplitScrollState(
    val left: SplitScrollColumnState = SplitScrollColumnState.DEFAULT_STATE,
    val right: SplitScrollColumnState = SplitScrollColumnState.DEFAULT_STATE,
) {
    fun updateLeftWidth(percent: Int) = copy(left = left.withWidth(percent))
    fun updateLeftZoom(value: Float) = copy(left = left.withZoom(value))
    fun updateRightWidth(percent: Int) = copy(right = right.withWidth(percent))
    fun updateRightZoom(value: Float) = copy(right = right.withZoom(value))

    fun resetLeftWidth() = copy(left = left.resetWidth())
    fun resetLeftZoom() = copy(left = left.resetZoom())
    fun resetRightWidth() = copy(right = right.resetWidth())
    fun resetRightZoom() = copy(right = right.resetZoom())

    fun resetLeft() = copy(left = left.reset())
    fun resetRight() = copy(right = right.reset())

    companion object {
        val DEFAULT_STATE = SplitScrollState()
    }
}
