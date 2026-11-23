package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitScrollPageDistributorTest {

    @Test
    fun emptyListProducesEmptyColumns() {
        val result = SplitScrollPageDistributor.distribute(emptyList<Int>())
        assertTrue(result.left.isEmpty())
        assertTrue(result.right.isEmpty())
    }

    @Test
    fun singleItemStartsInLeftColumn() {
        val result = SplitScrollPageDistributor.distribute(listOf("page-1"))
        assertEquals(listOf("page-1"), result.left.map { it.item })
        assertTrue(result.right.isEmpty())
    }

    @Test
    fun itemsAlternateBetweenColumnsInSourceOrder() {
        val result = SplitScrollPageDistributor.distribute((1..6).toList())
        assertEquals(listOf(1, 3, 5), result.left.map { it.item })
        assertEquals(listOf(2, 4, 6), result.right.map { it.item })
    }

    @Test
    fun oddItemCountLeavesLeftWithOneExtraItem() {
        val result = SplitScrollPageDistributor.distribute((1..5).toList())
        assertEquals(3, result.left.size)
        assertEquals(2, result.right.size)
    }

    @Test
    fun placementIndicesPreserveEveryItemExactlyOnce() {
        val source = listOf("a", "b", "c", "d", "e")
        val result = SplitScrollPageDistributor.distribute(source)
        val placements = result.inSourceOrder()

        assertEquals(source, placements.map { it.item })
        assertEquals(source.indices.toList(), placements.map { it.originalIndex })
        assertEquals(source.size, result.left.size + result.right.size)
    }

    @Test
    fun placementLookupResolvesColumnByEffectiveIndex() {
        val result = SplitScrollPageDistributor.distribute(listOf("a", "b", "c"))
        assertEquals(
            SplitScrollPageDistributor.Placement(1, "b", SplitScrollPageDistributor.Column.RIGHT),
            result.placementForOriginalIndex(1),
        )
        assertNull(result.placementForOriginalIndex(-1))
        assertNull(result.placementForOriginalIndex(99))
    }
}
