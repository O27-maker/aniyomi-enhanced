package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Index mapping for a logical Spread display sequence.
 *
 * Page indices refer to the effective display sequence passed to
 * [SpreadPageMapper.map], which may already be reversed for R2L. This class
 * never mutates page order or original page objects.
 */
internal object SpreadNavigationMap {

    internal data class Spread(
        val displayIndex: Int,
        val pageIndices: List<Int>,
    )

    fun <T> fromGroups(groups: List<SpreadPageMapper.Group<T>>): List<Spread> {
        var pageIndex = 0
        return groups.mapIndexed { spreadIndex, group ->
            val pageIndices = when (group) {
                is SpreadPageMapper.Group.Single -> listOf(pageIndex++)
                is SpreadPageMapper.Group.Pair -> listOf(pageIndex++, pageIndex++)
            }
            Spread(spreadIndex, pageIndices)
        }
    }

    /** Returns the display spread containing the given effective page index. */
    fun spreadIndexForPage(groups: List<SpreadPageMapper.Group<*>>, pageIndex: Int): Int {
        if (pageIndex < 0) return -1
        var currentPageIndex = 0
        groups.forEachIndexed { spreadIndex, group ->
            val count = when (group) {
                is SpreadPageMapper.Group.Single -> 1
                is SpreadPageMapper.Group.Pair -> 2
            }
            if (pageIndex in currentPageIndex until (currentPageIndex + count)) return spreadIndex
            currentPageIndex += count
        }
        return -1
    }

    /** Returns effective page indices belonging to a spread, or empty if invalid. */
    fun pageIndicesForSpread(groups: List<SpreadPageMapper.Group<*>>, spreadIndex: Int): List<Int> {
        if (spreadIndex !in groups.indices) return emptyList()
        var pageIndex = 0
        groups.forEachIndexed { index, group ->
            val count = when (group) {
                is SpreadPageMapper.Group.Single -> 1
                is SpreadPageMapper.Group.Pair -> 2
            }
            if (index == spreadIndex) return (pageIndex until pageIndex + count).toList()
            pageIndex += count
        }
        return emptyList()
    }
}
