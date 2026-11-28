# Dual-page selector-to-renderer wiring audit

Status: source inspection on `feature/dual-page-reading`.

## Existing UI and persistence

The feature selector is already present:
- `BottomReaderBar` opens `DualPageSelectDialog`.
- The dialog offers Spread and Split Scroll.
- `ReaderActivity` observes `ReaderPreferences.dualPageMode()` and writes changes back through the preference.
- The preference is stored as integer key `pref_dual_page_mode`, default `0`.

This confirms the user's two-mode popup and persisted selection are represented in the current branch.

## Missing renderer connection

The current Pager and Webtoon configuration classes still observe the older preferences:
- Pager: `dualPageSplitPaged()`, `dualPageInvertPaged()`, and rotate-to-fit preferences.
- Webtoon: `dualPageSplitWebtoon()`, `dualPageInvertWebtoon()`, and rotate-to-fit preferences.

Neither configuration path currently reads `dualPageMode()` or maps its value to a live Spread/Split Scroll renderer. The current image-processing `dualPageSplit` booleans are legacy split-wide-image behavior; they are not the new two-mode presentation selector.

Therefore the selector currently persists a choice but does not, by itself, activate the new presentation mode. Do not describe the feature as working in the reader yet.

## Required wiring sequence

1. Define a typed preference model for the two presentation modes instead of scattering raw integer values. Keep the existing persisted key and default for compatibility.
2. Add a single conversion boundary from the stored value to the typed mode, with a safe fallback for unknown values.
3. Pass the typed mode into the viewer presentation layer without modifying the existing Reading Mode preference.
4. Implement Pager and Webtoon integrations separately, respecting their distinct holder lifecycles.
5. Keep legacy split-wide-image and rotate-to-fit preferences independent unless an explicit migration decision is made.
6. Add preference conversion tests and viewer integration tests before enabling either mode by default.

## Compatibility warning

Do not repurpose `dualPageSplitPaged()` or `dualPageSplitWebtoon()` as the new Spread/Split Scroll selector. They currently control legacy image processing in the page holders. Conflating these preferences could change existing reader behavior and break compatibility for users upgrading from stable builds.
