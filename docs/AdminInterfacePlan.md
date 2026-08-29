# CineCLI Admin Interface Implementation Plan

## Authority and use

This document is the persistent source of truth for the approved cross-feature
plan covering the CineCLI administrator interface, shared persistence, deferred
seat confirmation, test coverage, and Git workflow.

It records target behaviour. A requirement described here is not evidence that
the behaviour has already been implemented. Consult the implementation status
below and inspect the current integration branch before planning a feature.

For each remaining feature, use this sequence:

1. Read `AGENTS.md`, both authoritative standards PDFs, and this document.
2. Inspect the latest `codex/admin-interface` state and select exactly one
   remaining feature.
3. Invoke `$grill-me` for that feature. Resolve its product ambiguities without
   silently changing the cross-feature decisions in this document.
4. Produce and obtain owner approval for a PRD.
5. Produce and obtain owner approval for a TDD consistent with the approved
   PRD.
6. Start implementation only after the owner approves both documents.
7. Implement on the feature branch from the latest integration tip using
   incremental, coherent, green commits and the coverage workflow below.

If feature-level grilling, a PRD, or a TDD conflicts with this plan, present the
conflict to the owner. Treat a new owner decision as an explicit amendment and
update this document in the same workstream.

Persist each approved feature PRD, TDD, and requirements-to-tests checklist
under `docs/admin/<feature>/`. Mark the approval status and date at the top of
each artifact so a later task can distinguish a draft from an owner-approved
design.

## Authoritative references

All architecture, implementation, testing, and review work must follow:

- `../Resources/CS2113 - Software Engineering for Self-Directed Learners [Printable Version for CS2113].pdf`
- `../Resources/Java coding standard.pdf`

The written Java indentation rule controls: 4 spaces for block indentation and
an additional 8 spaces for continuation indentation. Java source must not use
tabs.

## Implementation checkpoint

Checkpoint date: 27 August 2026.

Completed and fast-forwarded into `codex/admin-interface`:

- Customer feature history was fast-forwarded through seat selection,
  snack/combo selection, ticket types and promo codes, and bill-summary
  sections before the integration branch was created.
- `codex/admin-test-foundation`
  - `979c780 Define feature testing workflow`
  - `64787a3 Enforce complete baseline coverage`
- `codex/admin-package-structure`
  - `8125134 Separate role and persistence packages`
  - `5c8619d Unify checked storage failures`
- The checkpoint has 130 passing tests, 100% aggregate line coverage, and 100%
  aggregate branch coverage.
- The packaged JAR starts at `cinecli.app.Main` and was smoke-tested from an
  empty temporary directory.

The package-structure checkpoint contains these role and persistence locations:

```text
cinecli
├── app
├── admin
│   ├── ui
│   └── parser
├── customer
│   ├── ui
│   └── parser
├── model
└── storage
    ├── catalog
    ├── pricing
    ├── seat
    ├── transaction
    └── exception
```

Only package placeholders exist for unimplemented admin, pricing, and
transaction responsibilities. The current customer code still has its
pre-deferred-confirmation behaviour. None of the remaining target behaviour in
this document should be assumed complete.

## Product scope

Add an unauthenticated administrator UI as CineCLI's second role-focused
interface. The customer and administrator workflows share domain and persistence
modules. The administrator manages the catalogue and pricing visible to the
customer.

The approved administrator scope is:

- list, add, edit, and delete movies;
- list, add, edit, and delete screenings;
- edit the global prices of the existing ticket types;
- edit the global prices of the existing snacks and combos; and
- list, add, edit, and delete percentage promotions.

The approved exclusions are:

- authentication or authorization credentials;
- complete booking records;
- payment processing;
- snack or ticket inventory;
- promotion expiry rules;
- concurrent-process locking or cross-process reservations;
- a database;
- production dependencies, frameworks, or libraries not separately approved.

Use Java 25 LTS without preview features, Maven coordinates
`cinecli:cinecli:0.1.0-SNAPSHOT`, the `cinecli` package root, JUnit 5, and
structured plain-text persistence. Keep all selected tool and dependency
versions pinned.

