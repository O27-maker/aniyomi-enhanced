package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerSplitScrollPlanTest {
    @Test
    fun `pages alternate between columns in source order`() {
        val source = listOf("a", "b", "c", "d", "e")
        val plan = PagerSplitScrollPlan.create(source, { false }, PagerSplitScrollPlan.Column.LEFT)

        assertEquals(listOf("a", "c", "e"), plan.segments.single().left.map { it.item })
        assertEquals(listOf("b", "d"), plan.segments.single().right.map { it.item })
        assertTrue(plan.matchesSource(source))
    }

    @Test
    fun `right first placement follows effective source order`() {
        val source = listOf("last", "middle", "first")
        val plan = PagerSplitScrollPlan.create(source, { false }, PagerSplitScrollPlan.Column.RIGHT)

        assertEquals(listOf("last", "first"), plan.segments.single().right.map { it.item })
        assertEquals(listOf("middle"), plan.segments.single().left.map { it.item })
        assertTrue(plan.matchesSource(source))
    }

    @Test
    fun `chapter boundaries stay full width and restart alternation`() {
        val source = listOf("a", "b", "transition", "c", "d")
        val plan = PagerSplitScrollPlan.create(source, { it == "transition" }, PagerSplitScrollPlan.Column.LEFT)

        assertEquals("transition", plan.segments[0].boundaryAfter?.item)
        assertEquals(0, plan.segmentIndexForSourceIndex(2))
        assertEquals("c", plan.segments[1].left.single().item)
        assertEquals("d", plan.segments[1].right.single().item)
        assertTrue(plan.matchesSource(source))
    }

    @Test
    fun `right first placement restarts on right after chapter boundary`() {
        val source = listOf("before", "transition", "after-a", "after-b")
        val plan = PagerSplitScrollPlan.create(
            source,
            { it == "transition" },
            PagerSplitScrollPlan.Column.RIGHT,
        )

        assertEquals(PagerSplitScrollPlan.Column.RIGHT, plan.placementForSourceIndex(0)?.column)
        assertEquals(PagerSplitScrollPlan.Column.FULL_WIDTH, plan.placementForSourceIndex(1)?.column)
        assertEquals(PagerSplitScrollPlan.Column.RIGHT, plan.placementForSourceIndex(2)?.column)
        assertEquals(PagerSplitScrollPlan.Column.LEFT, plan.placementForSourceIndex(3)?.column)
        assertTrue(plan.matchesSource(source))
    }

    @Test
    fun `identity replacement invalidates source snapshot`() {
        val original = String(charArrayOf('a'))
        val source = listOf(original, "b")
        val plan = PagerSplitScrollPlan.create(source, { false }, PagerSplitScrollPlan.Column.LEFT)

        assertTrue(plan.matchesSource(source))
        assertFalse(plan.matchesSource(listOf(String(charArrayOf('a')), "b")))
    }

    @Test
    fun `source index resolves original placement`() {
        val source = listOf("a", "b", "c")
        val plan = PagerSplitScrollPlan.create(source, { false }, PagerSplitScrollPlan.Column.LEFT)

        assertEquals(PagerSplitScrollPlan.Column.RIGHT, plan.placementForSourceIndex(1)?.column)
        assertEquals(2, plan.placementForSourceIndex(2)?.sourceIndex)
        assertEquals(null, plan.placementForSourceIndex(3))
    }
}
