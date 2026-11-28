# Stage 30 — typed presentation preference

Implemented on `feature/dual-page-reading`.

- Added `DualPagePresentationMode` with `SPREAD(0)` and `SPLIT_SCROLL(1)`.
- Added a safe conversion from the existing integer preference value.
- Unknown values fall back to Spread without modifying the stored preference.
- Added JUnit 4 tests for both valid values, unknown values, and stable persisted numbers.
- Kept the existing `pref_dual_page_mode` storage contract unchanged.

The selector and activity still exchange integer values at this stage. The typed enum is the conversion boundary for the next wiring step; it is not yet connected to PagerConfig or WebtoonConfig.

Verification: source files and test conventions were inspected. Tests have not been executed and no Android build has been run.