## Application and role behaviour

### Global commands

- CineCLI starts in customer mode.
- Recognize trimmed, case-insensitive `/admin`, `/customer`, and `/exit` at every
  prompt in both role interfaces.
- The shared application loop owns input and converts global commands into typed
  role transitions.
- Role switching is normal control flow, not exception handling.
- `/admin` during an unfinished customer purchase immediately discards the
  customer session, including every tentative seat, without updating
  `seats.tsv`.
- `/customer` during an unfinished customer purchase also discards that session
  without updating `seats.tsv`.
- `/exit`, EOF, input failure, and forced termination before finalized seat
  persistence leave `seats.tsv` missing or byte-for-byte unchanged.

### Administrator homepage

The administrator homepage contains these sections:

1. Movies
2. Screenings
3. Ticket Prices
4. Snack/Combo Prices
5. Promotions

Display a prominent reminder that `/customer` returns to the kiosk. Use guided
numbered menus, field validation, a change preview, and Y/N confirmation before
each administrative update is persisted. Persist a confirmed change
immediately.

## Deferred seat confirmation

### Tentative state

- Selected seats remain tentative and in memory throughout ticket, snack, and
  promotion selection.
- Tentative seats appear unavailable in the current customer's in-memory seat
  map.
- Tentative selection does not write or initialize `seats.tsv`.
- Customer workflow state owns tentative coordinates. Ordinary cancellation
  needs no storage-layer release operation because tentative seats were never
  persisted.
- The single-process assumption remains. CineCLI does not promise reservations
  between concurrently running processes.
- A missing `seats.tsv` is treated as empty while browsing and remains absent
  until the first successful finalized purchase.

At every intermediate prompt between seat selection and final bill generation,
termination, role switching, cancellation, EOF, or input failure must leave
persistent occupancy unchanged.

### Finalization order

The customer workflow must finalize in this order:

1. Validate every ticket, snack, and promotion selection.
2. Construct the immutable `Bill`.
3. Generate the complete bill-summary string in memory.
4. Reload current occupancy and atomically confirm the selected seats.
5. Display the already-generated bill only after seat persistence succeeds.

Separate pure bill formatting from terminal output so the summary can be fully
generated, or fail, before seat confirmation begins.

Ticket and snack selections capture immutable unit-price snapshots. An applied
promotion captures its code and percentage. `Bill` calculates only from these
captured values, so a later administrative price change cannot alter an existing
in-memory bill.

### Finalization outcomes

- On success, confirm the selected seats exactly once and then display the
  pre-generated summary.
- After a successful bill, retain confirmed occupancy and prompt for ENTER,
  `/admin`, or `/exit`.
- Once summary generation and atomic seat confirmation both succeed, the seats
  are legitimately confirmed. A later forced termination does not roll them
  back.
- If final confirmation detects a seat conflict:
  - do not display the bill;
  - do not change occupancy;
  - discard ticket, snack, and promotion selections; and
  - return to seat selection for the same screening using a freshly loaded seat
    map.
- If final persistence fails:
  - preserve the original seat file;
  - do not display the bill;
  - report the storage error; and
  - end the unfinished purchase at the post-session prompt.

## Catalogue management

### Movies

- List movies in their persisted display order.
- Add movies with immutable generated IDs in the form `MOV-<UUID>`.
- Edit approved mutable movie fields while preserving the movie ID.
- Delete a movie only after validation, preview, and Y/N confirmation.
- Movie deletion cascades through all screenings belonging to that movie and all
  persisted occupancy belonging to those screenings.

### Screenings

- List screenings with their parent movies in persisted display order.
- Add screenings with immutable generated IDs in the form `SCR-<UUID>`.
- Edit approved mutable screening fields while preserving the screening ID.
- Rescheduling a screening preserves its persisted occupancy.
- Deleting a screening clears its persisted occupancy.
- Delete only after validation, preview, and Y/N confirmation.

