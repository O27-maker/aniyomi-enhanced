package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Immutable derived snapshot for a spread-capable adapter.
 *
 * This projection deliberately does not own Android views or mutate the source adapter sequence.
 * It can be rebuilt after chapter, split-page, or cleanup updates.
 */
internal data class SpreadAdapterProjection<T>(
    val units: List<SpreadAdapterItemMapper.Unit<T>>,
    val selectedAnchor: SpreadAdapterNavigationMap.Anchor?,
    private val sourceSnapshot: List<T>,
) {
    /** Identity-based guard against reusing a projection after adapter mutation. */
    fun matchesSource(adapterItems: List<T>): Boolean =
        adapterItems.size == sourceSnapshot.size &&
            adapterItems.indices.all { adapterItems[it] === sourceSnapshot[it] }

    companion object {
        fun <T> build(
            adapterItems: List<T>,
            selectedAdapterIndex: Int,
            isBoundary: (T) -> Boolean = { false },
            canPair: (T) -> Boolean = { true },
        ): SpreadAdapterProjection<T> {
            val units = SpreadAdapterItemMapper.map(adapterItems, isBoundary, canPair)
            return SpreadAdapterProjection(
                units = units.toList(),
                selectedAnchor = SpreadAdapterNavigationMap.anchorForAdapter(units, selectedAdapterIndex),
                sourceSnapshot = adapterItems.toList(),
            )
        }
    }
}
