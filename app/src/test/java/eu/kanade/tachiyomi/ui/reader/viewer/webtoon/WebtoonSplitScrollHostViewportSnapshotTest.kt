package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollHostState.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSplitScrollHostViewportSnapshotTest {

    private data class Item(val name: String, val boundary: Boolean = false)

    @Test
    fun `restores independent viewport values when segment identities are unchanged`() {
        val items = listOf(Item("one"), Item("two"), Item("three"), Item("four"))
        val plan = WebtoonSplitScrollPlan.create(items) { it.boundary }
        val (attachedLeft, leftBinding) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val (attachedBoth, rightBinding) = attachedLeft.attach(Column.RIGHT)
        val state = attachedBoth
            .resize(Column.LEFT, 40)
            .resize(Column.RIGHT, 60)
            .setZoom(Column.LEFT, 1.75f)
            .setZoom(Column.RIGHT, 2.25f)
            .recordScroll(leftBinding, 320)
            .recordScroll(rightBinding, 910)

        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(plan, 0, state)
        val restored = snapshot?.let { WebtoonSplitScrollHostViewportSnapshot.restore(plan, it) }

        assertEquals(40, restored?.left?.widthPercent)
        assertEquals(60, restored?.right?.widthPercent)
        assertEquals(1.75f, restored?.left?.zoom)
        assertEquals(2.25f, restored?.right?.zoom)
        assertEquals(320, restored?.left?.scrollOffsetPx)
        assertEquals(910, restored?.right?.scrollOffsetPx)
        assertFalse(restored?.left?.attached ?: true)
        assertFalse(restored?.right?.attached ?: true)
        assertTrue((restored?.left?.generation ?: 0) > state.left.generation)
        assertTrue((restored?.right?.generation ?: 0) > state.right.generation)
    }

    @Test
    fun `restored generation rejects callbacks from previous host`() {
        val items = listOf(Item("one"), Item("two"))
        val plan = WebtoonSplitScrollPlan.create(items) { it.boundary }
        val (attached, oldBinding) = WebtoonSplitScrollHostState().attach(Column.LEFT)
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(plan, 0, attached)!!
        val restored = WebtoonSplitScrollHostViewportSnapshot.restore(plan, snapshot)!!
        val (newHost, newBinding) = restored.attach(Column.LEFT)

        val afterOldCallback = newHost.recordScroll(oldBinding, 999)
        val afterNewCallback = newHost.recordScroll(newBinding, 123)

        assertSame(newHost, afterOldCallback)
        assertNotEquals(oldBinding.generation, newBinding.generation)
        assertEquals(123, afterNewCallback.left.scrollOffsetPx)
    }

    @Test
    fun `does not restore offsets after column membership changes`() {
        val first = Item("first")
        val second = Item("second")
        val third = Item("third")
        val original = WebtoonSplitScrollPlan.create(listOf(first, second, third)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            original,
            0,
            WebtoonSplitScrollHostState(),
        )!!

        val changed = WebtoonSplitScrollPlan.create(listOf(Item("inserted"), first, second, third)) { it.boundary }

        assertNull(WebtoonSplitScrollHostViewportSnapshot.restore(changed, snapshot))
    }

    @Test
    fun `does not restore offsets when item order changes`() {
        val first = Item("first")
        val second = Item("second")
        val original = WebtoonSplitScrollPlan.create(listOf(first, second)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            original,
            0,
            WebtoonSplitScrollHostState(),
        )!!
        val reordered = WebtoonSplitScrollPlan.create(listOf(second, first)) { it.boundary }

        assertNull(WebtoonSplitScrollHostViewportSnapshot.restore(reordered, snapshot))
    }

    @Test
    fun `capture rejects missing segment`() {
        val plan = WebtoonSplitScrollPlan.create(emptyList<Item>()) { it.boundary }

        assertNull(WebtoonSplitScrollHostViewportSnapshot.capture(plan, 1, WebtoonSplitScrollHostState()))
    }
}
