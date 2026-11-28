# Stage 31 — viewer configuration preference observation

Implemented on `feature/dual-page-reading`.

## Changes
- `PagerConfig` now initializes and observes `DualPagePresentationMode` from the existing `dualPageMode()` preference.
- `WebtoonConfig` independently initializes and observes the same typed preference.
- Each configuration exposes a mode-change listener so a future presentation host can react without coupling the two viewer pipelines.
- The existing legacy split/invert/rotate-to-fit preferences remain untouched.

## Scope boundary
This is configuration wiring only. Neither viewer currently renders Spread or Split Scroll from this property. No holders are wrapped or reparented, no page ordering is changed, and the selector must not yet be advertised as functional.

## Verification
The edited files were checked against the existing preference `register` pattern. No Gradle tests, Android build, emulator run, or device test was executed. The new listener paths still require integration tests once a presentation host exists.
