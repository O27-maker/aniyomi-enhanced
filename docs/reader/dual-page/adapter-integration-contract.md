# Spread adapter integration contract

Status: design checkpoint for the experimental `feature/dual-page-reading` branch. This document does not wire the feature into the live reader or alter stable/main.

## Non-negotiable invariant

The original `PagerViewerAdapter.items` list remains the authoritative effective sequence of `ReaderPage`, `ChapterTransition`, and `InsertPage` objects. Spread mapping is a derived view only. Never replace or reorder the authoritative sequence to implement pairing.

The mapper and navigation map are already pure units:
- `SpreadAdapterItemMapper.map(items, isBoundary, canPair)` builds singles/pairs while retaining effective adapter indices.
- `SpreadAdapterNavigationMap` maps any adapter member to its display unit and creates an anchor that preserves the requested adapter index.

## Required integration bridge

A future integration should introduce a dedicated adapter/view bridge rather than wrapping `PagerPageHolder` inside `SpreadFrameLayout` prematurely.

1. **Display-unit projection**: Build mapped display units from the adapter's current `items` snapshot. Chapter-transition entries and inserted/special pages are boundaries and are always single units. Pair only eligible `ReaderPage` objects; reject pages with special-page status or incompatible landscape/rotation state as dictated by the existing viewer rules.
2. **Anchor preservation**: Whenever current adapter selection changes, derive its display index through `SpreadAdapterNavigationMap`. If a pair contains the selected second page, retain that exact adapter index as the active anchor; do not normalize it to the pair's first page.
3. **Adapter updates**: Rebuild projection after `setChapters`, `onPageSplit`, and `cleanupPageSplit`. Because split-page callbacks mutate the effective sequence, projection cannot be cached without an invalidation mechanism.
4. **Selection and progress**: Selection callbacks must report the anchored original `ReaderPage`. Existing progress/resume continues to use adapter/page identity and indices; display-unit indices must not leak into progress storage.
5. **Chapter transitions**: Transition holders remain independent display units. Pairing must never bridge a transition or any boundary item.
6. **Holder lifecycle**: Preserve `PagerPageHolder` creation, cancellation, and status-flow ownership. Do not detach/reparent live holders without a lifecycle design. The current holder starts its loading/processing job during init and cancels on detach; it does not currently restart that job on attach. Reuse/reparenting therefore needs a verified recovery strategy.
7. **Direction**: The mapper consumes the adapter's already-effective order. `R2L` adapter order is reversed upstream in `setChapters`; integration must not reverse the mapped units again.
8. **Fallback**: If projection cannot safely handle an item, render it as a single unit and keep the original Pager path. No silent page loss, crop, stretching, or forced pair.

## Validation gates before live wiring

- Unit tests cover mapper boundary/eligibility and navigation anchors (already authored, not yet executed).
- Test selected first and second members of pairs, including after chapter updates and split insertion.
- Verify current-page callback, progress, resume, previous/next, chapter transition, and preloading remain adapter-based.
- Verify holder loader/status/zoom/gesture lifecycle before any holder reuse or reparenting.
- Android build and device test are mandatory before enabling the experimental mode in a user-facing setting.

## Safest next implementation step

Implement and test a pure projection refresh helper that accepts an adapter item snapshot and eligibility/boundary predicates, returning display units plus a selected-page anchor. Keep it independent from Android `View` ownership and keep the original adapter list untouched. Live ViewPager integration remains blocked until the holder lifecycle and selection/progress bridge are resolved.
