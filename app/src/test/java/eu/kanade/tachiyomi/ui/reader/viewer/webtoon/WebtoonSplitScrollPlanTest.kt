package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSplitScrollPlan.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebtoonSplitScrollPlanTest {

    @Test
    fun `ordinary items alternate within one segment and retain source indices`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("p0", "p1", "p2", "p3")) { false }

        assertEquals(1, plan.segments.size)
        assertEquals(listOf(0, 2), plan.segments.single().left.map { it.originalIndex })
        assertEquals(listOf(1, 3), plan.segments.single().right.map { it.originalIndex })
        assertEquals(listOf(0, 1, 2, 3), plan.inSourceOrder().map { it.originalIndex })
        assertEquals(Column.LEFT, plan.placementForSourceIndex(0)?.column)
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(1)?.column)
    }

    @Test
    fun `inverted placement starts on right and alternates toward left`() {
        val plan = WebtoonSplitScrollPlan.create(
            items = listOf("p0", "p1", "p2", "p3"),
            isBoundary = { false },
            firstColumn = Column.RIGHT,
        )

        assertEquals(listOf(1, 3), plan.segments.single().left.map { it.originalIndex })
        assertEquals(listOf(0, 2), plan.segments.single().right.map { it.originalIndex })
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(0)?.column)
        assertEquals(Column.LEFT, plan.placementForSourceIndex(1)?.column)
    }

    @Test
    fun `inverted placement restarts on right after chapter boundary`() {
        val items = listOf("before", "transition", "after")
        val plan = WebtoonSplitScrollPlan.create(
            items = items,
            isBoundary = { it == "transition" },
            firstColumn = Column.RIGHT,
        )

        assertEquals(Column.RIGHT, plan.placementForSourceIndex(0)?.column)
        assertEquals(Column.FULL_WIDTH, plan.placementForSourceIndex(1)?.column)
        assertEquals(Column.RIGHT, plan.placementForSourceIndex(2)?.column)
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }
    @Test
    fun `chapter transition closes segment and starts following segment at left`() {
        val items = listOf("before-a", "before-b", "transition", "after-a", "after-b")
        val plan = WebtoonSplitScrollPlan.create(items) { it == "transition" }

        assertEquals(2, plan.segments.size)
        assertEquals(listOf(0), plan.segments[0].left.map { it.originalIndex })
        assertEquals(listOf(1), plan.segments[0].right.map { it.originalIndex })
        assertEquals(2, plan.segments[0].boundaryAfter?.originalIndex)
        assertEquals(Column.FULL_WIDTH, plan.placementForSourceIndex(2)?.column)
        assertEquals(listOf(3), plan.segments[1].left.map { it.originalIndex })
        assertEquals(listOf(4), plan.segments[1].right.map { it.originalIndex })
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }

    @Test
    fun `leading and consecutive boundaries are preserved as empty segments`() {
        val items = listOf("t0", "page", "t1", "t2", "last")
        val plan = WebtoonSplitScrollPlan.create(items) { it.startsWith("t") }

        assertEquals(4, plan.segments.size)
        assertEquals(0, plan.segments[0].left.size)
        assertEquals(0, plan.segments[0].right.size)
        assertEquals(0, plan.segments[0].boundaryAfter?.originalIndex)
        assertEquals(listOf(1), plan.segments[1].left.map { it.originalIndex })
        assertEquals(2, plan.segments[1].boundaryAfter?.originalIndex)
        assertEquals(3, plan.segments[2].boundaryAfter?.originalIndex)
        assertEquals(listOf(4), plan.segments[3].left.map { it.originalIndex })
        assertEquals(items, plan.inSourceOrder().map { it.item })
    }

    @Test
    fun `resume source index resolves segment and independent column position`() {
        val items = listOf("before-a", "before-b", "transition", "after-a", "after-b", "after-c")
        val plan = WebtoonSplitScrollPlan.create(items) { it == "transition" }

        assertEquals(1, plan.segmentIndexForSourceIndex(3))
        assertEquals(Column.LEFT to 0, plan.columnPositionForSourceIndex(3))
        assertEquals(1, plan.segmentIndexForSourceIndex(4))
        assertEquals(Column.RIGHT to 0, plan.columnPositionForSourceIndex(4))
        assertEquals(1, plan.segmentIndexForSourceIndex(5))
        assertEquals(Column.LEFT to 1, plan.columnPositionForSourceIndex(5))
    }

    @Test
    fun `boundaries and invalid source indices have no page column position`() {
        val plan = WebtoonSplitScrollPlan.create(listOf("page", "transition", "next")) {
            it == "transition"
        }

        assertEquals(-1, plan.segmentIndexForSourceIndex(1))
        assertNull(plan.columnPositionForSourceIndex(1))
        assertEquals(-1, plan.segmentIndexForSourceIndex(-1))
        assertNull(plan.columnPositionForSourceIndex(-1))
        assertEquals(-1, plan.segmentIndexForSourceIndex(3))
        assertNull(plan.columnPositionForSourceIndex(3))
    }

    @Test
    fun `source snapshot validation rejects changed identities and sizes`() {
        data class Item(val id: String)

        val first = Item("same")
        val second = Item("other")
        val source = listOf(first, second)
        val plan = WebtoonSplitScrollPlan.create(source) { false }

        assertEquals(true, plan.matchesSource(source))
        assertEquals(false, plan.matchesSource(listOf(Item("same"), second)))
        assertEquals(false, plan.matchesSource(listOf(first)))
        assertEquals(false, plan.matchesSource(listOf(first, second, Item("extra"))))
    }

    @Test
    fun `empty input retains a trailing empty segment and invalid indices are safe`() {
        val plan = WebtoonSplitScrollPlan.create(emptyList<String>()) { false }

        assertEquals(0, plan.sourceSize)
        assertEquals(1, plan.segments.size)
        assertEquals(emptyList<Int>(), plan.inSourceOrder().map { it.originalIndex })
        assertNull(plan.placementForSourceIndex(-1))
        assertNull(plan.placementForSourceIndex(0))
    }
    @Test
    fun `source snapshot validation rejects duplicate and missing original indices`() {
        val first = "first"
        val second = "second"
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, Column.LEFT),
                    ),
                    right = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, Column.RIGHT),
                    ),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 2,
        )

        assertEquals(false, malformed.matchesSource(listOf(first, second)))
    }

    @Test
    fun `source snapshot validation rejects placements assigned to wrong columns`() {
        val first = "first"
        val second = "second"
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, Column.RIGHT),
                    ),
                    right = listOf(
                        WebtoonSplitScrollPlan.Placement(1, second, Column.LEFT),
                    ),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 2,
        )

        assertEquals(false, malformed.matchesSource(listOf(first, second)))
    }

    @Test
    fun `source snapshot validation rejects non full width chapter boundaries`() {
        val transition = "transition"
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = emptyList(),
                    right = emptyList(),
                    boundaryAfter = WebtoonSplitScrollPlan.Placement(0, transition, Column.LEFT),
                ),
                WebtoonSplitScrollPlan.Segment(
                    left = emptyList(),
                    right = emptyList(),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 1,
        )

        assertEquals(false, malformed.matchesSource(listOf(transition)))
    }

    @Test
    fun `source snapshot validation rejects pages placed after an unclosed segment`() {
        val first = "first"
        val second = "second"
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, Column.LEFT),
                    ),
                    right = emptyList(),
                    boundaryAfter = null,
                ),
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(1, second, Column.LEFT),
                    ),
                    right = emptyList(),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 2,
        )

        assertEquals(false, malformed.matchesSource(listOf(first, second)))
    }

    @Test
    fun `source snapshot validation rejects non alternating pages within a segment`() {
        val first = "first"
        val second = "second"
        val malformed = WebtoonSplitScrollPlan(
            segments = listOf(
                WebtoonSplitScrollPlan.Segment(
                    left = listOf(
                        WebtoonSplitScrollPlan.Placement(0, first, Column.LEFT),
                        WebtoonSplitScrollPlan.Placement(1, second, Column.LEFT),
                    ),
                    right = emptyList(),
                    boundaryAfter = null,
                ),
            ),
            sourceSize = 2,
        )

        assertEquals(false, malformed.matchesSource(listOf(first, second)))
    }

    @Test
    fun `source snapshot validation rejects a plan with no segments`() {
        val malformed = WebtoonSplitScrollPlan<String>(
            segments = emptyList(),
            sourceSize = 0,
        )

        assertEquals(false, malformed.matchesSource(emptyList()))
    }

}
