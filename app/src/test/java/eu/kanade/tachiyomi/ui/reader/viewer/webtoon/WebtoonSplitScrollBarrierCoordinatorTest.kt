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
    fun `malformed projection cannot release or advance a chapter barrier`() {
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, "page", Column.LEFT),
                        WebtoonSplitScrollPlan.Placement(1, "transition", Column.LEFT),
                    ),
                    right = emptyList(),
                    boundaryAfter = WebtoonSplitScrollPlan.Placement(
                        2,
                        "transition",
                        Column.FULL_WIDTH,
                    ),
                ),
            ),
            sourceSize = 3,
        )
        val coordinator = WebtoonSplitScrollBarrierCoordinator(malformed)
        val initial = coordinator.initialState()
        val forgedReady = WebtoonSplitScrollBarrierCoordinator.State(
            segmentIndex = 0,
            leftAtEnd = true,
            rightAtEnd = true,
        )

        assertFalse(coordinator.isBoundaryReady(initial))
        assertFalse(coordinator.isBoundaryReady(forgedReady))
        assertEquals(initial, coordinator.markColumnAtEnd(initial, 0, Column.LEFT))
        assertNull(coordinator.advance(forgedReady))
    }

    @Test
    fun `one column reaching end does not release boundary while other has pages`() {
        val coordinator = coordinator()
        val waiting = coordinator.markColumnAtEnd(coordinator.initialState(), 0, Column.LEFT)

        assertTrue(waiting.leftAtEnd)
        assertFalse(waiting.rightAtEnd)
        assertFalse(coordinator.isBoundaryReady(waiting))
        assertNull(coordinator.advance(waiting))
    }

    @Test
    fun `boundary releases after both columns reach end`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()
        val both = coordinator.markColumnAtEnd(
            coordinator.markColumnAtEnd(initial, 0, Column.LEFT),
            0,
            Column.RIGHT,
        )

        assertTrue(coordinator.isBoundaryReady(both))
        assertEquals(1, coordinator.advance(both)?.segmentIndex)
        assertEquals(false, coordinator.advance(both)?.leftAtEnd)
        assertEquals(false, coordinator.advance(both)?.rightAtEnd)
    }

    @Test
    fun `empty column is already complete and cannot deadlock a boundary`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("only-left", "transition")) {
            it == "transition"
        }
        val coordinator = WebtoonSplitScrollBarrierCoordinator(plan)
        val initial = coordinator.initialState()

        assertFalse(initial.leftAtEnd)
        assertTrue(initial.rightAtEnd)
        val ready = coordinator.markColumnAtEnd(initial, 0, Column.LEFT)
        assertTrue(coordinator.isBoundaryReady(ready))
    }

    @Test
    fun `empty leading segment releases its transition immediately`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("transition", "page")) {
            it == "transition"
        }
        val coordinator = WebtoonSplitScrollBarrierCoordinator(plan)
        val initial = coordinator.initialState()

        assertTrue(initial.bothColumnsAtEnd)
        assertTrue(coordinator.isBoundaryReady(initial))
        assertEquals(1, coordinator.advance(initial)?.segmentIndex)
    }

    @Test
    fun `stale callbacks from another segment are ignored`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()
        assertEquals(initial, coordinator.markColumnAtEnd(initial, 1, Column.LEFT))
    }

    @Test
    fun `full width callback cannot satisfy a column arrival`() {
        val coordinator = coordinator()
        val initial = coordinator.initialState()
        assertEquals(initial, coordinator.markColumnAtEnd(initial, 0, Column.FULL_WIDTH))
        assertFalse(coordinator.isBoundaryReady(initial))
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
