package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollHostRestore.Destination
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WebtoonSplitScrollHostRebuildSnapshotTest {

    private data class Item(val name: String, val boundary: Boolean = false)

    @Test
    fun `restores selection and independent viewport when earlier segment shifts index`() {
        val before = Item("before")
        val boundary = Item("chapter", boundary = true)
        val selected = Item("selected")
        val sibling = Item("sibling")
        val originalItems = listOf(before, boundary, selected, sibling)
        val original = WebtoonSplitScrollPlan.create(originalItems) { it.boundary }
        val state = WebtoonSplitScrollHostState()
            .resize(Column.LEFT, 43)
            .resize(Column.RIGHT, 57)
            .setZoom(Column.LEFT, 1.5f)
        val snapshot = WebtoonSplitScrollHostRebuildSnapshot.capture(original, selected, state)!!

        val insertedBoundary = Item("inserted-chapter", boundary = true)
        val rebuiltItems = listOf(Item("new-page"), insertedBoundary) + originalItems
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            rebuiltItems,
            selected,
        ) { it.boundary }

        val restored = WebtoonSplitScrollHostRebuildSnapshot.restore(rebuild, snapshot)

        assertEquals(2, restored?.selection.segmentIndex)
        assertSame(selected, restored?.selection?.item)
        assertEquals(Destination.Column(Column.LEFT, 0), restored?.selection?.destination)
        assertEquals(43, restored?.viewportState?.left?.widthPercent)
        assertEquals(57, restored?.viewportState?.right?.widthPercent)
        assertEquals(1.5f, restored?.viewportState?.left?.zoom)
    }

    @Test
    fun `captures and restores a selected chapter boundary with its preceding segment`() {
        val page = Item("page")
        val boundary = Item("chapter", boundary = true)
        val original = WebtoonSplitScrollPlan.create(listOf(page, boundary)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostRebuildSnapshot.capture(
            original,
            boundary,
            WebtoonSplitScrollHostState(),
        )!!
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            listOf(page, boundary),
            boundary,
        ) { it.boundary }

        val restored = WebtoonSplitScrollHostRebuildSnapshot.restore(rebuild, snapshot)

        assertEquals(0, restored?.selection?.segmentIndex)
        assertEquals(Destination.FullWidthBoundary, restored?.selection?.destination)
        assertSame(boundary, restored?.selection?.item)
    }

    @Test
    fun `rejects combined restore when selected item was removed`() {
        val selected = Item("selected")
        val original = WebtoonSplitScrollPlan.create(listOf(selected, Item("sibling"))) { it.boundary }
        val snapshot = WebtoonSplitScrollHostRebuildSnapshot.capture(
            original,
            selected,
            WebtoonSplitScrollHostState(),
        )!!
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            listOf(Item("replacement"), Item("sibling")),
            selected,
        ) { it.boundary }

        assertNull(WebtoonSplitScrollHostRebuildSnapshot.restore(rebuild, snapshot))
    }

    @Test
    fun `rejects combined restore when segment membership changes`() {
        val selected = Item("selected")
        val sibling = Item("sibling")
        val original = WebtoonSplitScrollPlan.create(listOf(selected, sibling)) { it.boundary }
        val snapshot = WebtoonSplitScrollHostRebuildSnapshot.capture(
            original,
            selected,
            WebtoonSplitScrollHostState(),
        )!!
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            listOf(selected, Item("new-sibling")),
            selected,
        ) { it.boundary }

        assertNull(WebtoonSplitScrollHostRebuildSnapshot.restore(rebuild, snapshot))
    }

    @Test
    fun `capture rejects selection not present in plan`() {
        val plan = WebtoonSplitScrollPlan.create(listOf(Item("present"))) { it.boundary }

        assertNull(
            WebtoonSplitScrollHostRebuildSnapshot.capture(
                plan,
                Item("not-present"),
                WebtoonSplitScrollHostState(),
            ),
        )
    }
}
