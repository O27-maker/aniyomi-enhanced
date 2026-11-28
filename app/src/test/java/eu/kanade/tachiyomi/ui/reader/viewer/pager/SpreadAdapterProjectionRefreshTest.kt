package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * These tests exercise projection rebuilds after adapter mutations that can occur
 * during reader use. They are pure unit tests; execution is pending CI/build access.
 */
class SpreadAdapterProjectionRefreshTest {

    @Test
    fun insertedSplitPageRebuildKeepsNewAdapterIndicesAuthoritative() {
        val before = listOf("p0", "p1", "p2", "p3")
        val beforeProjection = SpreadAdapterProjection.build(before, selectedAdapterIndex = 2)
        assertEquals(SpreadAdapterNavigationMap.Anchor(1, 2), beforeProjection.selectedAnchor)

        val after = listOf("p0", "p1", "split", "p2", "p3")
        val afterProjection = SpreadAdapterProjection.build(
            after,
            selectedAdapterIndex = 3,
            canPair = { it != "split" },
        )
        assertEquals(
            SpreadAdapterNavigationMap.Anchor(2, 3),
            afterProjection.selectedAnchor,
        )
        assertNotEquals(beforeProjection.units, afterProjection.units)
    }

    @Test
    fun chapterTransitionAddedBetweenSequencesCannotBePairedAcross() {
        val items = listOf("prev-page", "transition", "current-0", "current-1")
        val projection = SpreadAdapterProjection.build(
            adapterItems = items,
            selectedAdapterIndex = 2,
            isBoundary = { it == "transition" },
        )
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("prev-page", 0),
                SpreadAdapterItemMapper.Unit.Single("transition", 1),
                SpreadAdapterItemMapper.Unit.Pair("current-0", 2, "current-1", 3),
            ),
            projection.units,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(2, 2), projection.selectedAnchor)
    }

    @Test
    fun selectedSecondMemberAfterRebuildRetainsItsAdapterIndex() {
        val projection = SpreadAdapterProjection.build(
            adapterItems = listOf("a", "b", "c", "d"),
            selectedAdapterIndex = 3,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(1, 3), projection.selectedAnchor)
    }
}
