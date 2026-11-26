package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class WebtoonSplitScrollColumnWidthsTest {

    @Test
    fun defaultSplitIsEven() {
        assertEquals(50, WebtoonSplitScrollColumnWidths.normalize(50))
        assertEquals(50f to 50f, WebtoonSplitScrollColumnWidths.weights(50))
    }

    @Test
    fun widthIsClampedToKeepBothColumnsUsable() {
        assertEquals(20, WebtoonSplitScrollColumnWidths.normalize(Int.MIN_VALUE))
        assertEquals(80, WebtoonSplitScrollColumnWidths.normalize(Int.MAX_VALUE))
        assertEquals(20f to 80f, WebtoonSplitScrollColumnWidths.weights(0))
        assertEquals(80f to 20f, WebtoonSplitScrollColumnWidths.weights(100))
    }

    @Test
    fun weightsAlwaysPreserveFullAvailableWidth() {
        (-100..200).forEach { value ->
            val (left, right) = WebtoonSplitScrollColumnWidths.weights(value)
            assertEquals(100f, left + right, 0f)
            assert(left in 20f..80f)
            assert(right in 20f..80f)
        }
    }
}
