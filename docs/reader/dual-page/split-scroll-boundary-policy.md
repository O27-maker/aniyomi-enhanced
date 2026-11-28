# Split Scroll boundary policy

The distributor now accepts an `isBoundary` predicate. A boundary is emitted as a `FULL_WIDTH` placement, never placed into either scroll column, and resets the next ordinary page to the left column.

This policy is intended for adapter entries such as chapter transitions and inserted/special pages. The caller must explicitly identify these entries; the generic distributor cannot infer reader model types.

## Guarantees

- Every input item appears exactly once in `inSourceOrder()`.
- Every placement retains its index in the supplied effective sequence.
- Boundary entries are independently addressable through `placementForOriginalIndex()`.
- Ordinary pages alternate left/right within each segment.
- The first ordinary page after a boundary starts in the left column.
- The input sequence is not mutated.
- For R2L, callers pass the already reversed effective adapter sequence; the distributor does not reverse it.

## Remaining design decision

The reset-to-left behavior is a conservative deterministic policy, not yet a confirmed visual rule for every Reading Mode. Before UI integration, verify whether each mode should restart at the left after a boundary or preserve column parity. Keep transitions full-width regardless.
