package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerSplitScrollControllerTest {
    private data class Source(val label: String, val boundary: Boolean = false)

    private fun controller() = PagerSplitScrollController<Source>(
        isBoundary = { it.boundary },
    )

    @Test
    fun `rebuild retains source identity and segment mapping`() {
        val a = Source("a")
        val b = Source("b")
        val boundary = Source("transition", boundary = true)
        val c = Source("c")
        val source = listOf(a, b, boundary, c)
        val controller = controller()

        assertTrue(controller.rebuild(source))
        assertTrue(controller.matchesSource(source))
        assertEquals(2, controller.segmentCount)
        assertEquals(0, controller.segmentAtSourceIndex(0)?.index)
        assertEquals(1, controller.segmentAtSourceIndex(3)?.index)
        assertEquals(PagerSplitScrollPlan.Column.RIGHT to 0, controller.columnPositionForSourceIndex(1))
        assertEquals(boundary, controller.boundaryAfterSegment(0)?.item)
    }

    @Test
    fun `stale source identity is rejected`() {
        val original = Source("page")
        val controller = controller()
        assertTrue(controller.rebuild(listOf(original)))

        assertFalse(controller.matchesSource(listOf(Source("page"))))
    }

    @Test
    fun `boundary has no column coordinate`() {
        val boundary = Source("transition", boundary = true)
        val controller = controller()
        assertTrue(controller.rebuild(listOf(Source("a"), boundary, Source("b"))))

        assertNull(controller.columnPositionForSourceIndex(1))
        assertEquals(0, controller.segmentAtSourceIndex(1)?.index)
        assertNotNull(controller.boundaryAfterSegment(0))
        assertEquals(1, controller.nextSegmentIndex(0))
        assertNull(controller.nextSegmentIndex(1))
    }

    @Test
    fun `clear releases snapshot and mappings`() {
        val controller = controller()
        assertTrue(controller.rebuild(listOf(Source("a"))))
        controller.clear()

        assertFalse(controller.isActive)
        assertEquals(0, controller.segmentCount)
        assertEquals(-1, controller.sourceIndexOf(Source("a")))
        assertNull(controller.sourceItemAt(0))
    }
}
