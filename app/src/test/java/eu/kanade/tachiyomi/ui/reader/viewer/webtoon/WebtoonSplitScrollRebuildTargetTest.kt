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

        assertEquals(1, target?.sourceIndex)
        assertEquals(Column.RIGHT, target?.column)
        assertEquals(0, target?.columnPosition)
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
        assertEquals(1, target?.segmentIndex)
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
}
