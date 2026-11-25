# Dual-page presentation host contract

Status: implementation contract based on source inspection of the current Pager and Webtoon pipelines. This is not a claim that dual-page rendering is integrated.

## Pager: preserve ViewPager ownership

The existing `ViewPagerAdapter` owns the lifecycle of each view:
- `instantiateItem` creates a view and adds it directly to the pager container.
- `destroyItem` calls the adapter hook and removes that view from the pager container.
- `isViewFromObject` requires the view to be the exact object returned by instantiation.

`PagerViewerAdapter.getItemPosition` resolves a `PositionableView` by looking up its original `item` in the current adapter list. `PagerViewer.getPageHolder` also searches direct pager children for `PagerPageHolder`.

Therefore:
1. Do not place a `PagerPageHolder` inside a wrapper and return the wrapper as the pager item without redesigning both lookup paths.
2. Do not make a synthetic spread object replace the original `ReaderPage` in `adapter.items`.
3. A display-unit layer must retain a reversible mapping to original adapter indices and original item identities.
4. Chapter transitions and inserted split pages remain first-class adapter items; they cannot be dropped or silently paired with ordinary pages.
5. Any adapter mutation (chapter replacement, inserted page creation, split cleanup) must invalidate/rebuild the derived display mapping while preserving the selected original item.
6. Do not apply a second R2L reversal: the Pager adapter already reverses its effective sequence for R2L.

A viable Pager design needs an explicit holder strategy. The current direct-child lookup and adapter object contract make naive reparenting or wrapping unsafe. Before selecting a design, prove how the ViewPager's instantiate/destroy/getItemPosition lifecycle behaves with a multi-page display item and how original-page selection callbacks are dispatched.

## Page image state

`ReaderPageImageView` owns the decoded image view, image configuration, animation/disposal, zoom and pan state. It exposes pan capability and operations, but does not currently expose a complete serializable zoom/center snapshot API.

Consequently, recreating a holder does not guarantee zoom/pan restoration. A future split-column implementation must explicitly define:
- whether each original page owns its own zoom state;
- how scale and center are captured/restored across holder recreation;
- how double-tap and pinch gestures are routed when two images share a display surface;
- how landscape zoom and page-selection callbacks behave for each original page.

Do not claim independent zoom is complete until this state path is implemented and tested.

## Webtoon: RecyclerView owns binding and recycling

The Webtoon adapter uses DiffUtil and creates a `WebtoonPageHolder` for each `ReaderPage`. It binds pages through `bind(page)` and resets them through `recycle()`. Its holder uses a Webtoon-specific image view and loading path.

A dual-column Webtoon implementation must preserve:
- RecyclerView's holder ownership and bind/recycle sequence;
- the original page sequence and stable logical page identity;
- chapter transition entries;
- continuous vertical scrolling and long-strip gap semantics;
- independent column scroll offsets without losing the logical current page;
- preload and progress updates based on original ReaderPage objects.

A dedicated two-column RecyclerView arrangement may be explored, but it must not simply reuse Pager holders or feed a Pager display projection into Webtoon.

## Required integration proof before enabling the feature

- Pager instantiate/destroy lifecycle remains balanced for every display unit.
- Every original ReaderPage can be selected and reported exactly once at the correct logical position.
- InsertPage creation and cleanup do not duplicate, skip, or reorder pages.
- Chapter transitions remain reachable and correctly trigger preload.
- R2L uses the existing effective adapter order exactly once.
- Webtoon binds and recycles all pages without leaking jobs or losing status updates.
- Long Strip with Gaps retains the configured gap.
- Zoom/pan state survives the intended navigation and recycling behavior.
- Chapter resume/progress remains tied to the original page, not the display-unit index.

Until those checks are met, keep the new presentation mode opt-in and non-functional scaffolding must not be presented as a working reader feature.
