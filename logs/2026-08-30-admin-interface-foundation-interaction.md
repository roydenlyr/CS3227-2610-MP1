# Admin Interface Planning and Foundation - Interaction Summary

## Initial request

The owner invoked `$grill-me` and asked to add an administrator interface as
CineCLI's second role-based UI. The initial scope was an admin interface that
shares the customer kiosk's persistence layer and manages the catalogue and
prices visible to customers. The owner also requested a cleaner project
structure with separate administrator and customer packages and nested storage
and exception packages.

The request was then extended with explicit engineering constraints:

- tests must cover happy paths, errors, exceptions, all reachable lines and
  branches, equivalence partitions, and boundary values;
- aggregate line and branch coverage should be 100%, without treating that
  metric as proof of test quality;
- `codex/admin-interface` should be divided into sequential feature branches,
  with incremental commits within each branch; and
- the workflow should be recorded in `AGENTS.md`, with subsidiary instruction
  files only if actually needed.

The owner subsequently corrected the seat lifecycle requirement. Seat choices
must remain tentative until the complete bill summary has been generated and
final occupancy is confirmed. Forced termination or switching to admin during
an unfinished customer flow must not leave seats occupied or update
`seats.tsv`. All other parts of the then-current plan were approved to remain as
they were.

## Clarifications and planning

No standalone clarification question from Codex is present in this conversation
record. Instead, the owner progressively supplied the missing constraints and
then provided a complete finalized implementation plan.

That finalized plan specified:

- unauthenticated movie and screening CRUD;
- editing the prices of the existing ticket, snack, and combo identities;
- percentage-promotion CRUD and its validation limits;
- trimmed, case-insensitive `/admin`, `/customer`, and `/exit` commands at every
  prompt;
- immutable price and promotion snapshots in bills;
- deferred and atomic final seat confirmation after bill formatting;
- version 1 `pricing.tsv` persistence while preserving catalogue and seat format
  version 1;
- an atomic recovery journal for catalogue deletions that also change persisted
  occupancy;
- a strict JaCoCo 0.8.15 line and branch coverage gate;
- negative, exception, interruption, equivalence-partition, boundary-value, and
  interaction testing; and
- a detailed sequential branch and fast-forward integration workflow.

The plan also excluded authentication, complete booking records, payment,
inventory, promotion expiry, cross-process locking, databases, and unapproved
production dependencies.

Codex treated the owner's `PLEASE IMPLEMENT THIS PLAN` prompt as authorization
for the listed local branches, commits, and fast-forward integrations. Before
changing code, Codex read the repository instructions, the applicable skills,
and both authoritative standards PDFs.

## Decisions made by me

The owner explicitly decided or approved the following during this
conversation:

- the admin interface is a second, unauthenticated role UI using shared domain
  and persistence code;
- the administrator manages movies, screenings, the fixed ticket/snack/combo
  prices, and percentage promotions;
- the project uses distinct `app`, `admin`, `customer`, `model`, and nested
  storage packages;
- global role and exit commands are accepted at every prompt;
- tentative seats remain in memory and ordinary cancellation requires no
  persisted release operation;
- bill construction and complete bill-summary generation precede atomic seat
  confirmation, and display follows successful persistence;
- interruption before successful final confirmation preserves a missing seat
  file or its exact existing bytes;
- a final conflict suppresses the bill and returns the customer to refreshed
  seat selection for the same screening;
- a final persistence failure suppresses the bill and preserves the original
  file;
- every production commit retains 100% aggregate line and branch coverage, with
  meaningful non-metric test design as a separate obligation;
- feature work uses sequential child branches and incremental green commits;
- no subsidiary `AGENTS.md` was needed because the workflow applies uniformly;
  and
- `codex/admin-interface` must not be merged into `master` without new approval.

After implementation began, the owner instructed Codex to finish only the
active test-foundation task and pause because feature-level PDDs and TDDs had
not yet been completed. The owner later authorized resuming only through package
restructuring, again requiring a fresh grilling session followed by a PDD and
TDD before each feature implementation.

Finally, the owner requested that the finalized cross-feature plan be saved as a
persistent repository artifact for future chats.

## Codex proposals and assumptions

Codex proposed or selected these implementation details within the approved
scope:

- the root `AGENTS.md` would remain the single repository-wide instruction
  source instead of adding subsidiary files;
- application startup would move to `cinecli.app`, the kiosk workflow to
  `cinecli.customer`, customer input parsing to `cinecli.customer.parser`, and
  customer terminal output to `cinecli.customer.ui`;
- catalogue and seat persistence would move to separate storage packages, while
  checked persistence exceptions would share `StorageException`;
- deterministic package-private storage-operation hooks would provide internal
  seams for testing initialization, read, temporary-write, atomic-replacement,
  and cleanup failures without adding production dependencies;
- empty admin, pricing, and transaction package locations would initially be
  represented by `package-info.java`, without implementing their features; and
- the persistent plan would live at `docs/AdminInterfacePlan.md`, with a concise
  context pointer in `AGENTS.md` and a reusable new-chat prompt.

The plan artifact also records that approved feature PDDs, TDDs, and
requirements-to-tests checklists should be stored under
`docs/admin/<feature>/` so later chats can distinguish approved documents from
drafts.

## Follow-up prompts and corrections

The historical sequence of owner follow-ups was:

1. Expand the test plan beyond happy paths to errors, exceptions, branches,
   lines, equivalence partitions, and boundary values.
2. Require a branch per admin feature and incremental commits within each
   branch.
