package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Builds display units from an already assembled adapter sequence without mutating
 * that sequence. Boundary items (chapter transitions, inserted pages, or other
 * special adapter entries) remain single units and prevent pairing across them.
 *
 * The caller supplies eligibility and boundary policy so this mapping stays
 * independent of reader item types and rendering lifecycle.
 */
internal object SpreadAdapterItemMapper {

    internal sealed interface Unit<out T> {
        data class Single<T>(val item: T, val adapterIndex: Int) : Unit<T>
        data class Pair<T>(
            val first: T,
            val firstAdapterIndex: Int,
            val second: T,
            val secondAdapterIndex: Int,
        ) : Unit<T>
    }

    fun <T> map(
        items: List<T>,
        isBoundary: (T) -> Boolean = { false },
        canPair: (T) -> Boolean = { true },
        firstPageSingle: Boolean = false,
    ): List<Unit<T>> {
        val result = mutableListOf<Unit<T>>()
        var index = 0
        if (firstPageSingle && items.isNotEmpty()) {
            result += Unit.Single(items.first(), 0)
            index = 1
        }
        while (index < items.size) {
            val current = items[index]
            if (isBoundary(current) || !canPair(current)) {
                result += Unit.Single(current, index)
                index++
                continue
            }

            val nextIndex = index + 1
            val next = items.getOrNull(nextIndex)
            if (next != null && !isBoundary(next) && canPair(next)) {
                result += Unit.Pair(current, index, next, nextIndex)
                index += 2
            } else {
                result += Unit.Single(current, index)
                index++
            }
        }
        return result
    }
}
