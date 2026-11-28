package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSpreadPlanControllerTest {

    @Test
    fun decodedOrientationRefreshKeepsTheSameSourceSnapshot() {
        val first = Any()
        val second = Any()
        val items = listOf(first, second)
        val controller = WebtoonSpreadPlanController<Any>()

        val initial = controller.rebuild(items, { false }, { null })
        assertTrue(initial.matchesSource(items))

        val refreshed = controller.refreshIfSourceMatches(items, { false }, { true })
        assertSame(second, (refreshed?.units?.first() as WebtoonSpreadPlan.Unit.Pair).second)
        assertTrue(controller.matchesSource(items))
    }

    @Test
    fun lateDecodeCallbackCannotRebuildAfterSourceMutation() {
        val first = Any()
        val oldSecond = Any()
        val replacement = Any()
        val original = listOf(first, oldSecond)
        val changed = listOf(first, replacement)
        val controller = WebtoonSpreadPlanController<Any>()
        controller.rebuild(original, { false }, { true })

        assertNull(controller.refreshIfSourceMatches(changed, { false }, { true }))
        assertFalse(controller.matchesSource(changed))
        assertTrue(controller.matchesSource(original))
    }

    @Test
    fun clearReleasesProjectionAndSnapshot() {
        val page = Any()
        val controller = WebtoonSpreadPlanController<Any>()
        controller.rebuild(listOf(page), { false }, { true })

        controller.clear()

        assertNull(controller.current)
        assertFalse(controller.matchesSource(listOf(page)))
    }
}