The feature-level PRD must define exact guided input flows, field-edit choices,
validation messages, cancellation behaviour, ordering behaviour, and previews.
The TDD must fit those decisions to catalogue version 1 without changing its
approved compatibility guarantees.

## Pricing and promotions

### Fixed priced identities

Keep the existing three ticket types and five snack/combo definitions. Their
identities, names, and membership are fixed; only their global prices are
editable.

The missing-file seed preserves the current values:

- Adult ticket: S$11.00
- Senior ticket: S$4.50
- Student ticket: S$7.00
- Popcorn: S$5.00
- Nachos: S$6.00
- Soft Drink: S$3.00
- Popcorn Combo (Popcorn + Soft Drink): S$7.00
- Nachos Combo (Nachos + Soft Drink): S$8.00

Accept each price from S$0.01 through S$9,999.99 inclusive. Validate values using
exact decimal/cents semantics rather than binary floating-point arithmetic.

### Promotions

- Promotions apply percentage discounts.
- Accept integer percentages from 1 through 100 inclusive.
- A code is 1 through 32 characters.
- Its first character is alphanumeric.
- Remaining characters are letters, digits, underscores, or hyphens.
- Codes are case-insensitive at input and stored in uppercase.
- Persisted promotion codes are unique under case-insensitive comparison.
- Support listing, adding, editing, and deleting promotions.

The missing-file seed also preserves the current promotions: `CS2103` at 20%
and `CS3227` at 99%.

The feature-level PRD must resolve exact user interaction, duplicate-edit
behaviour, confirmation previews, and cancellation semantics. The TDD must
resolve the canonical version 1 record grammar without weakening the persistence
requirements below.

## Persistence

### Existing and new files

- Preserve `catalog.tsv` format version 1.
- Preserve `seats.tsv` format version 1.
- Add `pricing.tsv` format version 1.
- `pricing.tsv` contains exactly one price for every fixed ticket identity and
  every fixed snack/combo identity, plus zero or more unique promotions.
- When `pricing.tsv` is missing, seed it with the current customer-facing values.
- When an existing pricing file is malformed, reject it without overwriting it.

Do not decide or alter a record schema outside an approved feature TDD. Canonical
serialization, validation, and version handling belong behind each storage
module's interface.

### Shared storage responsibilities

Shared storage operations must support:

- catalogue load and save;
- pricing load and save;
- read-only occupancy loading that does not initialize a missing file;
- final atomic seat confirmation;
- administrative seat clearing; and
- transaction recovery before either role accesses affected data.

All checked persistence failures live under `cinecli.storage.exception` and share
the `StorageException` parent for role-level handling.

### Atomic updates

Every single-file update must:

1. validate the complete proposed state;
2. serialize it canonically;
3. create a temporary file in the target file's directory;
4. write the complete proposed state; and
5. require atomic replacement of the target.

An update failure preserves the original file. Do not fall back to a non-atomic
replacement.

### Catalogue deletion recovery

Catalogue deletions that also affect persisted seats use an atomic recovery
journal containing the intended catalogue and seat snapshots.

- A confirmed deletion first records the complete intended snapshots in the
  journal.
- Recovery completes that confirmed deletion idempotently before either role
  accesses catalogue or occupancy data.
- Recovery may be interrupted and safely retried.
- Remove the journal only after both intended snapshots are durable.
- A malformed or unrecoverable journal blocks further affected data use.
- Recovery must not overwrite current files when the journal cannot be safely
  interpreted.

The movie-management TDD must define the journal's version 1 grammar,
transaction states, write ordering, recovery decision table, and deterministic
fault-injection seams before implementation.

## Testing and coverage

### Mandatory gate

- Keep `org.jacoco:jacoco-maven-plugin` pinned at `0.8.15`.
- Bind `prepare-agent`, `report`, and `check` to Maven.
- Enforce bundle-level `LINE` and `BRANCH` `COVEREDRATIO` minimums of `1.00`.
- Apply no coverage exclusions without explicit owner approval.
- Every production commit must pass the aggregate gate.
- Inspect the generated JaCoCo HTML report after every feature and reconcile
  every production line and branch with a meaningful test.
