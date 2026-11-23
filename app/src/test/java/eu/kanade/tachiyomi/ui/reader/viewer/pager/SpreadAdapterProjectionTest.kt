package eu.kanade.tachiyomi.ui.reader.viewer.pager

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
    fun missingSelectedAdapterIndexProducesNullAnchor() {
        val projection = SpreadAdapterProjection.build(
            adapterItems = listOf("p0", "p1"),
            selectedAdapterIndex = 8,
        )
        assertNull(projection.selectedAnchor)
    }
}
