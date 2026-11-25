package eu.kanade.tachiyomi.ui.reader.setting

/**
 * User-selected dual-page presentation. This is deliberately separate from [ReadingMode]:
 * reading direction and scroll behavior remain controlled by the existing reader mode.
 *
 * Values are persisted as integers for compatibility with the existing preference key.
 */
enum class DualPagePresentationMode(val preferenceValue: Int) {
    SPREAD(0),
    SPLIT_SCROLL(1),
    ;

    companion object {
        /**
         * Converts the persisted preference value safely. Unknown values fall back to Spread,
         * matching the preference's historical default without rewriting stored data.
         */
        fun fromPreference(value: Int): DualPagePresentationMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SPREAD
    }
}
