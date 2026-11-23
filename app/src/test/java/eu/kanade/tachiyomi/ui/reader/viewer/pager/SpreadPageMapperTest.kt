package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadPageMapperTest {

    @Test
    fun emptyInputProducesNoGroups() {
        assertEquals(emptyList<SpreadPageMapper.Group<Int>>(), SpreadPageMapper.map(emptyList()))
    }

    @Test
    fun pairsPortraitPagesInSourceSequence() {
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Pair(1, 2),
                SpreadPageMapper.Group.Pair(3, 4),
            ),
            SpreadPageMapper.map(listOf(1, 2, 3, 4)),
        )
    }

    @Test
    fun leavesOddPortraitPageAlone() {
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Pair(1, 2),
                SpreadPageMapper.Group.Single(3),
            ),
            SpreadPageMapper.map(listOf(1, 2, 3)),
        )
    }

    @Test
    fun firstPageCanBeKeptSingle() {
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Single(1),
                SpreadPageMapper.Group.Pair(2, 3),
                SpreadPageMapper.Group.Single(4),
            ),
            SpreadPageMapper.map(listOf(1, 2, 3, 4), firstPageSingle = true),
        )
    }

    @Test
    fun landscapePageBreaksPairing() {
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Pair(1, 2),
                SpreadPageMapper.Group.Single(3),
                SpreadPageMapper.Group.Pair(4, 5),
            ),
            SpreadPageMapper.map(
                pages = listOf(1, 2, 3, 4, 5),
                isLandscape = { it == 3 },
            ),
        )
    }

    @Test
    fun ineligiblePageBreaksPairingAndKeepsIdentity() {
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Single(1),
                SpreadPageMapper.Group.Pair(2, 3),
                SpreadPageMapper.Group.Single(4),
            ),
            SpreadPageMapper.map(
                pages = listOf(1, 2, 3, 4),
                canPair = { it != 1 && it != 4 },
            ),
        )
    }

    @Test
    fun r2lSequenceCanBeMappedInAlreadyReversedDisplayOrder() {
        val source = listOf(4, 3, 2, 1)
        assertEquals(
            listOf(
                SpreadPageMapper.Group.Pair(4, 3),
                SpreadPageMapper.Group.Pair(2, 1),
            ),
            SpreadPageMapper.map(source),
        )
    }
}
