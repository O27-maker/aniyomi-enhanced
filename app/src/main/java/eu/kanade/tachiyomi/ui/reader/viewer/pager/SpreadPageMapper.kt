package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Maps chapter pages to logical display groups for the experimental Spread reader.
 *
 * This mapper does not mutate chapter pages or change their original indices.
 * Callers can provide orientation and eligibility information after page
 * dimensions and page types are known.
 */
internal object SpreadPageMapper {

    internal sealed interface Group<out T> {
        data class Single<T>(val page: T) : Group<T>
        data class Pair<T>(val first: T, val second: T) : Group<T>
    }

    /**
     * Groups pages in order. Landscape pages and pages that are not eligible for
     * pairing are always displayed alone and break a portrait pair.
     *
     * When [firstPageSingle] is true, the first page is displayed alone,
     * commonly used for a cover.
     *
     * [isLandscape] should return true only when dimensions are known and width
     * is greater than height. Unknown dimensions should be treated as portrait.
     * [canPair] lets callers exclude special pages (such as inserted split
     * pages) without changing their position or identity.
     */
    fun <T> map(
        pages: List<T>,
        firstPageSingle: Boolean = false,
        isLandscape: (T) -> Boolean = { false },
        canPair: (T) -> Boolean = { true },
    ): List<Group<T>> {
        if (pages.isEmpty()) return emptyList()

        val groups = mutableListOf<Group<T>>()
        var index = 0

        if (firstPageSingle) {
            groups += Group.Single(pages[index])
            index++
        }

        while (index < pages.size) {
            val page = pages[index]
            if (isLandscape(page) || !canPair(page)) {
                groups += Group.Single(page)
                index++
                continue
            }

            val next = pages.getOrNull(index + 1)
            if (next != null && !isLandscape(next) && canPair(next)) {
                groups += Group.Pair(page, next)
                index += 2
            } else {
                groups += Group.Single(page)
                index++
            }
        }

        return groups
    }
}
