package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollHostState.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSplitScrollSelectionCoordinatorTest {

    private fun coordinator(): WebtoonSplitScrollSelectionCoordinator {
        val plan = WebtoonSplitScrollPlan.create<Any>(
            listOf("left-page", "right-page", "transition", "next-page"),
        ) { it == "transition" }
        return WebtoonSplitScrollSelectionCoordinator(plan)
    }

    @Test
    fun `each column callback maps to its original source index`() {
        val coordinator = coordinator()
        var state = coordinator.initialState()
        val (leftHost, leftBinding) = state.host.attach(Column.LEFT)
        val (bothHosts, rightBinding) = leftHost.attach(Column.RIGHT)
        state = state.copy(host = bothHosts)

        val left = coordinator.onColumnScrolled(
            state, leftBinding, 0, 0, 120, reachedSegmentEnd = true,
        )
        val right = coordinator.onColumnScrolled(
            left.state, rightBinding, 0, 1, 45, reachedSegmentEnd = true,
        )

        assertTrue(left.accepted)
        assertEquals(0, left.selection?.sourceIndex)
        assertEquals(1, right.selection?.sourceIndex)
        assertEquals(120, right.state.host.stateFor(Column.LEFT).scrollOffsetPx)
        assertEquals(45, right.state.host.stateFor(Column.RIGHT).scrollOffsetPx)
    }

    @Test
    fun `chapter boundary is selected only after both columns reach their end`() {
        val coordinator = coordinator()
        var state = coordinator.initialState()
        val (leftHost, leftBinding) = state.host.attach(Column.LEFT)
        val (bothHosts, rightBinding) = leftHost.attach(Column.RIGHT)
        state = state.copy(host = bothHosts)

        val left = coordinator.onColumnScrolled(
            state, leftBinding, 0, 0, 200, reachedSegmentEnd = true,
        )
        assertFalse(left.boundaryReady)
        assertEquals(0, left.selection?.sourceIndex)

        val right = coordinator.onColumnScrolled(
            left.state, rightBinding, 0, 1, 300, reachedSegmentEnd = true,
        )
        assertTrue(right.boundaryReady)
        assertEquals(2, right.selection?.sourceIndex)
        assertTrue(right.selection?.isBoundary == true)
    }

    @Test
    fun `direct transition navigation marks both columns complete`() {
        val coordinator = coordinator()
        val result = coordinator.selectBoundary(coordinator.initialState())

        assertTrue(result?.accepted == true)
        assertTrue(result?.boundaryReady == true)
        assertEquals(2, result?.selection?.sourceIndex)
        assertTrue(result?.selection?.isBoundary == true)
        assertTrue(result?.state?.barrier?.bothColumnsAtEnd == true)
    }

    @Test
    fun `stale lifecycle callback is rejected without changing state`() {
        val coordinator = coordinator()
        var state = coordinator.initialState()
        val (attached, oldBinding) = state.host.attach(Column.LEFT)
        val (reattached, _) = attached.detach(Column.LEFT).attach(Column.LEFT)
        state = state.copy(host = reattached)

        val result = coordinator.onColumnScrolled(
            state, oldBinding, 0, 0, 500, reachedSegmentEnd = true,
        )

        assertFalse(result.accepted)
        assertNull(result.selection)
        assertEquals(state, result.state)
    }

    @Test
    fun `stale segment callback is rejected`() {
        val coordinator = coordinator()
        var state = coordinator.initialState()
        val (host, binding) = state.host.attach(Column.LEFT)
        state = state.copy(host = host)

        val result = coordinator.onColumnScrolled(
            state, binding, 1, 0, 100, reachedSegmentEnd = false,
        )

        assertFalse(result.accepted)
        assertNull(result.selection)
    }

    @Test
    fun `cannot advance before boundary and can advance after both columns finish`() {
        val coordinator = coordinator()
        var state = coordinator.initialState()
        assertNull(coordinator.advancePastBoundary(state))

        val (leftHost, leftBinding) = state.host.attach(Column.LEFT)
        val (bothHosts, rightBinding) = leftHost.attach(Column.RIGHT)
        state = state.copy(host = bothHosts)
        state = coordinator.onColumnScrolled(
            state, leftBinding, 0, 0, 100, reachedSegmentEnd = true,
        ).state
        state = coordinator.onColumnScrolled(
            state, rightBinding, 0, 1, 150, reachedSegmentEnd = true,
        ).state

        val next = coordinator.advancePastBoundary(state)
        assertEquals(1, next?.barrier?.segmentIndex)
        assertNull(next?.selectedSourceIndex)
    }
    @Test
    fun `leading and consecutive empty segments expose their boundaries immediately`() {
        val plan = WebtoonSplitScrollPlan.create<Any>(
            listOf("first-transition", "second-transition", "page"),
        ) { it.toString().endsWith("transition") }
        val coordinator = WebtoonSplitScrollSelectionCoordinator(plan)

        val first = coordinator.readyBoundary(coordinator.initialState())
        assertTrue(first?.boundaryReady == true)
        assertEquals(0, first?.selection?.sourceIndex)

        val secondState = coordinator.advancePastBoundary(first!!.state)
        val second = coordinator.readyBoundary(secondState!!)
        assertTrue(second?.boundaryReady == true)
        assertEquals(1, second?.selection?.sourceIndex)
    }

}