- Treat 100% coverage as execution evidence, not proof of test quality.

### Test-design obligations

Preserve and adapt existing tests. For every added or changed responsibility,
test:

- happy paths and alternative successful paths;
- invalid inputs and retry behaviour;
- expected domain and storage exceptions;
- input and output failures;
- persistence failures at every transaction stage;
- interrupted recovery and idempotent recovery;
- every role and workflow transition; and
- interactions between UI, workflow, model, formatting, and persistence modules.

Apply equivalence partitioning to at least:

- empty and nonempty state;
- configured and unconfigured state;
- valid and invalid values;
- occupied and free seats;
- unique and duplicate identities;
- tentative and confirmed occupancy; and
- pending and recovered transactions.

Apply boundary value analysis immediately below, at, and above every relevant:

- price limit;
- promotion percentage limit;
- quantity limit;
- promotion-code length limit;
- menu limit;
- seat row and number limit;
- date/time limit; and
- numeric capacity limit.

Assert observable results, including models, output and error text, file bytes,
missing-file state, preserved files, exception type/cause/message, and role or
session state.

### Seat-lifecycle proof obligations

Add tests proving all of the following:

- A missing seat file remains missing through seat, ticket, snack, and promotion
  prompts.
- Existing seat-file bytes remain unchanged at every intermediate prompt.
- `/admin`, `/customer`, `/exit`, EOF, cancellation, and input exceptions discard
  tentative seats without storage writes.
- A synchronized subprocess can be forcibly terminated at an intermediate
  prompt without changing occupancy.
- Bill-formatting failure occurs before storage and leaves occupancy unchanged.
- Final seat confirmation occurs exactly once after summary generation.
- A final conflict suppresses the bill and returns to refreshed seat selection.
- Atomic-write failure suppresses the bill and preserves the original file.
- Successful confirmation persists seats and displays the pre-generated summary.

Maintain a requirements-to-tests checklist for each feature. A passing coverage
gate does not replace inspection of the checklist, assertions, partitions,
boundaries, state transitions, and failure interactions.

### Final integration verification

Before the completed integration branch is offered for owner review:

1. Run `.\mvnw.cmd clean verify`.
2. Inspect the JaCoCo HTML report.
3. Inspect the packaged JAR and bundled resources.
4. Run scripted JAR smoke tests from an empty temporary directory.
5. Review the complete integration diff against this plan, every approved PRD,
   every approved TDD, and both authoritative standards.

## Git workflow

### Completed preparation

`master` was fast-forwarded with `--ff-only` through:

1. `codex/seat-selection`
2. `codex/snack-combo-selection`
3. `codex/ticket-types-promocodes`
4. `codex/bill-summary-sections`

`codex/admin-interface` was then created from the updated `master`. The verified
test-foundation and package-structure branches were fast-forwarded into it.

### Remaining sequence

Process the remaining branches sequentially from the latest
`codex/admin-interface` tip:

1. `codex/admin-pricing-storage`
2. `codex/admin-movie-management`
3. `codex/admin-screening-management`
4. `codex/admin-pricing-management`
5. `codex/admin-role-routing`
6. `codex/admin-documentation`

Owner-approved split recorded on 27 August 2026: the former combined catalogue
management workstream is divided into Movie management followed by Screening
management. Movie management owns movie CRUD, movie-deletion cascade, and the
shared deletion-recovery foundation. Screening management owns standalone
screening CRUD and reuses that foundation.

The owner may split a feature further during grilling, PRD, or TDD. Record an
approved split in this document before implementation and preserve the same
sequential integration rule.

For each branch:

1. Complete and approve its grilling, PRD, and TDD.
2. Create it from the latest integration tip.
3. Commit incremental, coherent, passing behaviour slices.
4. Run relevant focused tests while developing.
5. Run the complete Maven verification before every production commit and before
   integration.
6. Inspect the coverage report and requirements-to-tests checklist.
7. Review the branch diff for scope, standards, and unrelated changes.
8. Fast-forward the verified branch into `codex/admin-interface` with
   `--ff-only`.
