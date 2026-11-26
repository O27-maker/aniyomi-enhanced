package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadAdapterProjectionTest {

    @Test
    fun snapshotAndSelectedAnchorAreDerivedWithoutChangingInput() {
        val source = listOf("p0", "p1", "transition", "p2", "p3")
        val original = source.toList()
        val projection = SpreadAdapterProjection.build(
            adapterItems = source,
            selectedAdapterIndex = 1,
            isBoundary = { it == "transition" },
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Pair("p0", 0, "p1", 1),
                SpreadAdapterItemMapper.Unit.Single("transition", 2),
                SpreadAdapterItemMapper.Unit.Pair("p2", 3, "p3", 4),
            ),
            projection.units,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(0, 1), projection.selectedAnchor)
        assertEquals(original, source)
    }

    @Test
    fun ineligibleItemsStaySingleAndSelectedAnchorUsesOriginalIndex() {
        val projection = SpreadAdapterProjection.build(
            adapterItems = listOf("p0", "insert", "p1", "p2"),
            selectedAdapterIndex = 1,
            canPair = { it != "insert" },
        )
        assertEquals(SpreadAdapterItemMapper.Unit.Single("insert", 1), projection.units[1])
        assertEquals(SpreadAdapterNavigationMap.Anchor(1, 1), projection.selectedAnchor)
    }

    @Test
    fun firstPageCanBeKeptAloneWithoutChangingFollowingPairingOrSourceIndices() {
        val source = listOf("cover", "p1", "p2", "p3")
        val projection = SpreadAdapterProjection.build(
            adapterItems = source,
            selectedAdapterIndex = 0,
            firstPageSingle = true,
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("cover", 0),
                SpreadAdapterItemMapper.Unit.Pair("p1", 1, "p2", 2),
                SpreadAdapterItemMapper.Unit.Single("p3", 3),
            ),
            projection.units,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(0, 0), projection.selectedAnchor)
        assertEquals(source, projection.units.flatMap { unit ->
            when (unit) {
                is SpreadAdapterItemMapper.Unit.Single -> listOf(unit.item)
                is SpreadAdapterItemMapper.Unit.Pair -> listOf(unit.first, unit.second)
            }
        })
    }

    @Test
    fun firstPageSingleDoesNotConsumeOrPairAcrossBoundary() {
        val projection = SpreadAdapterProjection.build(
            adapterItems = listOf("cover", "transition", "p1", "p2"),
            selectedAdapterIndex = 2,
            isBoundary = { it == "transition" },
            firstPageSingle = true,
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single("cover", 0),
                SpreadAdapterItemMapper.Unit.Single("transition", 1),
                SpreadAdapterItemMapper.Unit.Pair("p1", 2, "p2", 3),
            ),
            projection.units,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(2, 2), projection.selectedAnchor)
    }

    @Test
    fun readerProjectionKeepsLandscapeOrUnknownPagesStandalone() {
        val pageA = ReaderPage(0)
        val pageB = ReaderPage(1)
        val pageC = ReaderPage(2)
        val projection = SpreadAdapterProjection.buildReaderPages(
            adapterItems = listOf(pageA, pageB, pageC),
            selectedAdapterIndex = 1,
            isPortraitPage = { it !== pageB },
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single(pageA, 0),
                SpreadAdapterItemMapper.Unit.Single(pageB, 1),
                SpreadAdapterItemMapper.Unit.Single(pageC, 2),
            ),
            projection.units,
        )
    }

    @Test
    fun readerProjectionDoesNotPairAcrossPagesWithUnknownOrientation() {
        val pageA = ReaderPage(0)
        val pageB = ReaderPage(1)
        val pageC = ReaderPage(2)
        val pageD = ReaderPage(3)
        val projection = SpreadAdapterProjection.buildReaderPages(
            adapterItems = listOf(pageA, pageB, pageC, pageD),
            selectedAdapterIndex = 2,
            isPortraitPage = { page ->
                when (page) {
                    pageA, pageD -> true
                    pageB -> null
                    else -> false
                }
            },
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single(pageA, 0),
                SpreadAdapterItemMapper.Unit.Single(pageB, 1),
                SpreadAdapterItemMapper.Unit.Single(pageC, 2),
                SpreadAdapterItemMapper.Unit.Single(pageD, 3),
            ),
            projection.units,
        )
        assertEquals(SpreadAdapterNavigationMap.Anchor(2, 2), projection.selectedAnchor)
    }

    @Test
    fun readerProjectionPairsAdjacentPortraitPagesAndKeepsCoverAlone() {
        val cover = ReaderPage(0)
        val pageA = ReaderPage(1)
        val pageB = ReaderPage(2)
        val pageC = ReaderPage(3)
        val projection = SpreadAdapterProjection.buildReaderPages(
            adapterItems = listOf(cover, pageA, pageB, pageC),
            selectedAdapterIndex = 0,
            isPortraitPage = { true },
            firstPageSingle = true,
        )

        assertEquals(
            listOf(
                SpreadAdapterItemMapper.Unit.Single(cover, 0),
                SpreadAdapterItemMapper.Unit.Pair(pageA, 1, pageB, 2),
                SpreadAdapterItemMapper.Unit.Single(pageC, 3),
            ),
            projection.units,
        )
    }

    @Test
    fun projectionRejectsMutatedOrReplacedSourceItems() {
        val original = listOf(Any(), Any())
        val projection = SpreadAdapterProjection.build(
            adapterItems = original,
            selectedAdapterIndex = 0,
        )

        assertEquals(true, projection.matchesSource(original))
        assertEquals(false, projection.matchesSource(listOf(original[0], Any())))
        assertEquals(false, projection.matchesSource(listOf(original[0])))
        assertEquals(false, projection.matchesSource(listOf(Any(), original[1])))
    }

    @Test
    fun missingSelectedAdapterIndexProducesNullAnchor() {
        val projection = SpreadAdapterProjection.build(
            adapterItems = listOf("p0", "p1"),
            selectedAdapterIndex = 8,
        )
        assertNull(projection.selectedAnchor)
    }
}
