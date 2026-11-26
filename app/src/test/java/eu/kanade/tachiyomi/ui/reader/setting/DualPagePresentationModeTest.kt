package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.Assert.assertEquals
import org.junit.Test

class DualPagePresentationModeTest {

    @Test
    fun preferenceValuesMapToTheirPresentationModes() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(0))
        assertEquals(DualPagePresentationMode.SPLIT_SCROLL, DualPagePresentationMode.fromPreference(1))
        assertEquals(DualPagePresentationMode.NONE, DualPagePresentationMode.fromPreference(2))
    }

    @Test
    fun unknownPreferenceValuesFallBackToSpread() {
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(-1))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(3))
        assertEquals(DualPagePresentationMode.SPREAD, DualPagePresentationMode.fromPreference(Int.MAX_VALUE))
    }

    @Test
    fun selectingAnActivePresentationDisablesDualPageMode() {
        assertEquals(
            DualPagePresentationMode.NONE,
            DualPagePresentationMode.SPREAD.toggleTo(DualPagePresentationMode.SPREAD),
        )
        assertEquals(
            DualPagePresentationMode.NONE,
            DualPagePresentationMode.SPLIT_SCROLL.toggleTo(DualPagePresentationMode.SPLIT_SCROLL),
        )
    }

    @Test
    fun selectingAnotherPresentationSwitchesDirectly() {
        assertEquals(
            DualPagePresentationMode.SPLIT_SCROLL,
            DualPagePresentationMode.SPREAD.toggleTo(DualPagePresentationMode.SPLIT_SCROLL),
        )
        assertEquals(
            DualPagePresentationMode.SPREAD,
            DualPagePresentationMode.SPLIT_SCROLL.toggleTo(DualPagePresentationMode.SPREAD),
        )
        assertEquals(
            DualPagePresentationMode.SPREAD,
            DualPagePresentationMode.NONE.toggleTo(DualPagePresentationMode.SPREAD),
        )
    }

    @Test
    fun enumValuesPreservePersistedPreferenceNumbers() {
        assertEquals(0, DualPagePresentationMode.SPREAD.preferenceValue)
        assertEquals(1, DualPagePresentationMode.SPLIT_SCROLL.preferenceValue)
        assertEquals(2, DualPagePresentationMode.NONE.preferenceValue)
    }
}

    @Test
    fun selectingDisabledStateKeepsPresentationDisabled() {
        assertEquals(
            DualPagePresentationMode.NONE,
            DualPagePresentationMode.SPREAD.toggleTo(DualPagePresentationMode.NONE),
        )
        assertEquals(
            DualPagePresentationMode.NONE,
            DualPagePresentationMode.SPLIT_SCROLL.toggleTo(DualPagePresentationMode.NONE),
        )
        assertEquals(
            DualPagePresentationMode.NONE,
            DualPagePresentationMode.NONE.toggleTo(DualPagePresentationMode.NONE),
        )
    }

    @Test
    fun disabledStateCanEnableEitherPresentation() {
        assertEquals(
            DualPagePresentationMode.SPREAD,
            DualPagePresentationMode.NONE.toggleTo(DualPagePresentationMode.SPREAD),
        )
        assertEquals(
            DualPagePresentationMode.SPLIT_SCROLL,
            DualPagePresentationMode.NONE.toggleTo(DualPagePresentationMode.SPLIT_SCROLL),
        )
    }
