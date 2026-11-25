package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSplitScrollBarrierCoordinatorTest {

    private fun coordinator(): WebtoonSplitScrollBarrierCoordinator<String> {
        val plan = WebtoonSplitScrollPlan.create(
            listOf("left-page", "right-page", "chapter-transition", "next-page"),
        ) { it == "chapter-transition" }
        return WebtoonSplitScrollBarrierCoordinator(plan)
    }

    @Test
    fun `one column reaching end does not release full width boundary`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()

        val waiting = coordinator.markColumnAtEnd(initial, 0, Column.LEFT)

        assertTrue(waiting.leftAtEnd)
        assertFalse(waiting.rightAtEnd)
        assertFalse(coordinator.isBoundaryReady(waiting))
        assertNull(coordinator.advance(waiting))
    }

    @Test
    fun `boundary releases only after both independent columns reach end`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()

        val leftWaiting = coordinator.markColumnAtEnd(initial, 0, Column.LEFT)
        val bothWaiting = coordinator.markColumnAtEnd(leftWaiting, 0, Column.RIGHT)

        assertTrue(coordinator.isBoundaryReady(bothWaiting))
        assertEquals(1, coordinator.advance(bothWaiting)?.segmentIndex)
        assertEquals(false, coordinator.advance(bothWaiting)?.leftAtEnd)
        assertEquals(false, coordinator.advance(bothWaiting)?.rightAtEnd)
    }

    @Test
    fun `stale callbacks from another segment are ignored`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()

        val unchanged = coordinator.markColumnAtEnd(initial, 1, Column.LEFT)

        assertEquals(initial, unchanged)
        assertFalse(coordinator.isBoundaryReady(unchanged))
    }

    @Test
    fun `full width and unknown columns cannot satisfy a column arrival`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()

        val unchanged = coordinator.markColumnAtEnd(initial, 0, Column.FULL_WIDTH)

        assertEquals(initial, unchanged)
        assertFalse(coordinator.isBoundaryReady(unchanged))
    }

    @Test
    fun `final segment has no boundary to advance through`() {
        val coordinator = coordinator()
        val state = coordinator.initialState(1)
        val both = coordinator.markColumnAtEnd(
            coordinator.markColumnAtEnd(state, 1, Column.LEFT),
            1,
            Column.RIGHT,
        )

        assertFalse(coordinator.isBoundaryReady(both))
        assertNull(coordinator.advance(both))
    }
}
