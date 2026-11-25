# Split Scroll host integration contract

## Status

`WebtoonSplitScrollHostView` provides two independent `WebtoonFrame` / `WebtoonRecyclerView` pairs,
independent `WebtoonLayoutManager` instances, weighted column widths, and a full-screen boundary slot.
`WebtoonSplitScrollColumnAdapter` now projects page-only segment placements into either column while
retaining original source indices. `WebtoonPageHolder` can now size its progress/error UI and side margins
against its owning recycler instead of always using the legacy viewer recycler. These pieces remain
standalone: `WebtoonViewer` does not install the host, and the existing single-column reader is still active.

## Required host structure

A real Split Scroll host must own two independent column subtrees. Each subtree needs its own:

- `WebtoonRecyclerView` and `WebtoonLayoutManager`;
- adapter projection for that column's current segment;
- RecyclerView holder bind/recycle lifecycle;
- scroll listener and end-of-segment callback;
- pinch, double-tap, pan and fling gesture target;
- measured width and zoom state.

The full-width chapter transition is a separate host row spanning both columns. It is not a member of either column adapter. The barrier coordinator may release it only after both non-empty columns reach their segment end; an empty column is complete immediately.

## Existing holder constraints

Do not move or reparent a live `PagerPageHolder` or `WebtoonPageHolder`.

- `PagerPageHolder` starts its page/status collection during initialization and cancels it on detach. There is no attach-time restart path.
- `WebtoonPageHolder` starts loading from `bind(page)` and cancels/reset state from `recycle()`. It now accepts an optional owning `WebtoonRecyclerView`; the Split Scroll adapter passes its column recycler so parent-height and side-margin calculations use that column's dimensions.
- `WebtoonFrame` currently delegates all scale and fling gestures to its first child recycler. It cannot host both columns as-is.
- `WebtoonViewer.onScrolled()` currently selects the last end-visible item from one adapter. Two independent adapters must report through a shared selection/progress coordinator using original source indices.

## State and callback ownership

`WebtoonSplitScrollHostState` is a pure state model for the future host:

- width and zoom are stored independently for LEFT and RIGHT;
- scroll offsets are independent and are not synchronized;
- attaching/recreating a column issues a new generation token;
- detaching invalidates its token;
- delayed callbacks are accepted only when their token still matches the attached column.

The host must capture the binding token in each listener. It must not derive a source index from a child adapter position without mapping it through the active segment's `WebtoonSplitScrollPlan`.

## Required integration sequence

1. Connect the standalone `WebtoonSplitScrollHostView` to a dedicated viewer path without replacing the existing single-column frame.
2. Give each column its own RecyclerView, LayoutManager, `WebtoonSplitScrollColumnAdapter`, and holder lifecycle. The adapter accepts only `ReaderPage` placements and retains original indices.
3. Add a full-width transition row controlled by `WebtoonSplitScrollBarrierCoordinator`.
4. Map visible column items through `WebtoonSplitScrollSelection` to the original `WebtoonAdapter.items` index.
5. Route only accepted current-generation selection events to the existing activity page-selection/progress APIs.
6. Restore by resolving the persisted original page identity/index into the current segment and column; do not restore from a column-local adapter position.
7. Verify gestures, pinch zoom, long press, image loading, split-page insertion, chapter transitions, resume and rotation before enabling the mode.

## Non-goals for this stage

- No activation from the reader mode selector.
- No installation of the standalone host into `WebtoonViewer` yet.
- No active holder reparenting.
- No mutation/reordering of `WebtoonAdapter.items`.
- No synchronization of ordinary column scrolling.
- No change to the default Webtoon reader.
- No change to the stable `main` branch or Build #139 fallback.
