package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitScrollPageDistributorDirectionTest {

    @Test
    fun reversedEffectiveSequenceIsDistributedWithoutSecondReverse() {
        val original = listOf("page-1", "page-2", "page-3", "page-4")
        val effectiveR2L = original.reversed()
        val result = SplitScrollPageDistributor.distribute(effectiveR2L)

        assertEquals(listOf("page-4", "page-2"), result.left.map { it.item })
        assertEquals(listOf("page-3", "page-1"), result.right.map { it.item })
        assertEquals(effectiveR2L, result.inSourceOrder().map { it.item })
        assertEquals(original, original.toList())
    }

    @Test
    fun sourceIndicesAreIndicesInTheEffectiveSequence() {
        val effective = listOf("page-5", "page-4", "page-3")
        val result = SplitScrollPageDistributor.distribute(effective)

        assertEquals(listOf(0, 2), result.left.map { it.originalIndex })
        assertEquals(listOf(1), result.right.map { it.originalIndex })
        assertEquals(effective.indices.toList(), result.inSourceOrder().map { it.originalIndex })
    }

    @Test
    fun boundariesInReversedSequenceRemainFullWidth() {
        val original = listOf("page-1", "page-2", "transition", "page-3", "page-4")
        val effectiveR2L = original.reversed()
        val result = SplitScrollPageDistributor.distribute(
            effectiveR2L,
            isBoundary = { it == "transition" },
        )

        assertEquals(listOf("page-4", "page-2"), result.left.map { it.item })
        assertEquals(listOf("page-3", "page-1"), result.right.map { it.item })
        assertEquals(listOf("transition"), result.fullWidth.map { it.item })
        assertEquals(effectiveR2L, result.inSourceOrder().map { it.item })
    }
}
