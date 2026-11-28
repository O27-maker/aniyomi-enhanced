package eu.kanade.tachiyomi.ui.reader.viewer.pager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerSelectionDirectionTest {

    @Test
    fun ltrPageNumbersIncreaseInForwardDirection() {
        assertTrue(PagerSelectionDirection.isForwardBetweenPages(3, 4, false, false))
        assertFalse(PagerSelectionDirection.isForwardBetweenPages(4, 3, false, false))
    }

    @Test
    fun rtlPageNumbersDecreaseInForwardDirection() {
        assertTrue(PagerSelectionDirection.isForwardBetweenPages(4, 3, true, false))
        assertFalse(PagerSelectionDirection.isForwardBetweenPages(3, 4, true, false))
    }

    @Test
    fun sameNumberInsertPageIsForwardButOrdinaryPageIsNot() {
        assertTrue(PagerSelectionDirection.isForwardBetweenPages(5, 5, false, true))
        assertFalse(PagerSelectionDirection.isForwardBetweenPages(5, 5, false, false))
        assertTrue(PagerSelectionDirection.isForwardBetweenPages(5, 5, true, true))
    }

    @Test
    fun previousTransitionIsForwardWhenReturningToItsSourceChapter() {
        assertTrue(PagerSelectionDirection.isForwardFromTransition(true, true, false))
        assertFalse(PagerSelectionDirection.isForwardFromTransition(true, false, true))
    }

    @Test
    fun nextTransitionIsForwardWhenEnteringItsDestinationChapter() {
        assertTrue(PagerSelectionDirection.isForwardFromTransition(false, false, true))
        assertFalse(PagerSelectionDirection.isForwardFromTransition(false, true, false))
    }

    @Test
    fun missingDestinationIsNeverMistakenForForwardEntry() {
        assertFalse(PagerSelectionDirection.isForwardFromTransition(false, false, false))
    }
}
