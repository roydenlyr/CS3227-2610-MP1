# Role Routing and Deferred Confirmation Integration

## Approved scope

- Keep `Main` as a thin bootstrap and place role transitions/shared wiring in
  `ApplicationRouter`.
- Make existing Movie, Screening, and Pricing/Promotions workflows reachable
  through a three-section administrator homepage without redesigning their
  internals.
- Parse trimmed, case-insensitive global role commands once in the shared
  terminal adapter.
- Defer customer seat persistence until complete bill rendering succeeds, then
  atomically persist seats before outputting the bill.
- Correct `SeatStorage` only so browsing does not create a missing seat file and
  failed first persistence leaves that file absent.

## Integrated behaviour

`ApplicationRouter`, `Utf8Terminal`, and `AdministratorApplication` provide the
shared route from `Main` to either role. A single `CatalogRecoveryGate` runs
before each role dispatch. Customer selections remain in memory until final bill
text is prepared; final conflicts suppress the bill and refresh seat selection,
and a post-persistence output failure does not undo confirmed seats.

The User Guide, Developer Guide, runtime-data policy, and feature checklists now
describe the actual entry point, homepage, finalization order, and storage
semantics. `MOV-GAP-002` through `MOV-GAP-008` remain open inherited Movie
checklist gaps.

## Verification status

Focused regression coverage was added for global commands at customer deferred
prompts, local cancellation, final conflict/retry, pre-persistence terminal
failure, post-persistence bill-output failure, and administrator homepage
error-output failure.

On 30 August 2026, a clean `verify` completed from an isolated temporary source
copy and Maven repository, without relying on the locked workspace build output
or repository. All tests passed and JaCoCo reported zero missed instructions,
branches, and lines. A packaged-JAR smoke run from a separate empty temporary
runtime directory accepted `/admin`, `1`, `0`, and `/exit`, displayed the
administrator homepage and Movie Management menu, and exited with code 0.

The earlier workspace lock was left untouched. The broader plan's dedicated
forced-subprocess-termination test remains an audit item.
