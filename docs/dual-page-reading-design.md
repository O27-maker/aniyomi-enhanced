# Dual-page reading design (experimental)

## Compatibility principle

Dual-page is a presentation/layout option, not a replacement for Reading Mode. It must coexist with the selected reading mode and preserve the user's direction, navigation, chapter boundaries, page progress, resume position, loading/error states, and inserted-page behavior.

## Supported reading modes

Spread and Split Scroll must work with the existing reading modes where their semantics can be preserved:

- L2R: source progression is left-to-right.
- R2L: source progression is right-to-left.
- Vertical: preserve vertical progression and navigation semantics while presenting spread/column layout.
- Long Strip and Long Strip with gaps: preserve the strip mode's source order and gap behavior within each split column.
- Other existing modes: support without silently changing the active mode; if a mode cannot be safely adapted, keep the existing mode behavior and do not force a fallback mode.

## Spread

- Portrait pages can form a pair; landscape pages stay single.
- Optional first-page-single cover behavior.
- Special/inserted pages are not paired unless explicitly safe.
- Do not crop, stretch, or mutate page objects.
- Pair order and displayed side must follow the active reading direction.
- Page identity and progress mapping must remain tied to original source indices.

## Split Scroll

- Two independently vertically scrollable columns.
- Each column has independent width and zoom; width and zoom are distinct settings.
- No forced equal widths, no scroll synchronization, no cropping or stretching.
- Page placement must be mode- and direction-aware. The current alternating-index distributor is only a prototype and is not final.
- Preserve original indices so navigation, progress, resume, and chapter transitions can map to source pages.
- Keep existing page loading, image rendering, insert/split-page processing, and error states intact; avoid duplicating or bypassing the reader pipeline.

## Architecture guardrails

- Avoid adapting Pager holders directly into a new container until holder lifecycle, page loading, image rendering, transition/insert pages, progress, and preloading are understood.
- Prefer a small pure mapping layer for layout decisions, with UI integration using the existing reader page pipeline.
- Keep this work isolated to the experimental `feature/dual-page-reading` branch. Stable Aniyomi+ 0.2.1 / Build #139 remains untouched.

## Implementation status

- UI selector and preference scaffolding: committed.
- Spread grouping mapper: committed, not wired into rendering.
- Split Scroll column state model: committed, not wired into UI or persistence.
- Split Scroll distribution helper: committed prototype; does not yet account for active reading mode/direction.
- Distributor unit tests: committed, not executed.
- Actual Spread/Split Scroll rendering integration: not implemented.
- Android build and tests: not verified.


## Reader integration findings (follow-up investigation)

- `PagerViewerAdapter.setChapters()` constructs a source sequence containing previous/current/next chapter pages and chapter transitions, inserts preprocessed split pages, then reverses the complete sequence for `R2LPagerViewer`. The pager tracks selected items and page identities for progress and chapter preload.
- `PagerViewer` selection callbacks notify the activity, call page-holder selection, and preload chapters. Navigation and page-split operations are adapter-aware.
- `WebtoonViewer` is a separate `RecyclerView`-based implementation with a distinct adapter and page-holder lifecycle; it is not a subclass of `PagerViewer`. Its continuous and non-continuous variants share a distinct rendering pipeline.
- `WebtoonPageHolder` owns page loading/status collection, image processing, image rendering, error UI, and recycling. It is unsafe to bypass or duplicate this lifecycle when implementing Split Scroll.
- Because pager and webtoon are separate implementations, Dual-page must be integrated per viewer type or through a carefully scoped shared layout abstraction. It must not assume one universal holder/container can safely serve both.
- The adapter's page sequence already incorporates R2L reversal; layout mapping should consume the sequence in its effective display order and avoid applying a second direction reversal.

## Updated integration status

- Adapter/pager and Webtoon pipeline inspected at source level.
- Dual-page remains presentation-only; page-loading and progress pipelines must remain authoritative.
- No renderer integration has been committed yet. This investigation is source inspection only; Android compilation and tests remain unverified.


## Spread mapper validation cases added

A committed JUnit test suite now covers empty input, portrait pairing, odd page counts, first-page isolation, landscape breaks, `canPair` exclusions, and R2L mapping against an already-reversed display sequence. These tests are committed but have not been executed locally or in CI.


## Spread integration decision

The existing `PagerViewerAdapter` creates one `PagerPageHolder` per `ReaderPage`, and `PagerViewer` selection/progress/preloading is based on individual page identities. The current mapper produces logical groups only; it does not provide a safe multi-page holder or coordinated page-selection/navigation behavior. Therefore, wiring the mapper into `getCount()` or replacing adapter items with groups now would be premature. The next implementation should first define a spread holder/container that can host two existing page image holders while preserving each page's loading state, image/error callbacks, lifecycle/recycling, and original page identities. Spread navigation/progress should map to the active original page, and chapter transitions/InsertPage must remain standalone unless explicitly handled.


## Spread navigation mapping prototype

Added `SpreadNavigationMap` as a pure helper that maps logical spreads to effective page-index ranges and maps an effective page index back to its spread. It consumes mapper groups in the order supplied and does not reverse R2L sequences. This helper is groundwork only: it is not connected to ViewPager selection, progress, or resume handling. Tests cover page-index mapping, invalid indices, and an already-reversed R2L sequence. Tests remain unexecuted.



## ViewPager selection, progress, and resume findings

The existing `PagerViewer` treats adapter items as the navigation unit: `moveToPage(ReaderPage)` resolves the page object in `adapter.items` and selects that item position; page changes call `onReaderPageSelected`, which notifies the Activity/ViewModel. `ReaderViewModel.onPageSelected` saves progress using the selected page's underlying index and updates the active chapter. Therefore, a spread integration cannot simply collapse two `ReaderPage` items into one without explicitly defining which underlying page becomes the active/progress page and how `moveToPage` maps either member back to its spread. Current reader progress is page-based, not spread-based. A safe initial policy is: the spread's direction-leading page is the navigation anchor, but jumps/resume to either member select the containing spread and retain the requested member as the active page when possible. This needs implementation and tests before wiring.

`PagerViewer` selection also triggers holder selection/panning, preload checks, chapter transition behavior, and adjacent chapter loading. Any spread holder must not suppress these calls. `InsertPage` is explicitly excluded from progress updates by the ViewModel, so it must remain excluded after integration. Chapter transition objects are adapter items and must remain standalone display items.

## Spread integration readiness checkpoint

Source inspection is complete enough to establish the key integration contracts, but not enough to safely build the actual renderer. The missing critical detail is how the `ViewPagerAdapter` and `DirectionalViewPager` instantiate/recycle child views and how the image holder gesture/pan state should be delegated for two children. Before a renderer implementation, inspect the base adapter/pager lifecycle and determine whether a nested two-child holder can safely reuse `PagerPageHolder` or whether an alternative image-renderer wrapper is needed. Do not change adapter item counts yet.
