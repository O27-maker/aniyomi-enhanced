package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.Assert.assertEquals
import org.junit.Test

class DualPagePresentationModeTest {

    @Test
    fun preferenceValuesMapToTheirPresentationModes() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(0))
        assertEquals(DualPagePresentationMode.SPLIT_SCROLL, DualPagePresentationMode.fromPreference(1))
    }

    @Test
    fun unknownPreferenceValuesFallBackToSpread() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(-1))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(2))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(Int.MAX_VALUE))
    }

    @Test
    fun enumValuesPreservePersistedPreferenceNumbers() {
        assertEquals(0, DualPagePresentationMode.SPREAD.preferenceValue)
        assertEquals(1, DualPagePresentationMode.SPLIT_SCROLL.preferenceValue)
    }
}
