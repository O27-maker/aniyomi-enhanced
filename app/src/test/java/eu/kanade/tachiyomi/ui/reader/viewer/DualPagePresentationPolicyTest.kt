package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPagePresentationPolicyTest {

    @Test
    fun presentationDoesNotChangeHorizontalReadingNavigation() {
        val l2rSpread = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.L2R,
            DualPagePresentationPolicy.Presentation.SPREAD,
        )
        val r2lSplit = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.R2L,
            DualPagePresentationPolicy.Presentation.SPLIT_SCROLL,
        )

        assertEquals(DualPagePresentationPolicy.NavigationAxis.HORIZONTAL, l2rSpread.navigationAxis)
        assertEquals(DualPagePresentationPolicy.NavigationAxis.HORIZONTAL, r2lSplit.navigationAxis)
        assertTrue(l2rSpread.consumesEffectiveOrder)
        assertTrue(r2lSplit.consumesEffectiveOrder)
    }

    @Test
    fun verticalModeRetainsVerticalNavigationInBothPresentations() {
        listOf(
            DualPagePresentationPolicy.Presentation.SPREAD,
            DualPagePresentationPolicy.Presentation.SPLIT_SCROLL,
        ).forEach { presentation ->
            val policy = DualPagePresentationPolicy.resolve(
                DualPagePresentationPolicy.ReadingMode.VERTICAL,
                presentation,
            )
            assertEquals(DualPagePresentationPolicy.NavigationAxis.VERTICAL, policy.navigationAxis)
        }
    }

    @Test
    fun continuousModesRemainContinuousAndGapModePreservesGaps() {
        val strip = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.LONG_STRIP,
            DualPagePresentationPolicy.Presentation.SPLIT_SCROLL,
        )
        val stripWithGaps = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.LONG_STRIP_WITH_GAPS,
            DualPagePresentationPolicy.Presentation.SPREAD,
        )

        assertEquals(DualPagePresentationPolicy.NavigationAxis.CONTINUOUS, strip.navigationAxis)
        assertEquals(DualPagePresentationPolicy.NavigationAxis.CONTINUOUS, stripWithGaps.navigationAxis)
        assertFalse(strip.preserveInterPageGaps)
        assertTrue(stripWithGaps.preserveInterPageGaps)
    }

    @Test
    fun onlySplitScrollRequestsIndependentColumnScrolling() {
        val spread = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.L2R,
            DualPagePresentationPolicy.Presentation.SPREAD,
        )
        val split = DualPagePresentationPolicy.resolve(
            DualPagePresentationPolicy.ReadingMode.L2R,
            DualPagePresentationPolicy.Presentation.SPLIT_SCROLL,
        )

        assertFalse(spread.supportsIndependentColumnScroll)
        assertTrue(split.supportsIndependentColumnScroll)
    }
}
