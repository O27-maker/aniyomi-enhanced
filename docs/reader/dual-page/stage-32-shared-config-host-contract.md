# Stage 32 — shared presentation configuration and host contract

Branch: `feature/dual-page-reading`

## Shared configuration
The selected `DualPagePresentationMode` now lives in `ViewerConfig`, the common base for Pager and Webtoon configuration. The persisted integer preference is converted once at this boundary. Both concrete configs inherit:
- `dualPagePresentationMode`
- `dualPagePresentationModeChangedListener`

This avoids duplicate preference observers and keeps the old `dualPageSplitPaged` / `dualPageSplitWebtoon` image-processing preferences separate.

## Pager host constraints
- The authoritative sequence remains `PagerViewerAdapter.items`; it includes pages, inserted pages and chapter transitions.
- Presentation must be a derived display projection, never a destructive rewrite of adapter items.
- Selection, progress, resume and chapter preload must continue to use original adapter positions and page identities.
- `PagerViewer.getPageHolder()` searches direct ViewPager children. A host must not reparent existing `PagerPageHolder` instances without redesigning holder lookup and lifecycle.
- Pager page holders start loading during initialization and cancel work on detach; there is no proven attach-time restart path. Avoid detach/reparent-based pairing.

## Webtoon host constraints
- `WebtoonAdapter.items` is the authoritative continuous sequence and includes chapter transitions.
- The adapter currently creates one `WebtoonPageHolder` per page item and binds/recycles through RecyclerView.
- Split Scroll needs a deliberate multi-column layout/holder strategy. Alternating adapter positions alone is insufficient: transitions must remain full-width, original positions must remain addressable, and each column needs independent scroll/zoom state.
- The existing `WebtoonRecyclerView` owns global zoom and vertical scrolling. It is not yet an independent-column implementation.

## Mode behavior contract
- `SINGLE`: existing rendering path, unchanged.
- `SPREAD`: paired display units over the original effective sequence; transitions and other ineligible items remain standalone. Reading direction controls visual ordering, not the underlying sequence.
- `SPLIT_SCROLL`: two independent columns with independent width, zoom and scroll; boundary/transition items span both columns and reset distribution according to the established distributor policy.
- L2R, R2L, Vertical, Long Strip and Long Strip with Gaps remain the reading-mode authority. Presentation must not silently replace the reading mode.

## Explicitly not implemented
No display host, holder composition, paired image surface, independent column scroller, or renderer activation is included in this stage. The selector remains configuration-only until both viewer families have safe hosts. No build or device validation has been performed.
