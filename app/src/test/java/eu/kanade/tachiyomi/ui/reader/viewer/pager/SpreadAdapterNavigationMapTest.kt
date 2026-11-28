package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadAdapterNavigationMapTest {

    private val units = listOf(
        SpreadAdapterItemMapper.Unit.Pair("a", 4, "b", 5),
        SpreadAdapterItemMapper.Unit.Single("special", 6),
        SpreadAdapterItemMapper.Unit.Pair("c", 7, "d", 8),
    )

    @Test
    fun bothPagesResolveToSameDisplayUnit() {
        assertEquals(0, SpreadAdapterNavigationMap.displayIndexForAdapter(units, 4))
        assertEquals(0, SpreadAdapterNavigationMap.displayIndexForAdapter(units, 5))
    }

    @Test
    fun singleAndLaterPairResolveCorrectly() {
        assertEquals(1, SpreadAdapterNavigationMap.displayIndexForAdapter(units, 6))
        assertEquals(2, SpreadAdapterNavigationMap.displayIndexForAdapter(units, 7))
        assertEquals(2, SpreadAdapterNavigationMap.displayIndexForAdapter(units, 8))
    }

    @Test
    fun displayUnitResolvesBothOriginalIndicesInSourceOrder() {
        assertEquals(listOf(4, 5), SpreadAdapterNavigationMap.adapterIndicesForDisplay(units, 0))
        assertEquals(listOf(6), SpreadAdapterNavigationMap.adapterIndicesForDisplay(units, 1))
        assertEquals(listOf(7, 8), SpreadAdapterNavigationMap.adapterIndicesForDisplay(units, 2))
    }

    @Test
    fun displaySelectionPreservesPreferredPageInsideSpread() {
        assertEquals(5, SpreadAdapterNavigationMap.adapterIndexForDisplay(units, 0, preferredAdapterIndex = 5))
        assertEquals(4, SpreadAdapterNavigationMap.adapterIndexForDisplay(units, 0, preferredAdapterIndex = 99))
        assertEquals(6, SpreadAdapterNavigationMap.adapterIndexForDisplay(units, 1, preferredAdapterIndex = 5))
    }

    @Test
    fun invalidDisplayUnitHasNoSourceIndices() {
        assertEquals(emptyList<Int>(), SpreadAdapterNavigationMap.adapterIndicesForDisplay(units, -1))
        assertEquals(emptyList<Int>(), SpreadAdapterNavigationMap.adapterIndicesForDisplay(units, units.size))
        assertNull(SpreadAdapterNavigationMap.adapterIndexForDisplay(units, units.size))
    }

    @Test
    fun requestedMemberIsRetainedInAnchor() {
        assertEquals(
            SpreadAdapterNavigationMap.Anchor(0, 5),
            SpreadAdapterNavigationMap.anchorForAdapter(units, 5),
        )
    }

    @Test
    fun malformedUnitsWithGapsOrDuplicateIndicesAreRejected() {
        val gap = listOf(
            SpreadAdapterItemMapper.Unit.Pair("a", 4, "c", 6),
        )
        val duplicate = listOf(
            SpreadAdapterItemMapper.Unit.Single("a", 4),
            SpreadAdapterItemMapper.Unit.Single("b", 4),
        )

        assertEquals(-1, SpreadAdapterNavigationMap.displayIndexForAdapter(gap, 4))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(gap, 6))
        assertEquals(-1, SpreadAdapterNavigationMap.displayIndexForAdapter(duplicate, 4))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(duplicate, 4))
    }

    @Test
    fun invalidAdapterIndexReturnsNoAnchor() {
        assertEquals(-1, SpreadAdapterNavigationMap.displayIndexForAdapter(units, -1))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(units, -1))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(units, 999))
    }
}
