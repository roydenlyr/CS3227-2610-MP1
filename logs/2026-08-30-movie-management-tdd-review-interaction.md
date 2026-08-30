# Movie Management TDD Review - Interaction Summary

## Initial request

The owner asked Codex to read `AGENTS.md`, `docs/AdminInterfacePlan.md`, and the
approved Movie-management PRD, then review the Movie-management TDD and
requirements-to-tests checklist. The requested review scope was conflicts,
implementation-critical omissions, and traceability gaps only. The owner
explicitly prohibited code implementation and branch creation, authorized only
necessary document corrections, and requested an approval-ready summary with any
remaining owner decision.

## Clarifications and planning

Codex did not ask a blocking clarification. It noted that the repository already
contained an unrelated modified Java file and preserved it. It also found a
status-history inconsistency: the TDD had both owner-approved metadata and a
draft-approval statement, while the checklist claimed completed implementation.

Codex reviewed the approved plan and PRD, the relevant source and test evidence,
and the two governing standards PDFs. It also checked the Java 25 `Files.move`
contract because the journal-publication outcome depends on `ATOMIC_MOVE`
semantics.

## Decisions made by me

- I provided the approved PRD as the product authority for this review.
- I limited this conversation to document review and corrections: no code,
  branches, commits, or implementation work were authorized.
- I did not approve any of Codex's proposed TDD revisions or the remaining
  legacy-control-character decision during this conversation.

## Codex proposals and assumptions

- Codex treated the TDD and checklist as draft revisions, while preserving their
  recorded original approval history. This was an interpretation of my request,
  not a new owner decision.
- It proposed using `CatalogRecoveryGate` as the single role-level recovery
  interface, with the later role-routing design responsible for exactly-once
  invocation and the Movie recovery-completed message.
- It proposed completing deterministic filesystem-fault seams, including
  catalogue/seat initialization and journal-directory creation.
- It proposed stricter journal validation: a movie-deletion journal must delete a
  movie with at least one child screening and carry canonical intended catalogue
  bytes. It also proposed post-publication inspection to distinguish
  `NOT_APPLIED` from `RECOVERY_PENDING` truthfully.
- For legacy titles containing non-TSV ISO control characters, Codex recommended
  preserving stored bytes but displaying controls as visible uppercase four-hex
  escapes (for example, `\u0007`).

## Follow-up prompts and corrections

There were no owner follow-up prompts or corrections to the TDD review. The owner
then requested this conversation-only interaction log, explicitly excluding
project-wide history and unrelated workstreams. Codex corrected the
TDD/checklist status and traceability claims on its own review findings; those
corrections remain subject to owner approval.

## Implementation performed

This conversation was primarily planning and review, not implementation.

Codex edited only the Movie-management TDD and requirements-to-tests checklist.
The TDD gained the recovery-gate handoff, complete typed fault model, stronger
journal rules, an atomic-publication classification rule, and an explicit owner
decision for legacy control-character display. The checklist gained explicit
requirement and use-case-extension mappings, eight open verification gaps, an
owner-decision dependency, and a completion gate.

No production or test code was implemented, no branch was created, and no commit
was made by Codex.

## Testing and verification

- Codex reviewed the plan, approved PRD, TDD, checklist, relevant source files,
  and relevant existing tests without modifying them.
- It verified that the revised checklist contained rows for all 21 PRD functional
  and non-functional requirements and all 30 use-case extensions.
- It ran `git diff --check` for the document changes.
- It did not run Maven tests because this was a documentation-only review and no
  code was changed.

## Issues / mistakes / lessons

- The former checklist's claim that all nondeferred checks passed was not
  supported by its evidence. Several passing tests used substring assertions or
  omitted required prompt partitions and durable-state assertions.
- The TDD's recovery handoff conflicted internally by introducing a recovery gate
  while still directing role routing to call the deletion transaction directly.
- The original TDD omitted some deterministic fault stages and did not fully
  define journal-publication failure classification.
- The current parser accepts some legacy ISO control characters even though the
  administrator UI rejects them; the PRD did not define how such persisted titles
  should be displayed.

## Deferred or unresolved items

- The owner must choose the legacy control-character display policy. Codex
  recommended visible escapes; the chosen behaviour requires the PRD and TDD to
  agree before implementation changes.
- Eight nondeferred verification gaps remained in the revised checklist,
  including every-prompt global/EOF/input-failure coverage, missing input
  partitions, exact output assertions, output-failure durability assertions,
  journal-publication fault cases, and additional occupancy/journal checks.
- Raw command parsing and shared recovery-gate routing remain deferred to the
  later role-routing workstream; its TDD must decide the exact shared-loop
  handoff.
- No test log or Maven verification output was created in this conversation.
