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
        if (adapterIndex < 0) return -1
        return units.indexOfFirst { unit ->
            when (unit) {
                is SpreadAdapterItemMapper.Unit.Single -> unit.adapterIndex == adapterIndex
                is SpreadAdapterItemMapper.Unit.Pair ->
                    unit.firstAdapterIndex == adapterIndex || unit.secondAdapterIndex == adapterIndex
            }
        }
    }

    /**
     * Returns a display-unit anchor that retains the requested adapter item.
     * Invalid adapter indices return null rather than silently selecting a page.
     */
    fun <T> anchorForAdapter(
        units: List<SpreadAdapterItemMapper.Unit<T>>,
        adapterIndex: Int,
    ): Anchor? {
        val displayIndex = displayIndexForAdapter(units, adapterIndex)
        if (displayIndex == -1) return null
        return Anchor(displayIndex, adapterIndex)
    }
}
