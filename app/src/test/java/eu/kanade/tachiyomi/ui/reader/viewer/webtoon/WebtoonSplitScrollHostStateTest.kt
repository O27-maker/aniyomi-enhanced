package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollHostState.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSplitScrollHostStateTest {

    @Test
    fun `left and right viewport state are independent`() {
        val state = WebtoonSplitScrollHostState()
            .resize(Column.LEFT, 35)
            .resize(Column.RIGHT, 65)
            .setZoom(Column.LEFT, 2f)
            .setZoom(Column.RIGHT, 1.25f)

        assertEquals(35, state.stateFor(Column.LEFT).widthPercent)
        assertEquals(65, state.stateFor(Column.RIGHT).widthPercent)
        assertEquals(2f, state.stateFor(Column.LEFT).zoom)
        assertEquals(1.25f, state.stateFor(Column.RIGHT).zoom)
    }

    @Test
    fun `scroll offsets are recorded independently per binding`() {
        val (withLeft, leftBinding) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val (withBoth, rightBinding) = withLeft.attach(Column.RIGHT)
        val scrolled = withBoth
            .recordScroll(leftBinding, 420)
            .recordScroll(rightBinding, 125)

        assertEquals(420, scrolled.stateFor(Column.LEFT).scrollOffsetPx)
        assertEquals(125, scrolled.stateFor(Column.RIGHT).scrollOffsetPx)
    }

    @Test
    fun `stale callbacks after reattach are ignored`() {
        val (attached, oldBinding) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val (reattached, newBinding) = attached.detach(Column.LEFT).attach(Column.LEFT)
        val result = reattached.recordScroll(oldBinding, 900)

        assertSame(reattached, result)
        assertNotEquals(oldBinding.generation, newBinding.generation)
        assertEquals(0, result.stateFor(Column.LEFT).scrollOffsetPx)
    }

    @Test
    fun `detaching one column does not detach the other`() {
        val (withLeft, _) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val (withBoth, _) = withLeft.attach(Column.RIGHT)
        val detachedLeft = withBoth.detach(Column.LEFT)

        assertFalse(detachedLeft.stateFor(Column.LEFT).attached)
        assertTrue(detachedLeft.stateFor(Column.RIGHT).attached)
    }

    @Test
    fun `width and zoom values are clamped independently`() {
        val state = WebtoonSplitScrollHostState()
            .resize(Column.LEFT, 1)
            .resize(Column.RIGHT, 99)
            .setZoom(Column.LEFT, 0.1f)
            .setZoom(Column.RIGHT, 5f)

        assertEquals(20, state.stateFor(Column.LEFT).widthPercent)
        assertEquals(80, state.stateFor(Column.RIGHT).widthPercent)
        assertEquals(0.5f, state.stateFor(Column.LEFT).zoom)
        assertEquals(3f, state.stateFor(Column.RIGHT).zoom)
    }

    @Test
    fun `non finite zoom values reset to the default`() {
        val state = WebtoonSplitScrollHostState()
            .setZoom(Column.LEFT, Float.NaN)
            .setZoom(Column.RIGHT, Float.POSITIVE_INFINITY)

        assertEquals(WebtoonSplitScrollHostState.DEFAULT_ZOOM, state.left.zoom)
        assertEquals(WebtoonSplitScrollHostState.DEFAULT_ZOOM, state.right.zoom)
    }

    @Test
    fun `scroll offset cannot become negative`() {
        val (attached, binding) = WebtoonSplitScrollHostState().attach(Column.RIGHT)
        val result = attached.recordScroll(binding, -10)

        assertEquals(0, result.stateFor(Column.RIGHT).scrollOffsetPx)
    }

    @Test
    fun `detaching one column invalidates only its binding`() {
        val (withLeft, leftBinding) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val (withBoth, rightBinding) = withLeft.attach(Column.RIGHT)

        val detachedLeft = withBoth.detach(Column.LEFT)
        val afterCallbacks = detachedLeft
            .recordScroll(leftBinding, 500)
            .recordScroll(rightBinding, 250)

        assertEquals(0, afterCallbacks.left.scrollOffsetPx)
        assertEquals(250, afterCallbacks.right.scrollOffsetPx)
        assertFalse(afterCallbacks.left.attached)
        assertTrue(afterCallbacks.right.attached)
    }
}
