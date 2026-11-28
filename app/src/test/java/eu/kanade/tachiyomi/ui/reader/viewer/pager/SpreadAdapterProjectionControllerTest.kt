package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadAdapterProjectionControllerTest {

    @Test
    fun decodedOrientationRefreshKeepsActivePageIdentity() {
        val first = ReaderPage(0)
        val active = ReaderPage(1)
        val last = ReaderPage(2)
        val items = listOf(first, active, last)
        val controller = SpreadAdapterProjectionController()

        val initial = controller.rebuild(items, active, { null })
        assertEquals(SpreadAdapterNavigationMap.Anchor(1, 1), initial.selectedAnchor)

        val refreshed = controller.refreshIfSourceMatches(items, active, { true })
        assertEquals(SpreadAdapterNavigationMap.Anchor(0, 1), refreshed?.selectedAnchor)
        assertSame(active, (refreshed?.units?.get(0) as SpreadAdapterItemMapper.Unit.Pair).first)
    }

    @Test
    fun lateOrientationCallbackCannotRefreshChangedSourceSequence() {
        val first = ReaderPage(0)
        val active = ReaderPage(1)
        val replacement = ReaderPage(2)
        val controller = SpreadAdapterProjectionController()
        controller.rebuild(listOf(first, active), active, { true })

        val result = controller.refreshIfSourceMatches(
            listOf(first, replacement),
            replacement,
            { true },
        )

        assertNull(result)
        assertFalse(controller.matchesSource(listOf(first, replacement)))
        assertTrue(controller.matchesSource(listOf(first, active)))
    }

    @Test
    fun clearDropsProjectionAndSourceReferences() {
        val page = ReaderPage(0)
        val controller = SpreadAdapterProjectionController()
        controller.rebuild(listOf(page), page, { true })

        controller.clear()

        assertNull(controller.current)
        assertFalse(controller.matchesSource(listOf(page)))
    }
}
