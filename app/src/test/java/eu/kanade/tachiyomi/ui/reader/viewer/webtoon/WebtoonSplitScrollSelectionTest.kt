package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSplitScrollSelectionTest {

    private fun plan() = WebtoonSplitScrollPlan.create(
        listOf("page-0", "page-1", "transition", "page-3", "page-4"),
    ) { it == "transition" }

    @Test
    fun `malformed projection cannot resolve a column selection`() {
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = emptyList(),
                    right = listOf(
                        WebtoonSplitScrollPlan.Placement(0, "page", Column.RIGHT),
                    ),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 1,
        )

        assertNull(WebtoonSplitScrollSelection.fromColumn(malformed, 0, Column.RIGHT, 0))
        assertNull(WebtoonSplitScrollSelection.fromColumn(malformed, 0, Column.LEFT, 0))
    }

    @Test
    fun `left and right callbacks resolve their own original adapter item`() {
        val plan = plan()

        val left = WebtoonSplitScrollSelection.fromColumn(plan, 0, Column.LEFT, 0)
        val right = WebtoonSplitScrollSelection.fromColumn(plan, 0, Column.RIGHT, 1)

        assertEquals(0, left?.sourceIndex)
        assertEquals("page-0", left?.item)
        assertEquals(1, right?.sourceIndex)
        assertEquals("page-1", right?.item)
        assertFalse(left?.isBoundary ?: true)
        assertFalse(right?.isBoundary ?: true)
    }

    @Test
    fun `wrong column and stale segment indices are rejected`() {
        val plan = plan()

        assertNull(WebtoonSplitScrollSelection.fromColumn(plan, 0, Column.RIGHT, 0))
        assertNull(WebtoonSplitScrollSelection.fromColumn(plan, 1, Column.LEFT, 0))
        assertNull(WebtoonSplitScrollSelection.fromColumn(plan, 0, Column.LEFT, 99))
    }

    @Test
    fun `chapter transition cannot be selected before both columns finish`() {
        val plan = plan()
        val coordinator = WebtoonSplitScrollBarrierCoordinator(plan)
        val initial = coordinator.initialState()
        val leftDone = coordinator.markColumnAtEnd(initial, 0, Column.LEFT)

        assertNull(WebtoonSplitScrollSelection.boundary(plan, leftDone))
    }

    @Test
    fun `chapter transition resolves to its original source index after both columns finish`() {
        val plan = plan()
        val coordinator = WebtoonSplitScrollBarrierCoordinator(plan)
        val initial = coordinator.initialState()
        val bothDone = coordinator.markColumnAtEnd(
            coordinator.markColumnAtEnd(initial, 0, Column.LEFT),
            0,
            Column.RIGHT,
        )

        val selection = WebtoonSplitScrollSelection.boundary(plan, bothDone)
        assertEquals(2, selection?.sourceIndex)
        assertEquals("transition", selection?.item)
        assertTrue(selection?.isBoundary == true)
    }

    @Test
    fun `boundary from a segment without a transition is never selectable`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("only-page")) { false }
        val coordinator = WebtoonSplitScrollBarrierCoordinator(plan)
        val initial = coordinator.initialState()
        val bothDone = coordinator.markColumnAtEnd(
            coordinator.markColumnAtEnd(initial, 0, Column.LEFT),
            0,
            Column.RIGHT,
        )

        assertNull(WebtoonSplitScrollSelection.boundary(plan, bothDone))
    }
}
