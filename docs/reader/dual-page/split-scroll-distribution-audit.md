# Split Scroll distribution integration audit

Status: prototype only on `feature/dual-page-reading`. No active reader wiring.

## Current prototype

`SplitScrollPageDistributor` alternates source indices into left/right columns. It retains original indices and exposes a source-order reconstruction. This is a simple, deterministic baseline, but it is not yet suitable for all reading modes or real adapter sequences.

## Risks and missing policy

- **Direction**: `PagerViewerAdapter` reverses its effective sequence for R2L. The distributor must consume that sequence without applying another reversal. The current parity rule is based on effective index, so it can be tested in both directions, but it does not encode semantic direction.
- **Special entries**: chapter transitions and `InsertPage` are not ordinary images. The current generic distributor can alternate them into a column; integration must mark them as full-width/boundary items or keep them on the original viewer path.
- **Mode-specific order**: L2R/R2L/vertical/long-strip/long-strip-with-gaps must preserve the existing mode's navigation and gesture expectations. A simple column assignment cannot independently define reading direction or scrolling.
- **Independent scroll**: column assignment is not scroll synchronization. Each column needs its own scroll offset and must not be forced to the same height or scroll position.
- **Zoom/resize**: column width state and zoom state must remain independent; distribution must not reflow pages in a way that resets scale or offsets unexpectedly.
- **Progress**: placement indices are source indices only. Progress and resume must continue using original page/chapter identity, never column-local indices.
- **Uneven lengths**: one column may contain an extra page. This is expected and must not be padded with fake pages.

## Recommended next safe step

Before Android view integration, define and test a policy object for direction, boundaries, and eligible page types. Keep this policy pure and ensure it preserves the original source sequence. Do not change the existing alternation algorithm until the UI behavior for boundaries and all reading modes has been confirmed against the original reader.
