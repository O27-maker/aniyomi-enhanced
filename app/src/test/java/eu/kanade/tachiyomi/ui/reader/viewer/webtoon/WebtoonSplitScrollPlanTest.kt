package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.pager.SplitScrollPageDistributor.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebtoonSplitScrollPlanTest {

    @Test
    fun `ordinary items alternate between columns while retaining source indices`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("p0", "p1", "p2", "p3")) { false }

        assertEquals(listOf(0, 2), plan.left.map { it.originalIndex })
        assertEquals(listOf(1, 3), plan.right.map { it.originalIndex })
        assertEquals(listOf(0, 1, 2, 3), plan.inSourceOrder().map { it.originalIndex })
        assertEquals(Column.LEFT, plan.placementForSourceIndex(0)?.column)
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(1)?.column)
    }

    @Test
    fun `chapter transitions remain full width and restart following segment on left`() {
        val items = listOf("before-a", "before-b", "transition", "after-a", "after-b")
        val plan = WebtoonSplitScrollPlan.create(items) { it == "transition" }

        assertEquals(listOf(0), plan.left.map { it.originalIndex })
        assertEquals(listOf(1), plan.right.map { it.originalIndex })
        assertEquals(listOf(2), plan.fullWidth.map { it.originalIndex })
        assertEquals(Column.FULL_WIDTH, plan.placementForSourceIndex(2)?.column)
        assertEquals(Column.LEFT, plan.placementForSourceIndex(3)?.column)
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(4)?.column)
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }

    @Test
    fun `empty and invalid source indices are handled safely`() {
        val plan = WebtoonSplitScrollPlan.create(emptyList<String>()) { false }

        assertEquals(0, plan.sourceSize)
        assertEquals(emptyList<Int>(), plan.inSourceOrder().map { it.originalIndex })
        assertNull(plan.placementForSourceIndex(-1))
        assertNull(plan.placementForSourceIndex(0))
    }
}
