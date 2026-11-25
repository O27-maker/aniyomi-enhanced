# Dual-page host renderer integration audit

Branch: `feature/dual-page-reading`

## Scope

This audit records the concrete constraints of the existing Pager and Webtoon hosts before either host is changed to render Spread or Split Scroll. The persisted presentation preference is already available as `ViewerConfig.dualPagePresentationMode`; this document does not claim that either mode is rendered.

## Pager host

- `PagerViewer` owns one `PagerViewerAdapter` whose `items` list is the authoritative effective sequence. It includes previous/current/next chapter pages, chapter-transition items, and dynamically inserted `InsertPage` items.
- `PagerViewerAdapter.setChapters()` reverses the effective list for `R2LPagerViewer`. Any display projection must consume this already-directed sequence and must not reverse it a second time.
- The adapter's ViewPager contract is one view per adapter position. `createView()`, `getItemPosition()`, and `PagerViewer.onPageChange()` all assume that positions map to original item identities.
- `PagerViewer.onPageChange()` drives activity progress, page selection, holder callbacks, chapter preload, and transition handling. A spread host must preserve the active original adapter index as its navigation/progress anchor, even when both pages belong to one spread.
- `getPageHolder()` searches direct ViewPager children for `PagerPageHolder`. Reparenting or nesting those holders would break this lookup.
- `PagerPageHolder` begins loading from its initializer and cancels its load job when detached. There is no demonstrated attach-time restart path. A renderer must not move/reparent active holders as a shortcut to pairing pages.
- Split-page insertion and cleanup mutate the adapter sequence. Any spread projection/navigation map must be rebuilt after those mutations and after chapter replacement.

### Pager implementation boundary

A safe first renderer should remain a ViewPager adapter projection: each display position owns a lightweight spread container, while page content is created and bound under an explicit lifecycle contract. It must not silently change the authoritative item sequence. Before implementation, define how a paired page holder starts, stops, and resumes loading without violating the existing detach behavior.

## Webtoon host

- `WebtoonViewer` currently owns one `WebtoonRecyclerView`, one `WebtoonLayoutManager`, one `WebtoonAdapter`, and one `WebtoonFrame`.
- `WebtoonAdapter.items` is an ordered effective list containing chapter pages and full-width chapter transitions. It uses DiffUtil and RecyclerView's bind/recycle lifecycle.
- `WebtoonPageHolder.bind()` and `recycle()` are the supported load lifecycle. A second column must not share one holder instance between RecyclerViews.
- `WebtoonViewer.onScrolled()` derives the active item from the last end-visible adapter position. Progress, page selection, preload, and chapter transitions currently depend on this single ordered list.
- `WebtoonFrame` delegates pinch, fling, quick-scale, and zoom-out behavior to its single child recycler. Two columns require explicit gesture routing and separate zoom state; merely placing two existing frames side by side will not create independent scroll/zoom semantics.
- The current adapter treats transitions as a distinct view type. A split-scroll projection must keep transitions full-width and preserve their position in the effective sequence.

### Webtoon implementation boundary

Split Scroll needs a coordinated host with two independently scrolling column lists and a single logical sequence/progress coordinator. Column assignment must be a derived projection of the original adapter sequence; transitions remain full-width synchronization barriers. Each column owns its own RecyclerView, adapter/holders, zoom state, and gesture handling. The coordinator must select the correct logical page when either column scrolls, without allowing recycled or off-screen pages to report stale progress.

## Shared requirements before activation

1. Keep the selected presentation independent of ReadingMode (L2R, R2L, Vertical, Long Strip, and Long Strip with Gaps).
2. Preserve original item identity, page order, chapter boundaries, inserted pages, and requested-page resume position.
3. Recompute projections after every source-sequence mutation.
4. Define one authoritative mapping between display positions, column positions, and original adapter indices.
5. Specify how zoom/pan state is preserved when a holder is recycled or a page changes columns.
6. Keep the feature non-default and non-rendering until the host lifecycle and progress contracts are implemented and tested.

## Verification status

Source inspection only. No Android unit tests, instrumentation tests, or APK build were run. This audit does not enable either presentation mode. Stable Build #139 and `main` are not modified by this work.
