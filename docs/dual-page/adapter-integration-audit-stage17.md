# Dual-page reader integration audit — Stage 17

Branch: `feature/dual-page-reading`

## Confirmed adapter behavior

Audited `PagerViewerAdapter` and `PagerViewer` from this branch.

- `items` is the effective reader sequence, including previous/current/next chapter pages and transition items.
- R2L reverses the assembled effective sequence in `setChapters()`; spread pairing must not reverse it a second time.
- Adapter views are created per item: `ReaderPage` becomes `PagerPageHolder`, while `ChapterTransition` becomes `PagerTransitionHolder`.
- `getItemPosition()` resolves a holder through its `PositionableView.item` and the current sequence.
- Split pages are inserted into the sequence via `onPageSplit()`; page splitting has direction-specific insertion rules and cycle guards.
- Viewer selection and page progress are driven by the original adapter item/page, not a spread index.
- `PagerViewer.getPageHolder()` currently searches direct ViewPager children for a `PagerPageHolder`. A wrapper container would make child holders invisible to that lookup.
- The adapter's `createView()` currently has no spread-aware branch. The existing `SpreadFrameLayout` is therefore still a standalone layout primitive, not active reader UI.

## Integration constraints

A spread cannot simply replace two adapter items with one without redesigning selection, progress, and ViewPager item-position handling. It also cannot wrap holders without addressing direct-child holder discovery and page-holder callbacks.

The next integration must preserve the original page object as the active/selected identity, map spread positions to effective adapter items, and keep chapter-transition items standalone. `InsertPage` items must remain in the effective sequence and must not be paired across special-page boundaries. The spread view should own only layout; original holders remain responsible for image rendering and state.

## Safe next step

Implement a separate, testable mapping layer between adapter positions and display slots (single item or a pair) over the already assembled effective sequence. It should not mutate `PagerViewerAdapter.items`. Add tests for L2R/R2L, odd page counts, split/insert pages, transition boundaries, and jumps/resume to either member. Only after mapping is validated should the adapter/view integration be attempted.

## Verification status

Source audit only. Android build and tests have not been run.