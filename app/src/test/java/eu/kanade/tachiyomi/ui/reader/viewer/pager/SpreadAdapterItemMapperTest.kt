package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadAdapterItemMapperTest {

    @Test
    fun emptySequenceMapsToNoUnits() {
        assertEquals(emptyList<SpreadAdapterItemMapper.Unit<Int>>(), SpreadAdapterItemMapper.map(emptyList<Int>()))
    }

    @Test
    fun preservesAdapterIndicesWhenPairing() {
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Pair(10, 0, 11, 1),
                SpreadAdapterItemMapper.Unit.Single(12, 2),
            ),
            SpreadAdapterItemMapper.map(listOf(10, 11, 12)),
        )
    }

    @Test
    fun boundaryItemsRemainSingleAndBreakPairs() {
        val items = listOf("page-a", "transition", "page-b", "page-c")
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("page-a", 0),
                SpreadAdapterItemMapper.Unit.Single("transition", 1),
                SpreadAdapterItemMapper.Unit.Pair("page-b", 2, "page-c", 3),
            ),
            SpreadAdapterItemMapper.map(items, isBoundary = { it == "transition" }),
        )
    }

    @Test
    fun ineligibleItemIsSingleAndDoesNotPairAcrossIt() {
        val items = listOf("page-a", "insert", "page-b", "page-c")
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("page-a", 0),
                SpreadAdapterItemMapper.Unit.Single("insert", 1),
                SpreadAdapterItemMapper.Unit.Pair("page-b", 2, "page-c", 3),
            ),
            SpreadAdapterItemMapper.map(items, canPair = { it != "insert" }),
        )
    }

    @Test
    fun adjacentEligibleItemsFromDifferentChaptersRemainSeparate() {
        val pages = listOf("chapter-a-page", "chapter-b-page", "chapter-b-next")
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("chapter-a-page", 0),
                SpreadAdapterItemMapper.Unit.Pair("chapter-b-page", 1, "chapter-b-next", 2),
            ),
            SpreadAdapterItemMapper.map(
                items = pages,
                canPairTogether = { first, second ->
                    first.substringBefore('-') == second.substringBefore('-')
                },
            ),
        )
    }

    @Test
    fun firstPageSingleAppliesAtTheSpecifiedChapterPageIndex() {
        val items = listOf("previous", "transition", "cover", "page-a", "page-b")
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("previous", 0),
                SpreadAdapterItemMapper.Unit.Single("transition", 1),
                SpreadAdapterItemMapper.Unit.Single("cover", 2),
                SpreadAdapterItemMapper.Unit.Pair("page-a", 3, "page-b", 4),
            ),
            SpreadAdapterItemMapper.map(
                items = items,
                isBoundary = { it == "transition" },
                firstPageSingle = true,
                firstPageIndex = 2,
            ),
        )
    }

    @Test
    fun invalidFirstPageIndexDoesNotDropItems() {
        val items = listOf("a", "b")
        assertEquals(
            listOf(SpreadAdapterItemMapper.Unit.Pair("a", 0, "b", 1)),
            SpreadAdapterItemMapper.map(items, firstPageSingle = true, firstPageIndex = 8),
        )
    }

    @Test
    fun consecutiveBoundaryItemsRemainIndividual() {
        val items = listOf("page-a", "transition-1", "transition-2", "page-b", "page-c")
        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("page-a", 0),
                SpreadAdapterItemMapper.Unit.Single("transition-1", 1),
                SpreadAdapterItemMapper.Unit.Single("transition-2", 2),
                SpreadAdapterItemMapper.Unit.Pair("page-b", 3, "page-c", 4),
            ),
            SpreadAdapterItemMapper.map(items, isBoundary = { it.startsWith("transition") }),
        )
    }
}
