# Dual-page reading-mode compatibility matrix

Status: integration design for `feature/dual-page-reading`. This is a compatibility contract, not a claim that the feature is already wired into every viewer.

## Existing reader pipelines

The reader has two distinct implementations:
- Pager pipeline: L2R, R2L, and vertical pager viewers share `PagerViewer` and `PagerViewerAdapter`.
- Continuous pipeline: `WebtoonViewer` uses a RecyclerView and its own adapter/layout manager.

The dual-page feature must not assume these pipelines share holders, selection callbacks, scrolling, or lifecycle behavior.

## Required behavior by reading mode

| Existing reading mode | Spread presentation | Split Scroll presentation |
|---|---|---|
| L2R | Pair eligible pages in the effective L2R sequence; navigate forward/backward using existing pager semantics | Preserve L2R page order; distribute pages without changing source identity |
| R2L | Consume the adapter's already-reversed sequence; do not reverse a second time | Consume the already-reversed effective sequence; no second reversal |
| Vertical | Keep vertical navigation semantics; spread is a presentation grouping only and must not turn navigation horizontal | Preserve vertical progression; columns scroll independently and retain effective page order |
| Long strip | Keep continuous page progression; pairing/grouping must not replace Webtoon adapter identity | Keep continuous progression within the dual-column presentation; each column has independent scroll state |
| Long strip with gaps | Preserve existing spacing and page boundaries | Preserve spacing per page and keep boundary items full-width |

## Shared invariants

1. Reading mode remains the source of navigation semantics. Spread/Split Scroll is a separate presentation choice.
2. Do not change page/chapter identity, effective adapter order, page numbers, resume target, or progress persistence.
3. Chapter transitions and inserted/special pages are never paired with ordinary pages.
4. Full-width boundary placement is allowed in Split Scroll; the caller must explicitly classify boundary items.
5. No image is cropped, stretched, or silently omitted to fit a spread or column.
6. Zoom and pan remain page-level. Split Scroll column width and scroll position remain independent; no forced synchronized scrolling.
7. Landscape pages remain standalone unless the existing reader's verified behavior explicitly supports another treatment.
8. Existing gesture/tap regions must retain their meaning for the selected reading mode.
9. The reader must fall back to its original Pager/Webtoon implementation if a dual-layout host cannot safely represent an item.

## Integration sequencing

### Pager modes
Integrate only after a host can retain the exact active `ReaderPage` while displaying one or two page holders. Do not wrap/reparent live `PagerPageHolder` instances until their detach/attach loader and status-flow lifecycle is verified. The ViewPager's adapter positions remain effective adapter positions; display-unit positions must be a separate mapping.

### Continuous modes
Do not force the Webtoon adapter through Pager-specific mapping. A dedicated continuous dual-column host must retain the Webtoon adapter's item identity, chapter transitions, visible-page selection, and preload callbacks. It must support independent column scrolling without changing the underlying logical sequence.

## Acceptance tests before activation

- Run the same source sequence under L2R and its reversed R2L effective sequence; verify no second reversal.
- Exercise vertical pager mode and verify next/previous and tap navigation remain vertical.
- Exercise long strip and long strip with gaps; verify continuous scroll, spacing, visible-page selection, and chapter preload.
- Exercise chapter transitions, split/insert pages, landscape pages, and special pages.
- Verify page-level zoom/pan, independent column widths, independent scroll offsets, resume, and progress.
- Verify switching presentation mode does not alter stored reading mode or resume position.
- Build and device-test on Android before exposing the feature as stable.

## Current implementation status

The pure Spread mapping and Split Scroll distribution helpers exist on the experimental branch. The compatibility matrix above is the contract for future integration. No live Pager or Webtoon host has been replaced, and no Android build/device test has been run.
