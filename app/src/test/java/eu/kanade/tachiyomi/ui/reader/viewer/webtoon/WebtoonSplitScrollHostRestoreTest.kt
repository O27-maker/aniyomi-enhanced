package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollHostRestore.Destination
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WebtoonSplitScrollHostRestoreTest {

    private data class Item(val name: String, val boundary: Boolean = false)

    @Test
    fun `maps selected page to its independent column and local position`() {
        val selected = Item("selected")
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = listOf(Item("first"), Item("second"), selected, Item("last")),
            selectedItem = selected,
            isBoundary = { it.boundary },
        )

        val restore = WebtoonSplitScrollHostRestore.from(rebuild)

        assertEquals(0, restore?.segmentIndex)
        assertEquals(2, restore?.sourceIndex)
        assertSame(selected, restore?.item)
        assertEquals(Destination.Column(Column.LEFT, 1), restore?.destination)
    }

    @Test
    fun `maps chapter transition to full width boundary destination`() {
        val boundary = Item("chapter", boundary = true)
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = listOf(Item("page"), boundary, Item("next")),
            selectedItem = boundary,
            isBoundary = { it.boundary },
        )

        val restore = WebtoonSplitScrollHostRestore.from(rebuild)

        assertEquals(0, restore?.segmentIndex)
        assertEquals(1, restore?.sourceIndex)
        assertEquals(Destination.FullWidthBoundary, restore?.destination)
    }

    @Test
    fun `returns null when selected item no longer survives rebuild`() {
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = listOf(Item("replacement")),
            selectedItem = Item("removed"),
            isBoundary = { it.boundary },
        )

        assertNull(WebtoonSplitScrollHostRestore.from(rebuild))
    }

    @Test
    fun `empty source has no host restoration instruction`() {
        val rebuild = WebtoonSplitScrollRebuildTargetResolver.rebuild(
            currentItems = emptyList<Item>(),
            selectedItem = null,
            isBoundary = { it.boundary },
        )

        assertNull(WebtoonSplitScrollHostRestore.from(rebuild))
    }
}
