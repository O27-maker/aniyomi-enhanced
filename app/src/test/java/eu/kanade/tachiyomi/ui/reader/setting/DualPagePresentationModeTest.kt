package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DualPagePresentationModeTest {

    @Test
    fun `preference values map to their presentation modes`() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(0))
        assertEquals(DualPagePresentationMode.SPLIT_SCROLL, DualPagePresentationMode.fromPreference(1))
    }

    @Test
    fun `unknown preference values fall back to spread`() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(-1))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(2))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(Int.MAX_VALUE))
    }

    @Test
    fun `enum values preserve persisted preference numbers`() {
        assertEquals(0, DualPagePresentationMode.SPREAD.preferenceValue)
        assertEquals(1, DualPagePresentationMode.SPLIT_SCROLL.preferenceValue)
    }
}
