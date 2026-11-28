package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Resolves effective adapter indices to display-unit indices for a mapped Spread
 * sequence. The original adapter position remains the active-page identity.
 */
internal object SpreadAdapterNavigationMap {

    data class Anchor(
        val displayIndex: Int,
        val adapterIndex: Int,
    )

    fun <T> displayIndexForAdapter(
        units: List<SpreadAdapterItemMapper.Unit<T>>,
        adapterIndex: Int,
    ): Int {
        if (adapterIndex < 0 || !hasValidUnits(units)) return -1
        return units.indexOfFirst { unit ->
            when (unit) {
                is SpreadAdapterItemMapper.Unit.Single -> unit.adapterIndex == adapterIndex
                is SpreadAdapterItemMapper.Unit.Pair ->
                    unit.firstAdapterIndex == adapterIndex || unit.secondAdapterIndex == adapterIndex
            }
        }
    }

    /**
     * Returns the original adapter indices represented by a display unit.
     * A single page maps to one index; a spread maps to both pages in source order.
     */
    fun <T> adapterIndicesForDisplay(
        units: List<SpreadAdapterItemMapper.Unit<T>>,
        displayIndex: Int,
    ): List<Int> {
        if (displayIndex !in units.indices || !hasValidUnits(units)) return emptyList()
        return when (val unit = units[displayIndex]) {
            is SpreadAdapterItemMapper.Unit.Single -> listOf(unit.adapterIndex)
            is SpreadAdapterItemMapper.Unit.Pair -> listOf(unit.firstAdapterIndex, unit.secondAdapterIndex)
        }
    }

    /**
     * Resolves the active original item when a display unit becomes selected.
     * Keep the current page when it belongs to the selected spread; otherwise use
     * the first page in effective adapter order.
     */
    fun <T> adapterIndexForDisplay(
        units: List<SpreadAdapterItemMapper.Unit<T>>,
        displayIndex: Int,
        preferredAdapterIndex: Int? = null,
    ): Int? {
        val indices = adapterIndicesForDisplay(units, displayIndex)
        if (indices.isEmpty()) return null
        return preferredAdapterIndex?.takeIf { it in indices } ?: indices.first()
    }

    /**
     * Returns a display-unit anchor that retains the requested adapter item.
     * Invalid adapter indices return null rather than silently selecting a page.
     */
    private fun <T> hasValidUnits(units: List<SpreadAdapterItemMapper.Unit<T>>): Boolean {
        if (units.isEmpty()) return true
        var expectedIndex = when (val first = units.first()) {
            is SpreadAdapterItemMapper.Unit.Single -> first.adapterIndex
            is SpreadAdapterItemMapper.Unit.Pair -> first.firstAdapterIndex
        }
        if (expectedIndex < 0) return false
        for (unit in units) {
            when (unit) {
                is SpreadAdapterItemMapper.Unit.Single -> {
                    if (unit.adapterIndex != expectedIndex) return false
                    expectedIndex++
                }
                is SpreadAdapterItemMapper.Unit.Pair -> {
                    if (unit.firstAdapterIndex != expectedIndex) return false
                    if (unit.secondAdapterIndex != expectedIndex + 1) return false
                    expectedIndex += 2
                }
            }
        }
        return true
    }

    fun <T> anchorForAdapter(
        units: List<SpreadAdapterItemMapper.Unit<T>>,
        adapterIndex: Int,
    ): Anchor? {
        val displayIndex = displayIndexForAdapter(units, adapterIndex)
        if (displayIndex == -1) return null
        return Anchor(displayIndex, adapterIndex)
    }
}
