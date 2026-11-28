# Dual-page holder lifecycle audit

Status: source inspection on `feature/dual-page-reading`; no runtime test performed.

## Pager holder

`PagerPageHolder` starts `loadPageAndProcessStatus()` in its initializer and cancels `loadJob` in `onDetachedFromWindow()`. There is no corresponding attach callback that restarts the job. Reparenting a live holder into a spread container can therefore cancel its loader/status collector and leave the holder without an active collector after reattachment.

It also owns image state, progress/error overlays, page processing, zoom and gesture callbacks. A spread host must not create a second independent loader for the same page or silently lose this holder state.

## Webtoon holder

`WebtoonPageHolder` is managed by RecyclerView. Its `bind(page)` cancels any previous job and starts a new one; `recycle()` cancels the job and resets the image frame and progress/error UI. This lifecycle is materially different from the Pager holder and should remain RecyclerView-owned unless a dedicated dual-column adapter has equivalent bind/recycle behavior.

## Consequence for integration

- Do not share or reparent Pager holders between ViewPager and a new spread container.
- Do not wrap a live Webtoon holder outside RecyclerView ownership.
- A dual-column Webtoon implementation should create/bind/recycle holders through RecyclerView-compatible ownership, or use two coordinated RecyclerViews with a carefully defined shared logical sequence.
- Page-level zoom state needs an explicit ownership and restoration strategy if holders are recreated.
- Keep chapter transitions as their existing dedicated holder types.

## Safe next step

Prefer a new presentation host that owns page-holder creation and lifecycle explicitly, while leaving the current adapters as the source of logical page identity and progress. Before that host is implemented, add lifecycle tests around cancellation, rebinding, status-flow collection, and zoom restoration. Do not activate a new host based only on the pure mapping tests.
