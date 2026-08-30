# Pricing Storage - Interaction Summary

## Initial request

The owner requested the next CineCLI Admin workstream, Pricing Storage. The requested scope was persisted `pricing.tsv`, validation, atomic storage failure handling, immutable ticket/snack/promotion price snapshots, customer-side pricing loading before an Admin pricing UI existed, tests, documentation, and a final quality review. The owner initially asked Codex to inspect the repository and give a short implementation plan before coding.

The owner then established the Git workflow: treat completed Movie Management as the Admin baseline, verify and fast-forward `codex/admin-interface` from `codex/admin-movie-management-refactor` where safe, and create `codex/admin-pricing-storage` for this workstream.

## Clarifications and planning

Codex inspected the repository guidance, existing storage and customer-purchase conventions, and the approved Admin interface plan. It proposed shared pricing contracts, a dependency graph, and a lead-plus-subagent decomposition before implementation. No blocking product-clarification question was required; the owner had specified the relevant behaviour and persistence rules.

The planned dependency order was pricing persistence and domain snapshots first, customer integration after those contracts compiled, then documentation and quality review after integration.

## Decisions made by me

- The owner approved the versioned TSV format, required identity completeness, price and promotion bounds, deterministic ordering, missing-file seeding, and malformed-file fail-safe behaviour.
- The owner approved atomic persistence requirements: validate complete state, write and force a same-directory temporary file, then require atomic replacement.
- The owner approved stable unit-price and promotion snapshots, with `Bill` calculated only from captured values.
- The owner chose customer-session fail-fast loading before catalogue or seat interaction, and explicitly prohibited seat-state initialization after a pricing-load failure.
- The owner approved the shared contracts and directed subagents A and B to run concurrently, C only after A+B integration, and D only after A+B+C integration.
- The owner directed that the three outstanding Movie Management changes be committed as its final baseline, then authorized fast-forwarding the Admin integration branch and creating the Pricing branch.
- After review, the owner authorized one normal Pricing Storage commit, fast-forward of `codex/admin-interface`, and creation of `codex/admin-screening-management`; no Screening Management implementation was authorized.

## Codex proposals and assumptions

- Codex proposed immutable `Pricing` state, identity-oriented ticket/snack enums, applied `PromoCode` values, and a `PricingStorage` API with parser, checked exception, deterministic serialization, and a narrow fault-injection seam for storage tests.
- Codex proposed reusing the established per-store atomic-write pattern rather than introducing a database, framework, dependency, or a speculative shared storage layer.
- Codex treated the existing ticket, snack/combo, and promotion values as the source of seeded defaults, rather than inventing new values.

## Follow-up prompts and corrections

- The owner supplied the complete approved Pricing Storage implementation scope after the initial planning request and asked that no new large PRD/TDD/grilling cycle be started.
- The owner later requested a review-only report of the uncommitted Pricing diff. Codex reported the 34 changed files, responsibilities, documentation, retained storage duplication, deferred work, and no unrelated Movie Management changes; it made no changes during that review.
- During the implementation quality review, Codex identified that `Files.exists(...)` could treat an inaccessible path as missing and accidentally reseed it. It changed this to confirmed-missing handling (`Files.notExists(...)`) before final verification.

## Implementation performed

- Movie Management was committed as the baseline in commits `d1ba968` and `2c2bb6e`; `codex/admin-interface` was fast-forwarded to that baseline and `codex/admin-pricing-storage` was created.
- Pricing persistence, parsing, validation, default seeding, complete-state saving, deterministic serialization, checked failures, and atomic write handling were implemented.
- Ticket selections, snack selections, promotions, bills, customer application flow, UI rendering, and `Main` wiring were updated to use persisted pricing and immutable snapshots.
- Focused storage, model, bill, customer startup, and UI tests were added or updated.
- Documentation and development artifacts were updated: User Guide, Developer Guide, data README, pricing PRD/TDD/requirements-to-tests artifacts, and the Pricing Storage task log.
- Pricing Storage was committed as `fd6e26b Add pricing storage and price snapshots`. `codex/admin-interface` was then fast-forwarded to that commit, and `codex/admin-screening-management` was created from it.

## Testing and verification

- Focused tests were run during implementation, including pricing parsing/storage, snapshots, bills, customer startup, and failure behaviour.
- The final `./mvnw.cmd verify` equivalent (`.\\mvnw.cmd verify` on Windows) passed: 210 tests, zero failures or errors, and all coverage checks met.
- The first final Maven attempt could not access Maven's local repository under the sandbox; it was rerun with the required repository access and passed.
- The final pre-commit check confirmed exactly the reviewed 34 Pricing Storage files, no unexpected or missing files, a clean diff check, and a clean working tree after committing.
- The Pricing branch was a clean fast-forward descendant of `codex/admin-interface`; the integration fast-forward created no merge commit and did not rewrite history.

## Issues / mistakes / lessons

- The storage review exposed the inaccessible-path seeding risk described above; it was corrected before the workstream was committed.
- Pricing storage intentionally retains an atomic-write implementation similar to catalogue and seat storage. A generic extractor was evaluated but deferred because it would have widened the change into completed modules for little immediate benefit.
- Git reported CRLF conversion warnings while staging files. These were warnings only; the diff check was clean.

## Deferred or unresolved items

- Admin UI for fixed-price editing.
- Admin promotion CRUD.
- Later role-routing integration.
- Persistence of captured price snapshots with any future booking persistence work.
- Re-evaluation of a shared atomic-storage helper in a later storage-focused workstream.