9. Retain the feature branch unless branch deletion is separately authorized.

Owner approval is required for coverage exclusions, failing or work-in-progress
commits, destructive Git actions, pushes, and merging the completed
`codex/admin-interface` branch into `master`. Do not rebase shared history,
amend existing commits, force-push, or delete branches without explicit
authorization.

## Feature handoff index

Use this index to start a new task without loading unrelated future-feature
details into the active design prematurely.

### Pricing storage

Branch: `codex/admin-pricing-storage`

Grill, PRD, and TDD must resolve:

- immutable price snapshots in ticket/snack selections and `Bill`;
- the `pricing.tsv` version 1 canonical grammar;
- completeness and uniqueness rules;
- missing-file seeding using current values;
- malformed-file rejection and byte preservation;
- exact decimal input conversion and boundaries;
- atomic replacement and fault injection; and
- customer loading behaviour before admin editing exists.

### Movie management

Branch: `codex/admin-movie-management`

Artifacts: `docs/admin/movies/`

Grill, PRD, and TDD must resolve:

- guided movie list, add, edit, and delete flows;
- immutable `MOV-<UUID>` IDs;
- mutable title and rating fields, ordering, previews, confirmation, and
  cancellation;
- movie-deletion cascade through child screenings and their persisted occupancy;
- canonical catalogue and occupancy updates;
- recovery-journal grammar, ordering, and idempotent recovery; and
- malformed/unrecoverable journal blocking behaviour.

### Screening management

Branch: `codex/admin-screening-management`

Artifacts: `docs/admin/screenings/`

Status: Implemented on 29 August 2026. Concise verification traceability is
recorded in `docs/admin/screenings/RequirementsToTests.md`.

- Provides guided screening list, add, edit, and delete flows.
- Generates immutable `SCR-<UUID>` IDs; valid legacy IDs remain supported.
- Selects a parent movie only during Add. The parent and ID are immutable; Edit
  changes the date, time, or both.
- Preserves occupancy during rescheduling and clears only the selected screening's
  occupancy during deletion.
- Retains catalogue version 1 and seat version 1, with movie-major persisted
  display order and selected-parent append ordering.
- Reuses the existing catalogue-deletion recovery foundation with
  `DELETE_SCREENING` journal semantics alongside `DELETE_MOVIE`.

### Pricing management

Branch: `codex/admin-pricing-management`

Grill, PRD, and TDD must resolve:

- guided fixed-price edit flows;
- promotion list/add/edit/delete flows;
- code normalization and uniqueness;
- price, percentage, and code boundaries;
- previews, Y/N confirmation, retries, and cancellation; and
- immediate persistence and customer-visible reload behaviour.

### Role routing and deferred confirmation

Branch: `codex/admin-role-routing`

Grill, PRD, and TDD must resolve:

- the shared application-loop interface and typed transitions;
- global-command interception at every prompt;
- customer session ownership and discard semantics;
- pure bill formatting and failure injection;
- final confirmation ordering and exactly-once interaction;
- conflict refresh and same-screening retry;
- persistence-failure post-session behaviour;
- successful post-bill prompt behaviour; and
- synchronized forced-termination testing.

### Documentation and integration

Branch: `codex/admin-documentation`

Update `docs/UserGuide.md`, `docs/DeveloperGuide.md`, `docs/Reflections.md`,
runtime-data documentation, and task logs to match only implemented and verified
behaviour. Reconcile this plan's status, run final integration verification, and
prepare the integration branch for owner review without merging it into
`master`.

## New-task prompt template

Use this template in a new chat, replacing the bracketed feature name:

```text
Read AGENTS.md and docs/AdminInterfacePlan.md completely. Work only on the
[feature name] entry in the feature handoff index. Inspect the latest
codex/admin-interface state and distinguish completed behaviour from target
behaviour. Start with $grill-me and do not implement production code. After the
feature plan is finalized, produce a PRD for owner approval, followed by a TDD
for owner approval. Implementation may begin only after both are approved.
Preserve every cross-feature decision and report any conflict instead of
resolving it silently.
```
