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
