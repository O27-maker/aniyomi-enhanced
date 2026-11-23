package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Index mapping for a logical Spread display sequence.
 *
 * Page indices always refer to the sequence passed to [SpreadPageMapper.map]
 * (the effective display order, which may already be reversed for R2L).
 * This helper deliberately does not alter page order or reader progress.
 */
internal object SpreadNavigationMap {

    internal data class Spread(
        val displayIndex: Int,
        val pageIndices: List<Int>,
    )

    fun <T> fromGroups(groups: List<SpreadPageMapper.Group<T>>): List<Spread> =
        groups.mapIndexed { spreadIndex, group ->
            val pageIndices = when (group) {
                is SpreadPageMapper.Group.Single -> listOf(spreadIndexPageIndex(groups, group))
                is SpreadPageMapper.Group.Pair -> listOf(
                    spreadIndexPageIndex(groups, group.first),
                    spreadIndexPageIndex(groups, group.second),
                )
            }
            Spread(spreadIndex, pageIndices)
        }

    private fun <T> spreadIndexPageIndex(groups: List<SpreadPageMapper.Group<T>>, target: Any): Int {
        var pageIndex = 0
        for (group in groups) {
            when (group) {
                is SpreadPageMapper.Group.Single -> {
                    if (group.page === target || group.page == target) return pageIndex
                    pageIndex++
                }
                is SpreadPageMapper.Group.Pair -> {
                    if (group.first === target || group.first == target) return pageIndex
                    pageIndex++
                    if (group.second === target || group.second == target) return pageIndex
                    pageIndex++
                }
            }
        }
        return -1
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

    /** Returns the effective page indices belonging to a spread. */
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
