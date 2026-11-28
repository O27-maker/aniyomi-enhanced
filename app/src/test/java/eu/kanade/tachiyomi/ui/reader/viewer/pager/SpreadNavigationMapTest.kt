package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadNavigationMapTest {

    private val groups = listOf(
        SpreadPageMapper.Group.Pair("a", "b"),
        SpreadPageMapper.Group.Single("c"),
        SpreadPageMapper.Group.Pair("d", "e"),
    )

    @Test
    fun mapsEachSpreadToEffectivePageIndices() {
        assertEquals(
            listOf(
                SpreadNavigationMap.Spread(0, listOf(0, 1)),
                SpreadNavigationMap.Spread(1, listOf(2)),
                SpreadNavigationMap.Spread(2, listOf(3, 4)),
            ),
            SpreadNavigationMap.fromGroups(groups),
        )
    }

    @Test
    fun mapsPagesBackToSpreadIndices() {
        assertEquals(0, SpreadNavigationMap.spreadIndexForPage(groups, 0))
        assertEquals(0, SpreadNavigationMap.spreadIndexForPage(groups, 1))
        assertEquals(1, SpreadNavigationMap.spreadIndexForPage(groups, 2))
        assertEquals(2, SpreadNavigationMap.spreadIndexForPage(groups, 3))
        assertEquals(2, SpreadNavigationMap.spreadIndexForPage(groups, 4))
    }

    @Test
    fun invalidPageIndicesReturnMinusOne() {
        assertEquals(-1, SpreadNavigationMap.spreadIndexForPage(groups, -1))
        assertEquals(-1, SpreadNavigationMap.spreadIndexForPage(groups, 5))
    }

    @Test
    fun returnsIndicesForSpreadAndEmptyForInvalidSpread() {
        assertEquals(listOf(0, 1), SpreadNavigationMap.pageIndicesForSpread(groups, 0))
        assertEquals(listOf(2), SpreadNavigationMap.pageIndicesForSpread(groups, 1))
        assertEquals(listOf(3, 4), SpreadNavigationMap.pageIndicesForSpread(groups, 2))
        assertEquals(emptyList<Int>(), SpreadNavigationMap.pageIndicesForSpread(groups, -1))
        assertEquals(emptyList<Int>(), SpreadNavigationMap.pageIndicesForSpread(groups, 3))
    }

    @Test
    fun handlesAlreadyReversedR2lEffectiveSequenceWithoutReversingAgain() {
        val r2lGroups = SpreadPageMapper.map(listOf(5, 4, 3, 2, 1))
        assertEquals(0, SpreadNavigationMap.spreadIndexForPage(r2lGroups, 0))
        assertEquals(listOf(0, 1), SpreadNavigationMap.pageIndicesForSpread(r2lGroups, 0))
        assertEquals(listOf(2, 3), SpreadNavigationMap.pageIndicesForSpread(r2lGroups, 1))
        assertEquals(listOf(4), SpreadNavigationMap.pageIndicesForSpread(r2lGroups, 2))
    }
}
