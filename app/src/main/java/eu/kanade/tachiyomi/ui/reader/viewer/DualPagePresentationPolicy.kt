package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * Separates the reader's navigation mode from its optional dual-page presentation.
 *
 * This policy is intentionally pure: it does not select a viewer, change preferences,
 * or alter page order. The existing reader mode remains authoritative for navigation.
 */
internal object DualPagePresentationPolicy {

    enum class ReadingMode {
        L2R,
        R2L,
        VERTICAL,
        LONG_STRIP,
        LONG_STRIP_WITH_GAPS,
    }

    enum class Presentation {
        SINGLE,
        SPREAD,
        SPLIT_SCROLL,
    }

    enum class NavigationAxis {
        HORIZONTAL,
        VERTICAL,
        CONTINUOUS,
    }

    data class Policy(
        val navigationAxis: NavigationAxis,
        val consumesEffectiveOrder: Boolean = true,
        val supportsIndependentColumnScroll: Boolean,
        val preserveInterPageGaps: Boolean,
    )

    fun resolve(readingMode: ReadingMode, presentation: Presentation): Policy {
        val axis = when (readingMode) {
            ReadingMode.L2R, ReadingMode.R2L -> NavigationAxis.HORIZONTAL
            ReadingMode.VERTICAL -> NavigationAxis.VERTICAL
            ReadingMode.LONG_STRIP, ReadingMode.LONG_STRIP_WITH_GAPS -> NavigationAxis.CONTINUOUS
        }

        return Policy(
            navigationAxis = axis,
            supportsIndependentColumnScroll = presentation == Presentation.SPLIT_SCROLL,
            preserveInterPageGaps = readingMode == ReadingMode.LONG_STRIP_WITH_GAPS,
        )
    }
}