3. Correct seat handling so interruptions do not persist occupancy and seat
   confirmation occurs only after bill-summary generation.
4. Supply and authorize the complete consolidated implementation plan.
5. Stop after the active coverage-foundation task because PDD and TDD work was
   still required.
6. Resume only through package restructuring and pause before feature
   implementation.
7. Require every later feature to start with grilling, then an approved PDD,
   then an approved TDD.
8. Request a persistent cross-feature plan artifact for use in new chats.

No later requirement in this conversation reversed the coverage, persistence,
or Git safeguards. The later prompts narrowed when implementation was allowed
to continue.

## Implementation performed

This conversation was primarily planning and development-foundation work. It
did not implement the requested admin features.

Codex performed the following repository work:

- fast-forwarded `master` through the four previously approved customer feature
  branches listed in the finalized plan;
- created `codex/admin-interface` from that updated `master`;
- created and integrated `codex/admin-test-foundation`;
- updated `AGENTS.md` with coverage, test-design, tentative-state, incremental
  commit, and fast-forward integration rules;
- pinned JaCoCo 0.8.15 and configured bundle-level 100% line and branch checks;
- expanded the baseline test suite from 101 to 130 tests;
- added deterministic storage failure seams and corresponding I/O, exception,
  EOF, interruption, and boundary tests;
- created and integrated `codex/admin-package-structure`;
- moved startup, customer workflow, UI, parser, catalogue storage, seat storage,
  and checked exceptions into the approved packages;
- split the shared storage fault hook into package-private catalogue and seat
  hooks;
- added the common checked `StorageException` parent;
- updated the JAR manifest to use `cinecli.app.Main`;
- created placeholder package documentation for the unimplemented admin,
  pricing, and transaction areas; and
- created `docs/AdminInterfacePlan.md` plus its `AGENTS.md` context pointer on
  `codex/admin-plan-artifact`, then fast-forwarded it into
  `codex/admin-interface`.

The commits created in this conversation were:

- `979c780 Define feature testing workflow`
- `64787a3 Enforce complete baseline coverage`
- `8125134 Separate role and persistence packages`
- `5c8619d Unify checked storage failures`
- `892fab0 Record approved admin implementation plan`

## Testing and verification

Codex first ran the existing Maven tests and confirmed the 101-test customer
baseline passed.

After adding the JaCoCo gate, verification initially failed at 92% line and 88%
branch coverage. Codex inspected the generated XML/HTML reports, added tests for
the actual uncovered decisions and failures, and reran the gate iteratively.
Intermediate reports reached 94%/92% and 98%/99% before the baseline reached
100% for both counters.

The completed test-foundation checkpoint had:

- 130 passing tests;
- 969 of 969 production lines covered; and
- 283 of 283 production branches covered.

After package restructuring and the common exception hierarchy, Codex ran
clean/full Maven verification again. The resulting checkpoint had:

- 130 passing tests;
- 976 of 976 production lines covered; and
- 283 of 283 production branches covered.

Codex inspected the JaCoCo HTML/XML totals, reviewed diffs with
`git diff --check`, inspected required packaged JAR entries and the manifest,
and smoke-tested the JAR from an empty temporary directory. The smoke test
confirmed successful startup, default catalogue initialization, and no
unexpected `seats.tsv` creation. The documentation-artifact branch also passed
the complete Maven verification before integration.

No separate test log was created during the earlier implementation steps in
this conversation. The persistent planning artifact was created, but this
interaction summary itself was requested only at the end of the conversation.

## Issues / mistakes / lessons

- The first package-move command failed because the destination directories did
  not yet exist on the Windows checkout. Git changed no files. Codex created the
  exact approved directories and repeated the moves successfully.
- The first standalone packaged-JAR inspection failed because `jar.exe` was not
  on the shell PATH. Codex located the Java 25 installation and reran both JAR
  inspection and execution with explicit executable paths.
- The coverage gate deliberately exposed substantial untested error and
  interruption paths. Reaching 100% required meaningful reader failures,
  malformed data, EOF-at-prompt, storage failure, race, cleanup, and boundary
  tests rather than only more happy-path executions.
- Codex was still closing the active foundation's coverage gaps with internal
  storage-testability edits when the owner clarified that work must pause for
  PDD and TDD preparation. Codex finished only that foundation slice,
  integrated it, and did not proceed into an admin feature.
- During drafting of the persistent plan, Codex initially expanded the PDD/TDD
  abbreviations. It removed those expansions before committing the artifact so
  it would not invent terminology the owner had not defined.
- The interaction established an important process lesson: approval of the
  cross-feature plan did not replace feature-specific grilling, PDD approval,
  and TDD approval. Later implementation must pass all three gates separately.

## Deferred or unresolved items

No feature-level PDD, TDD, or requirements-to-tests checklist was produced in
this conversation. Those artifacts remain mandatory before their respective
feature implementations.

The following planned branches and behaviours were left unimplemented at the
end of this conversation:

- `codex/admin-pricing-storage` and `pricing.tsv` version 1;
- `codex/admin-catalog-management`, including movie/screening CRUD, occupancy
  clearing, and deletion recovery;
- `codex/admin-pricing-management`, including price and promotion management;
- `codex/admin-role-routing`, including global commands and deferred seat
  confirmation; and
- `codex/admin-documentation` and final integration documentation/review.

The finalized plan for those future conversations is
`docs/AdminInterfacePlan.md`. At the end of this conversation,
`codex/admin-interface` contained only the test foundation, package
restructuring, and persistent plan artifact. It had not been pushed or merged
into `master`.
