package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSpreadPlanTest {

    @Test
    fun `pairs adjacent portrait pages and preserves source indices`() {
        val items = listOf("a", "b", "c", "d")
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { false },
            isPortrait = { true },
        )

        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), plan.units.map { it.sourceIndices })
        assertTrue(plan.matchesSource(items))
        assertEquals("b", plan.sourceItemAt(0, 1))
    }

    @Test
    fun `landscape and unknown orientation pages remain standalone`() {
        val items = listOf("portrait-a", "landscape", "portrait-b", "unknown", "portrait-c")
        val orientations = mapOf("landscape" to false, "unknown" to null)
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { false },
            isPortrait = { orientations[it] ?: if (it == "unknown") null else true },
        )

        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3), listOf(4)),
            plan.units.map { it.sourceIndices },
        )
    }

    @Test
    fun `chapter boundaries and ineligible pages prevent cross-pairing`() {
        val items = listOf("p0", "p1", "transition", "insert", "p2", "p3")
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { it == "transition" },
            isPortrait = { true },
            canPair = { it != "insert" },
        )

        assertEquals(
            listOf(listOf(0, 1), listOf(2), listOf(3), listOf(4, 5)),
            plan.units.map { it.sourceIndices },
        )
        assertTrue(plan.units[2] is WebtoonSpreadPlan.Unit.Single)
        assertTrue(plan.matchesSource(items))
    }

    @Test
    fun `pages from different chapters never form a spread pair`() {
        val items = listOf("previous-last", "current-first", "current-second")
        val chapters = mapOf(
            "previous-last" to "previous",
            "current-first" to "current",
            "current-second" to "current",
        )
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { false },
            isPortrait = { true },
            canPair = { item -> chapters[item] == chapters[items[items.indexOf(item) + 1.coerceAtMost(items.lastIndex)]] },
        )

        assertEquals(
            listOf(listOf(0), listOf(1, 2)),
            plan.units.map { it.sourceIndices },
        )
        assertTrue(plan.matchesSource(items))
    }

    @Test
    fun `first page can remain alone without changing following pairs`() {
        val items = listOf("cover", "p1", "p2", "p3")
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { false },
            isPortrait = { true },
            firstPageSingle = true,
        )

        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), plan.units.map { it.sourceIndices })
    }

    @Test
    fun `first page alone targets current chapter page after leading transition items`() {
        val items = listOf("previous-page", "transition", "cover", "p1", "p2")
        val plan = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = { it == "transition" },
            isPortrait = { true },
            firstPageSingle = true,
            firstPageIndex = 2,
        )

        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)),
            plan.units.map { it.sourceIndices },
        )
        assertTrue(plan.matchesSource(items))
    }

    @Test
    fun `source validation rejects reordered or replaced entries`() {
        val original = listOf("one", "two")
        val plan = WebtoonSpreadPlan.create(original, { false }, { true })

        assertTrue(plan.matchesSource(original))
        assertFalse(plan.matchesSource(listOf("two", "one")))
        assertFalse(plan.matchesSource(listOf("one", "replacement")))
    }
}
