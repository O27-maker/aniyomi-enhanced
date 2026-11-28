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
    fun `restore sanitizes non finite zoom values`() {
        val items = listOf(Item("one"), Item("two"))
        val plan = WebtoonSplitScrollPlan.create(items) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            plan,
            0,
            WebtoonSplitScrollHostState(),
        )!!.copy(
            left = WebtoonSplitScrollHostViewportSnapshot.Viewport(
                widthPercent = 50,
                zoom = Float.NaN,
                scrollOffsetPx = 0,
                generation = 2,
            ),
            right = WebtoonSplitScrollHostViewportSnapshot.Viewport(
                widthPercent = 50,
                zoom = Float.POSITIVE_INFINITY,
                scrollOffsetPx = 0,
                generation = 4,
            ),
        )

        val restored = WebtoonSplitScrollHostViewportSnapshot.restore(plan, snapshot)

        assertEquals(WebtoonSplitScrollHostState.DEFAULT_ZOOM, restored?.left?.zoom)
        assertEquals(WebtoonSplitScrollHostState.DEFAULT_ZOOM, restored?.right?.zoom)
    }

    @Test
    fun `restore clamps invalid viewport dimensions and offsets`() {
        val items = listOf(Item("one"), Item("two"))
        val plan = WebtoonSplitScrollPlan.create(items) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            plan,
            0,
            WebtoonSplitScrollHostState(),
        )!!.copy(
            left = WebtoonSplitScrollHostViewportSnapshot.Viewport(
                widthPercent = Int.MIN_VALUE,
                zoom = 1f,
                scrollOffsetPx = Int.MIN_VALUE,
                generation = 5,
            ),
            right = WebtoonSplitScrollHostViewportSnapshot.Viewport(
                widthPercent = Int.MAX_VALUE,
                zoom = 1f,
                scrollOffsetPx = Int.MAX_VALUE,
                generation = 7,
            ),
        )

        val restored = WebtoonSplitScrollHostViewportSnapshot.restore(plan, snapshot)

        assertEquals(WebtoonSplitScrollHostState.MIN_WIDTH_PERCENT, restored?.left?.widthPercent)
        assertEquals(WebtoonSplitScrollHostState.MAX_WIDTH_PERCENT, restored?.right?.widthPercent)
        assertEquals(0, restored?.left?.scrollOffsetPx)
        assertEquals(Int.MAX_VALUE, restored?.right?.scrollOffsetPx)
        assertEquals(6L, restored?.left?.generation)
        assertEquals(8L, restored?.right?.generation)
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

    @Test
    fun `restores segment after earlier chapter boundary shifts its index`() {
        val firstBoundary = Item("chapter-one", boundary = true)
        val secondBoundary = Item("chapter-two", boundary = true)
        val selectedSegmentPages = listOf(Item("three"), Item("four"))
        val originalItems =
            listOf(Item("one"), firstBoundary) + selectedSegmentPages + listOf(secondBoundary, Item("five"))
        val original = WebtoonSplitScrollPlan.create(originalItems) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            original,
            1,
            WebtoonSplitScrollHostState().resize(Column.LEFT, 42),
        )!!

        val insertedBoundary = Item("new-earlier-chapter", boundary = true)
        val rebuilt = WebtoonSplitScrollPlan.create(
            listOf(Item("new-page"), insertedBoundary) + originalItems,
        ) { it.boundary }

        assertEquals(2, WebtoonSplitScrollHostViewportSnapshot.resolveSegmentIndex(rebuilt, snapshot))
        assertEquals(42, WebtoonSplitScrollHostViewportSnapshot.restore(rebuilt, snapshot)?.left?.widthPercent)
    }

    @Test
    fun `rejects viewport restore when adjacent chapter boundary is replaced`() {
        val before = Item("before", boundary = true)
        val after = Item("after", boundary = true)
        val pages = listOf(Item("page-one"), Item("page-two"))
        val original = WebtoonSplitScrollPlan.create(listOf(before) + pages + listOf(after)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(original, 1, WebtoonSplitScrollHostState())!!
        val replacement = Item("replacement", boundary = true)
        val changed = WebtoonSplitScrollPlan.create(listOf(before) + pages + listOf(replacement)) { it.boundary }

        assertNull(WebtoonSplitScrollHostViewportSnapshot.resolveSegmentIndex(changed, snapshot))
        assertNull(WebtoonSplitScrollHostViewportSnapshot.restore(changed, snapshot))
    }

    @Test
    fun `preserves empty segment between consecutive chapter boundaries`() {
        val firstBoundary = Item("first", boundary = true)
        val secondBoundary = Item("second", boundary = true)
        val original = WebtoonSplitScrollPlan.create(listOf(firstBoundary, secondBoundary)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(original, 1, WebtoonSplitScrollHostState())!!
        val rebuilt = WebtoonSplitScrollPlan.create(listOf(firstBoundary, secondBoundary)) { it.boundary }

        assertEquals(1, WebtoonSplitScrollHostViewportSnapshot.resolveSegmentIndex(rebuilt, snapshot))
        assertTrue(WebtoonSplitScrollHostViewportSnapshot.restore(rebuilt, snapshot) != null)
    }

    @Test
    fun `capture and restore reject malformed source projections`() {
        val first = Item("first")
        val second = Item("second")
        val valid = WebtoonSplitScrollPlan.create(listOf(first, second)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostViewportSnapshot.capture(
            valid,
            0,
            WebtoonSplitScrollHostState(),
        )!!
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, WebtoonSplitScrollPlan.Column.LEFT),
                        WebtoonSplitScrollPlan.Placement(1, second, WebtoonSplitScrollPlan.Column.LEFT),
                    ),
                    right = emptyList(),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 2,
        )

        assertNull(
            WebtoonSplitScrollHostViewportSnapshot.capture(
                malformed,
                0,
                WebtoonSplitScrollHostState(),
            ),
        )
        assertNull(WebtoonSplitScrollHostViewportSnapshot.resolveSegmentIndex(malformed, snapshot))
        assertNull(WebtoonSplitScrollHostViewportSnapshot.restore(malformed, snapshot))
    }
}
