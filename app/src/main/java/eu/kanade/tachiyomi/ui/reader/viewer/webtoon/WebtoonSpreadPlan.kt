package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Source-preserving Spread projection for continuous readers.
 *
 * The plan groups only adjacent eligible portrait pages and never pairs across a
 * chapter transition or special/ineligible page. It does not own or reorder the
 * authoritative WebtoonAdapter.items sequence.
 */
internal data class WebtoonSpreadPlan<T>(
    val units: List<Unit<T>>,
    val sourceSize: Int,
) {
    internal sealed interface Unit<out T> {
        val sourceIndices: List<Int>

        data class Single<T>(
            val sourceIndex: Int,
            val item: T,
        ) : Unit<T> {
            override val sourceIndices: List<Int> = listOf(sourceIndex)
        }

        data class Pair<T>(
            val firstSourceIndex: Int,
            val first: T,
            val secondSourceIndex: Int,
            val second: T,
        ) : Unit<T> {
            override val sourceIndices: List<Int> = listOf(firstSourceIndex, secondSourceIndex)
        }

        data class Boundary<T>(
            val sourceIndex: Int,
            val item: T,
        ) : Unit<T> {
            override val sourceIndices: List<Int> = listOf(sourceIndex)
        }
    }

    fun unitIndexForSourceIndex(sourceIndex: Int): Int =
        units.indexOfFirst { sourceIndex in it.sourceIndices }

    fun sourceItemAt(unitIndex: Int, preferredSourceIndex: Int? = null): T? {
        val unit = units.getOrNull(unitIndex) ?: return null
        return when (unit) {
            is Unit.Single -> unit.item
            is Unit.Pair -> when (preferredSourceIndex) {
                unit.secondSourceIndex -> unit.second
                else -> unit.first
            }
            is Unit.Boundary -> unit.item
        }
    }

    /** Validate exact item identity and complete, ordered source coverage. */
    fun matchesSource(items: List<T>): Boolean {
        if (items.size != sourceSize || units.isEmpty() != items.isEmpty()) return false
        var nextIndex = 0
        for (unit in units) {
            when (unit) {
                is Unit.Single -> {
                    if (unit.sourceIndex != nextIndex || items.getOrNull(nextIndex) !== unit.item) return false
                    nextIndex++
                }
                is Unit.Pair -> {
                    if (unit.firstSourceIndex != nextIndex || unit.secondSourceIndex != nextIndex + 1) return false
                    if (items.getOrNull(nextIndex) !== unit.first || items.getOrNull(nextIndex + 1) !== unit.second) return false
                    nextIndex += 2
                }
                is Unit.Boundary -> {
                    if (unit.sourceIndex != nextIndex || items.getOrNull(nextIndex) !== unit.item) return false
                    nextIndex++
                }
            }
        }
        return nextIndex == items.size
    }

    companion object {
        fun <T> create(
            items: List<T>,
            isBoundary: (T) -> Boolean,
            isPortrait: (T) -> Boolean?,
            canPair: (T) -> Boolean = { true },
            firstPageSingle: Boolean = false,
            firstPageIndex: Int = 0,
        ): WebtoonSpreadPlan<T> {
            val units = mutableListOf<Unit<T>>()
            var index = 0
            while (index < items.size) {
                val item = items[index]
                if (isBoundary(item)) {
                    units += Unit.Boundary(index, item)
                    index++
                    continue
                }
                if (firstPageSingle && index == firstPageIndex) {
                    units += Unit.Single(index, item)
                    index++
                    continue
                }
                val next = items.getOrNull(index + 1)
                val canPairCurrent = canPair(item) && isPortrait(item) == true
                val canPairNext = next != null && !isBoundary(next) && canPair(next) && isPortrait(next) == true
                if (canPairCurrent && canPairNext) {
                    units += Unit.Pair(index, item, index + 1, next!!)
                    index += 2
                } else {
                    units += Unit.Single(index, item)
                    index++
                }
            }
            return WebtoonSpreadPlan(units, items.size)
        }
    }
}
