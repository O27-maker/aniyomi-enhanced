package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Pure page-selection direction rules used by pager presentations.
 *
 * Direction is expressed in reading order, not by the numeric adapter position:
 * R2L reverses page-number progression, and chapter transitions identify their
 * source and destination chapters explicitly.
 */
internal object PagerSelectionDirection {

    fun isForwardBetweenPages(
        currentPageNumber: Int,
        nextPageNumber: Int,
        isRightToLeft: Boolean,
        nextIsInsertPage: Boolean,
    ): Boolean {
        if (currentPageNumber == nextPageNumber) return nextIsInsertPage
        return if (isRightToLeft) {
            nextPageNumber < currentPageNumber
        } else {
            nextPageNumber > currentPageNumber
        }
    }

    fun isForwardFromTransition(
        isPreviousTransition: Boolean,
        pageMatchesFromChapter: Boolean,
        pageMatchesToChapter: Boolean,
    ): Boolean = if (isPreviousTransition) pageMatchesFromChapter else pageMatchesToChapter
}
