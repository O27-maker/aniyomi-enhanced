package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadLayoutGeometryTest {
    @Test
    fun singlePageOccupiesFullWidth() {
        assertEquals(SpreadLayoutGeometry.Slot(0f, 1f), SpreadLayoutGeometry.single())
    }

    @Test
    fun l2rPairPlacesFirstPageOnLeft() {
        assertEquals(
            listOf(SpreadLayoutGeometry.Slot(0f, 0.5f), SpreadLayoutGeometry.Slot(0.5f, 1f)),
            SpreadLayoutGeometry.pair(isR2L = false),
        )
    }

    @Test
    fun r2lPairPlacesFirstPageOnRight() {
        assertEquals(
            listOf(SpreadLayoutGeometry.Slot(0.5f, 1f), SpreadLayoutGeometry.Slot(0f, 0.5f)),
            SpreadLayoutGeometry.pair(isR2L = true),
        )
    }
}
