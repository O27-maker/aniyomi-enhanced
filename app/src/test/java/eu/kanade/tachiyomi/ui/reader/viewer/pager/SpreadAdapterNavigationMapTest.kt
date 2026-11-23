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
    fun requestedMemberIsRetainedInAnchor() {
        assertEquals(
            SpreadAdapterNavigationMap.Anchor(0, 5),
            SpreadAdapterNavigationMap.anchorForAdapter(units, 5),
        )
    }

    @Test
    fun invalidAdapterIndexReturnsNoAnchor() {
        assertEquals(-1, SpreadAdapterNavigationMap.displayIndexForAdapter(units, -1))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(units, -1))
        assertNull(SpreadAdapterNavigationMap.anchorForAdapter(units, 999))
    }
}
