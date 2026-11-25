package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebtoonSplitScrollRebuildTargetTest {

    private data class Item(val name: String, val boundary: Boolean = false)

    @Test
    fun `restores surviving page by identity after preceding insertion shifts source index`() {
        val selected = Item("selected")
        val plan = WebtoonSplitScrollPlan.create(
            listOf(Item("inserted"), Item("first"), selected, Item("last")),
        ) { it.boundary }

        val target = WebtoonSplitScrollRebuildTargetResolver.resolve(plan, selected)

        assertEquals(2, target?.sourceIndex)
        assertEquals(Column.LEFT, target?.column)
        assertEquals(1, target?.columnPosition)
        assertEquals(0, target?.segmentIndex)
    }

    @Test
    fun `restores chapter boundary as full width target`() {
        val boundary = Item("transition", boundary = true)
        val plan = WebtoonSplitScrollPlan.create(
            listOf(Item("before"), boundary, Item("after")),
        ) { it.boundary }

        val target = WebtoonSplitScrollRebuildTargetResolver.resolve(plan, boundary)

        assertEquals(1, target?.sourceIndex)
        assertEquals(0, target?.segmentIndex)
        assertEquals(Column.FULL_WIDTH, target?.column)
        assertEquals(null, target?.columnPosition)
        assertEquals(true, target?.isBoundary)
    }

    @Test
    fun `does not restore a replaced or removed selected item by stale index`() {
        val selected = Item("selected")
        val replacement = Item("selected")
        val plan = WebtoonSplitScrollPlan.create(listOf(replacement, Item("other"))) { it.boundary }

        assertNull(WebtoonSplitScrollRebuildTargetResolver.resolve(plan, selected))
        assertNull(WebtoonSplitScrollRebuildTargetResolver.resolve(plan, null))
    }
    @Test
    fun `rebuild creates a fresh projection and resolves selection against its new indices`() {
        val selected = Item("selected")
        val items = listOf(Item("new"), Item("first"), selected)

        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = items,
            selectedItem = selected,
            isBoundary = { it.boundary },
        )

        assertEquals(true, rebuild.plan.matchesSource(items))
        assertEquals(2, rebuild.target?.sourceIndex)
        assertEquals(Column.LEFT, rebuild.target?.column)
        assertEquals(1, rebuild.target?.columnPosition)
    }

    @Test
    fun `rebuild with empty source has empty plan and no restore target`() {
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = emptyList<Item>(),
            selectedItem = Item("removed"),
            isBoundary = { it.boundary },
        )

        assertEquals(0, rebuild.plan.sourceSize)
        assertEquals(1, rebuild.plan.segments.size)
        assertEquals(emptyList<Item>(), rebuild.plan.inSourceOrder().map { it.item })
        assertNull(rebuild.target)
    }

}
