package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebtoonSplitScrollPlanTest {

    @Test
    fun `ordinary items alternate within one segment and retain source indices`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("p0", "p1", "p2", "p3")) { false }

        assertEquals(1, plan.segments.size)
        assertEquals(listOf(0, 2), plan.segments.single().left.map { it.originalIndex })
        assertEquals(listOf(1, 3), plan.segments.single().right.map { it.originalIndex })
        assertEquals(listOf(0, 1, 2, 3), plan.inSourceOrder().map { it.originalIndex })
        assertEquals(Column.LEFT, plan.placementForSourceIndex(0)?.column)
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(1)?.column)
    }

    @Test
    fun `chapter transition closes segment and starts following segment at left`() {
        val items = listOf("before-a", "before-b", "transition", "after-a", "after-b")
        val plan = WebtoonSplitScrollPlan.create(items) { it == "transition" }

        assertEquals(2, plan.segments.size)
        assertEquals(listOf(0), plan.segments[0].left.map { it.originalIndex })
        assertEquals(listOf(1), plan.segments[0].right.map { it.originalIndex })
        assertEquals(2, plan.segments[0].boundaryAfter?.originalIndex)
        assertEquals(Column.FULL_WIDTH, plan.placementForSourceIndex(2)?.column)
        assertEquals(listOf(3), plan.segments[1].left.map { it.originalIndex })
        assertEquals(listOf(4), plan.segments[1].right.map { it.originalIndex })
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }

    @Test
    fun `leading and consecutive boundaries are preserved as empty segments`() {
        val items = listOf("t0", "page", "t1", "t2", "last")
        val plan = WebtoonSplitScrollPlan.create(items) { it.startsWith("t") }

        assertEquals(4, plan.segments.size)
        assertEquals(0, plan.segments[0].left.size)
        assertEquals(0, plan.segments[0].right.size)
        assertEquals(0, plan.segments[0].boundaryAfter?.originalIndex)
        assertEquals(listOf(1), plan.segments[1].left.map { it.originalIndex })
        assertEquals(2, plan.segments[1].boundaryAfter?.originalIndex)
        assertEquals(3, plan.segments[2].boundaryAfter?.originalIndex)
        assertEquals(listOf(4), plan.segments[3].left.map { it.originalIndex })
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }

    @Test
    fun `source snapshot validation rejects changed identities and sizes`() {
        data class Item(val id: String)

        val first = Item("same")
        val second = Item("other")
        val source = listOf(first, second)
        val plan = WebtoonSplitScrollPlan.create(source) { false }

        assertEquals(true, plan.matchesSource(source))
        assertEquals(false, plan.matchesSource(listOf(Item("same"), second)))
        assertEquals(false, plan.matchesSource(listOf(first)))
        assertEquals(false, plan.matchesSource(listOf(first, second, Item("extra"))))
    }

    @Test
    fun `empty input retains a trailing empty segment and invalid indices are safe`() {
        val plan = WebtoonSplitScrollPlan.create(emptyList<String>()) { false }

        assertEquals(0, plan.sourceSize)
        assertEquals(1, plan.segments.size)
        assertEquals(emptyList<Int>(), plan.inSourceOrder().map { it.originalIndex })
        assertNull(plan.placementForSourceIndex(-1))
        assertNull(plan.placementForSourceIndex(0))
    }
}
