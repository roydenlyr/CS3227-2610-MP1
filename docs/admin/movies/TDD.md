# CineCLI Movie Management Technical Design Document

**Status:** Owner-approved

**Draft date:** 28 August 2026

**Approval date:** 28 August 2026

**Feature:** Movie management

**Planned branch:** `codex/admin-movie-management`

**Source PRD:** Owner-approved on 28 August 2026

## Authority

This TDD translates the owner-approved Movie-management PRD into a technical and
test-driven design. It is subordinate to `AGENTS.md`, `docs/AdminInterfacePlan.md`,
the approved PRD, and the two authoritative standards PDFs referenced by those
documents. A conflict must be presented to the owner instead of being resolved
silently.

This document does not authorize implementation. Implementation may begin only
after the owner approves this TDD. The implementation must then proceed one
red-green cycle at a time through the confirmed seams in this document.

## Design goals

The design must:

- implement the approved Movie list, Add, Edit, and Delete behaviour without
  implementing standalone Screening management;
- keep terminal interaction, input interpretation, workflow state, catalogue
  persistence, occupancy persistence, and recovery as separate concerns;
- place complex deletion and recovery behaviour behind a small, deep interface;
- preserve catalogue and occupancy format-version-1 compatibility;
- make every required success, cancellation, failure, and recovery state
  observable through a confirmed public seam; and
- add no production dependency, framework, database, public persistence field,
  or unrelated feature.

## Existing baseline

The current code provides immutable `Movie` and `Screening` records,
`ContentRating`, catalogue version-1 loading, and seat-occupancy version-1 loading
and atomic confirmation. `CatalogStorage` has no save operation. `SeatStorage`
initializes a missing occupancy file during customer reads. Admin and transaction
packages contain only placeholders. The current customer UI also cannot
distinguish EOF from input failure in its return type or reliably detect output
failure.

Movie management therefore adds new interfaces and adapters without changing the
signatures or behaviour of existing public methods. Later role-routing and
deferred-confirmation work may migrate customer callers to the new shared terminal
and read-only occupancy interfaces.

## Confirmed test seams

Tests cross only these seams:

1. `MovieManagementApplication.run()` for administrator-visible workflow
   behaviour, typed navigation, and terminal failure outcomes.
2. `CatalogStorage.load()` and `CatalogStorage.save(List<Movie>)` for catalogue
   persistence and canonical bytes.
3. The read-only `SeatStorage` occupancy-snapshot interface for presence,
   validation, and canonical administrative replacement.
4. `MovieDeletionTransaction.recover()`, `prepare(String)`, and `commit(...)` for
   deletion previews, commit status, journal durability, and recovery.

Tests use real files under JUnit `@TempDir`. Test doubles are permitted only for
the terminal adapter, UUID generation, and deterministic fault injection at the
filesystem seam. Tests must not mock CineCLI workflow, storage, parser,
serializer, or transaction modules and must not test their private methods.

## Module design

### Movie workflow module

`cinecli.admin.MovieManagementApplication` coordinates the Movie workflow. Its
external interface is:

```java
public MovieManagementOutcome run();
```

`MovieManagementOutcome` has exactly these values:

- `BACK`: return to the administrator homepage through the local Back action;
- `ADMIN`: surrender a typed `/admin` transition to the shared loop;
- `CUSTOMER`: surrender a typed `/customer` transition to the shared loop;
- `EXIT`: surrender a typed `/exit` transition to the shared loop; and
- `TERMINATED`: end after EOF or terminal input/output failure.

The application owns the in-memory Add and Edit drafts and the selected deletion
until confirmation. It reloads the catalogue after every cancellation or
successful persistence before redrawing Movie management. A storage failure
returns `BACK` after the applicable error message is written successfully. If
that write fails, the outcome is `TERMINATED`.

The application does not parse raw global-command text. Every prompt consumes a
typed terminal result and immediately returns the corresponding global outcome,
discarding unconfirmed state. Raw recognition and the destination of those
outcomes belong to the later role-routing workstream.

### Terminal seam

The injected administrator terminal interface provides three operations:

```java
TerminalInput readLine();
boolean write(String text);
boolean writeError(String text);
```

`TerminalInput` is a closed typed result with these alternatives:

- `SubmittedLine(String value)`;
- `GlobalCommand(ADMIN | CUSTOMER | EXIT)`;
- `EndOfInput`; and
- `InputFailure`.

The production Reader/Writer adapter will be supplied by role routing. The Movie
workstream uses a scripted test adapter and does not take ownership of raw global
command recognition.

Every screen, preview, prompt, validation response, cancellation response, and
success response is first rendered completely in memory and passed to one
`write` call. `write` returns true only after the underlying writer has accepted
and flushed the complete string without reporting an error. A false result makes
the workflow call `writeError` best-effort with
`Unable to write output. CineCLI will exit.` and return `TERMINATED`.

`InputFailure` similarly triggers a best-effort
`Unable to read input. CineCLI will exit.` error and `TERMINATED`. `EndOfInput`
returns `TERMINATED` without an error. Failure of `writeError` is ignored because
there is no remaining reliable terminal channel.

No Add, Edit, childless Delete, or new journal publication may begin until its
complete preview has been written successfully and a submitted `Y` has been
validated. Catalogue initialization and recovery of a previously confirmed
journal are not new unconfirmed mutations.

### Input interpretation

The internal Movie input parser has no independent test seam. Its behaviour is
verified through `MovieManagementApplication`.

Numbered input is stripped, matched against `0|[1-9][0-9]*`, parsed with overflow
handling, and checked against the options offered by the current prompt. No
partial numeric parse is allowed.

Title interpretation uses this order:

1. Reject any submitted UTF-16 code unit for which `Character.isISOControl`
   returns true. This rejects tabs before normalization.
2. Remove surrounding code points for which `Character.isWhitespace` or
   `Character.isSpaceChar` returns true.
3. Reject the empty result.
4. Preserve every remaining code point and internal space exactly.

The typed terminal adapter has already removed global commands from submitted
lines. Exact trimmed, case-insensitive `/cancel` remains a local line value. It
cancels Add, Edit, or Delete at every prompt after that operation is selected,
including Edit/Delete target selection. At the fixed action menu, `/cancel` is
not active and receives the numeric-menu error. `0` retains meaning only where
the current prompt offers it; a title of `0` is valid.

Confirmation strips input and accepts only exact case-insensitive `Y`, `N`, or
`/cancel`. `N` and `/cancel` share the applicable cancellation outcome.

### Movie ID adapter

`MovieIdGenerator` is a package-private functional seam returning `UUID`. The
production adapter calls `UUID.randomUUID()`. The application prefixes the UUID's
standard lowercase text with `MOV-` after title and rating validation. It retries
while the candidate duplicates an existing movie ID. There is no artificial retry
limit because the approved product behaviour requires transparent collision
retry.

A normal public constructor creates the production adapter. A package-private
constructor accepts the deterministic adapter used by tests.

## Catalogue persistence

### External interface

`CatalogStorage` retains its current constructors and `load()` method and adds:

```java
public void save(List<Movie> movies) throws CatalogStorageException;
```

`save` performs complete validation and canonical serialization before any
target-file update. Its caller either receives a fully durable replacement or a
checked failure with the original target bytes preserved.

Cross-package transaction collaboration uses concrete final
`CatalogTransactionAdapter` and `SeatTransactionAdapter` classes beside their
respective storage classes. They are public only because Java package visibility
cannot span `cinecli.storage.catalog`, `cinecli.storage.seat`, and
`cinecli.storage.transaction`; their Javadocs mark them as storage-internal and
unsupported for role/workflow callers. They are concrete collaborators, not new
interfaces or test seams.

Each adapter converts between its storage module and an immutable
`TransactionFileSnapshot` carrying validated presence, logical state, and
defensively copied exact bytes. `MovieDeletionTransaction` receives the two
concrete adapters and the journal path through its constructor. No raw-snapshot
method is added to the ordinary `CatalogStorage` or `SeatStorage` interface, and
raw bytes never cross the Movie workflow interface. Transaction tests still use
the public deletion facade rather than these storage-internal collaborators.

### Complete-state validation

Before serialization, catalogue storage verifies:

- the movie list and every element, rating, and screening list are non-null;
- movie IDs and screening IDs match `[A-Za-z0-9][A-Za-z0-9_-]*`;
- movie IDs are unique among movies and screening IDs are unique among
  screenings;
- every title is nonblank and has no leading or trailing Java `strip()`
  whitespace;
- titles contain no tab, carriage return, or line feed that would break the TSV
  grammar;
- every screening time is non-null; and
- the complete serialized state can be parsed back into an equal ordered model.

The storage grammar does not newly reject other legacy ISO control characters
that version 1 previously accepted. Admin title input cannot create them, but a
legacy valid record remains loadable and round-trippable. This preserves the
existing version-1 compatibility guarantee.

### Canonical catalogue bytes

Canonical output uses UTF-8, LF line endings, no byte-order mark, and one final
newline. It contains:

1. `CINECLI-CATALOG<TAB>1`;
2. all `MOVIE` records in persisted movie order; and
3. all `SCREENING` records grouped by persisted movie order and then by that
   movie's persisted screening order.

Dates use `yyyy-MM-dd`; times use two-digit 24-hour `HH:mm`. Existing valid IDs
remain unchanged. Add appends one movie; Edit replaces one movie in place while
retaining its screenings; Delete removes one movie and its screenings while
preserving every remaining relative order.

### Atomic replacement

The reusable single-file writer:

1. validates and materializes all intended bytes;
2. creates the target parent directory if required;
3. creates a unique temporary file in that directory;
4. writes all bytes through a file channel;
5. calls `force(true)` on the temporary file;
6. atomically moves it over the target with `ATOMIC_MOVE` and
   `REPLACE_EXISTING`; and
7. removes a remaining temporary file best-effort.

There is no non-atomic fallback. Directory metadata syncing is excluded because
it is not portable through the approved Java-only implementation. Durability is
limited to forced file contents and the atomic-move guarantees of Java and the
filesystem.

### Deterministic filesystem-fault adapters

Existing string operation names are replaced by package-private typed enums.
The single-file writer enum has exactly:

- `CREATE_DIRECTORIES`;
- `READ_TARGET`;
- `CREATE_TEMPORARY`;
- `WRITE_TEMPORARY`;
- `FORCE_TEMPORARY`;
- `ATOMIC_REPLACE`; and
- `DELETE_TEMPORARY`.

The transaction enum has exactly:

- `READ_JOURNAL`;
- `CREATE_JOURNAL_TEMPORARY`;
- `WRITE_JOURNAL_TEMPORARY`;
- `FORCE_JOURNAL_TEMPORARY`;
- `PUBLISH_JOURNAL`;
- `VERIFY_CATALOG`;
- `VERIFY_SEATS`;
- `DELETE_JOURNAL`; and
- `DELETE_JOURNAL_TEMPORARY`.

Each hook receives an enum value and the affected normalized path immediately
before that filesystem operation and may throw `IOException`. Production uses a
no-op adapter. Package-private constructors accept deterministic test adapters.
Tests still invoke only public storage or transaction behaviour; the hook merely
arranges a true filesystem-seam failure and is never an assertion surface.

## Occupancy persistence

### Read-only snapshot interface

Seat storage adds an immutable `SeatOccupancySnapshot` with:

- `isPresent()`, distinguishing an existing file from a missing file; and
- the complete immutable occupied-seat map grouped by screening ID.

The external read-only interface accepts the complete current set of known
screening IDs and returns a validated snapshot without creating a missing file.
A missing file returns `isPresent() == false` and an empty map. An existing
header-only file returns `isPresent() == true` and an empty map.

The administrative replacement interface accepts a validated present snapshot
and uses the same forced atomic writer as catalogue storage. A missing intended
snapshot performs no creation or deletion. Movie and Screening deletion preserve
occupancy-file presence.

Existing customer-facing methods remain compatible during this workstream. The
later deferred-confirmation workstream adopts the read-only interface for
browsing and final purchase confirmation.

### Canonical occupancy bytes

When affected seat records are removed, canonical output remains occupancy
version 1: UTF-8, LF, no BOM, a final newline, screening IDs sorted
lexicographically, then seats sorted by row and number.

When a cascade finds no occupied-seat record belonging to any child screening,
the intended occupancy bytes equal the original exact bytes. Commit and recovery
skip seat replacement while still journaling a cascade with child screenings.
This avoids changing a logically unaffected file. A missing file remains missing.

Childless deletion never calls any occupancy interface, so malformed occupancy
cannot block it.

## Movie deletion module

### External interface

`MovieDeletionTransaction` is the deep module for preparation, commitment, and
recovery:

```java
public RecoveryResult recover() throws TransactionStorageException;

public PreparedMovieDeletion prepare(String movieId)
        throws MovieDeletionPreparationException;

public MovieDeletionResult commit(PreparedMovieDeletion deletion)
        throws MovieDeletionCommitException;
```

`RecoveryResult` is `NO_JOURNAL` or `RECOVERED`.

`PreparedMovieDeletion` is immutable and opaque. Its public preview exposes only:

- selected movie ID, title, rating, and one-based display position;
- ordered screening ID and start time entries with per-screening occupied counts;
- total screening and occupied-seat counts; and
- whether the operation is childless or cascading.

Original and intended raw snapshots, digests, and journal data are hidden behind
the interface. `MovieDeletionResult` contains only the immutable values required
for the success summary. A prepared deletion is single-use; a second commit is a
caller programming error and is rejected without storage access.

`MovieDeletionCommitException` exposes one status:

- `NOT_APPLIED`: no durable journal was published; or
- `RECOVERY_PENDING`: a valid durable journal exists and must be recovered.

All deletion and recovery checked exceptions extend `StorageException` through
types in `cinecli.storage.exception`.

### Preparation

`prepare` performs no writes. It loads and snapshots the complete catalogue,
locates the selected ID, calculates the intended catalogue, and records the
original raw catalogue bytes.

For a childless movie, it does not access occupancy and prepares an atomic
catalogue-only commit.

For a movie with child screenings, it reads the occupancy snapshot without
initialization, validates all occupancy against the original catalogue, counts
occupied seats for each child in child order, and calculates intended occupancy
by removing only those child IDs. Missing occupancy produces zero counts and
remains missing. Existing occupancy with no affected records retains identical
intended bytes.

### Childless commit

Immediately before an atomic catalogue save, commit verifies that current
catalogue bytes equal the prepared original bytes. Divergence is `NOT_APPLIED`.
No journal or occupancy access occurs. An atomic-save failure is `NOT_APPLIED`;
success returns the prepared result.

### Journal location and grammar

The single runtime journal is the sibling of the runtime catalogue named
`catalog-transaction.journal`. The standard runtime location is therefore
`data/runtime/catalog-transaction.journal`. A constructor accepts its explicit
path for integration and tests.

The version-1 journal is UTF-8, uses LF, has no BOM or blank lines, and ends with
one newline. Standard Base64 with padding and without line wrapping encodes exact
file bytes. SHA-256 values are 64 lowercase hexadecimal characters.

The missing-occupancy form is exactly:

```text
CINECLI-CATALOG-TRANSACTION<TAB>1
OPERATION<TAB>DELETE_MOVIE
SUBJECT_ID<TAB><movie ID>
CATALOG_ORIGINAL<TAB><SHA-256><TAB><Base64 bytes>
CATALOG_INTENDED<TAB><SHA-256><TAB><Base64 bytes>
SEATS_ORIGINAL<TAB>MISSING
SEATS_INTENDED<TAB>MISSING
```

The present-occupancy form replaces the final two lines with:

```text
SEATS_ORIGINAL<TAB>PRESENT<TAB><SHA-256><TAB><Base64 bytes>
SEATS_INTENDED<TAB>PRESENT<TAB><SHA-256><TAB><Base64 bytes>
```

Version 1 accepts only `DELETE_MOVIE`. The generic filename and internal
catalogue/occupancy snapshot writer are reusable. The later Screening TDD may add
an operation value only with its own semantic validator; Movie management does
not pre-implement that operation.

### Journal validation

Before recovery trusts a journal, it verifies:

- exact header, line order, field names, field counts, operation, and final
  newline;
- absence of BOM, blank lines, unknown fields, or extra lines;
- valid subject ID syntax;
- strict standard Base64 decoding and exact lowercase digest syntax;
- each digest against its decoded exact bytes;
- both catalogue snapshots through the version-1 parser;
- present seat snapshots against their corresponding catalogue snapshots;
- equal original and intended occupancy presence;
- the subject movie exists exactly once in the original and not in the intended
  catalogue;
- intended catalogue equals original catalogue minus exactly the subject movie
  and all its screenings, with all remaining values and orders unchanged; and
- intended occupancy equals original occupancy minus exactly records for those
  child screening IDs, with all unrelated occupancy unchanged.

If no affected seat record existed, the intended seat bytes must equal the exact
original bytes. If affected records existed, intended bytes must equal the
canonical serialization of the reduced logical state.

Any violation is malformed or semantically unsafe. Recovery preserves all target
files and the journal, throws `TransactionStorageException`, and blocks affected
access.

### Cascading commit protocol

Commit uses this order:

1. Reject an existing journal; callers must recover before preparation.
2. Re-read catalogue and occupancy and require exact equality with the prepared
   original presence and bytes.
3. Serialize the complete journal in memory.
4. Create and write a same-directory journal temporary file, then force it.
5. Atomically publish it to the journal path without replacing an existing file.
6. Replace catalogue with the intended forced atomic snapshot.
7. Replace occupancy only when present intended bytes differ from original.
8. Re-read both targets and require exact intended presence and bytes.
9. Delete the journal.
10. Return the immutable deletion result.

Successful atomic journal publication at step 5 is the durable-intent boundary.
Failures through step 4, or failure to publish because the target already exists,
are `NOT_APPLIED`; original target data remain unchanged. Every failure after
step 5 is `RECOVERY_PENDING`, even when both target snapshots are already
intended and only verification or journal cleanup failed.

A journal temporary file is not durable intent. Cleanup is best-effort and a
leftover uniquely named temporary file is ignored by recovery.

### Recovery decision table

Recovery runs before Movie management loads affected data. Later role routing
must also invoke the same recovery gate before either role accesses catalogue or
occupancy.

After full journal validation, each current target is classified by exact
presence and byte equality:

| Current catalogue | Current occupancy | Recovery action |
| --- | --- | --- |
| Original | Original | Replace catalogue, replace occupancy if different, verify, delete journal. |
| Original | Intended | Replace catalogue, verify both, delete journal. |
| Intended | Original | Replace occupancy if different, verify both, delete journal. |
| Intended | Intended | Verify both and delete journal. |
| Neither | Any | Preserve all data and journal; block affected access. |
| Any | Neither | Preserve all data and journal; block affected access. |

When original and intended occupancy bytes are identical, that target satisfies
both classifications and no seat replacement occurs. Presence is part of exact
equality, so unexpected creation or removal is divergent.

Each recovery replacement uses the same forced atomic writer. The journal remains
until both intended targets are re-read successfully. A recovery failure preserves
the journal and can be retried. Repeated recovery is therefore idempotent.

## Workflow failure mapping

The application maps checked failures exactly as follows:

| Failure | User-visible prefix | Outcome |
| --- | --- | --- |
| Entry recovery, journal, or catalogue access | `Unable to access movie management:` | `BACK` |
| Add or Edit save | `Movie change was not saved:` | `BACK` |
| Delete preparation | `Unable to prepare movie deletion:` | `BACK` |
| Delete commit `NOT_APPLIED` | `Movie deletion was not applied:` | `BACK` |
| Delete commit `RECOVERY_PENDING` | `Movie deletion is confirmed but recovery is pending:` | `BACK` |
| Terminal input failure | Fixed input-failure message | `TERMINATED` |
| Terminal output failure | Fixed output-failure message best-effort | `TERMINATED` |

The original checked exception is retained as the cause when a lower-level I/O
failure exists. Messages identify the affected path and whether existing data was
preserved or recovery is pending. No success message is written until the
applicable save or transaction has fully completed and, for a cascade, its journal
has been removed.

Output failure after successful Add, Edit, or completed Delete does not roll back
durable data. Output or process failure while a journal exists does not cancel the
confirmed deletion; the next recovery completes it.

## Test-driven implementation sequence

Implementation follows vertical slices. Each numbered item is multiple
red-green cycles, but every cycle adds only one failing public-behaviour test,
implements the minimum behaviour to pass, and then proceeds to the next test.
Refactoring is reserved for the review stage after the relevant behaviour is
green.

1. List a valid catalogue and return `BACK` through the typed terminal seam.
2. Add one valid movie, including deterministic ID generation, preview, and
   atomic catalogue save.
3. Complete Add validation, collision, cancellation, global-command, EOF, and
   failure variants.
4. Edit title, rating, and both fields through one atomic catalogue save, then add
   no-op, replacement, cancellation, and failure variants.
5. Delete a childless movie through catalogue-only preparation and commit,
   proving occupancy is not accessed.
6. Load read-only occupancy and render a cascading preview for present, empty,
   missing, and malformed states.
7. Commit a successful journaled cascade and verify exact target and journal
   bytes at the public deletion seam.
8. Add every pre-intent and post-intent fault, recovery state, interrupted retry,
   malformed journal, and divergent-target case.
9. Complete terminal output-failure cases and the end-to-end requirements
   checklist, then inspect aggregate coverage and assertions.

Focused storage tests may be introduced inside the vertical slice that first
needs the behaviour. Tests must not be written as a horizontal batch ahead of
their implementation slice.

## Test design obligations

`RequirementsToTests.md` is the normative traceability checklist for this TDD.
It maps all functional and non-functional requirements and every use-case
extension to planned tests at the confirmed seams.

Test data must apply equivalence partitioning and boundary-value analysis to:

- empty, one-movie, and multiple-movie catalogues;
- numeric values below, at, and above each offered range and `Integer.MAX_VALUE`;
- syntactically malformed, overflow, and valid numbered input;
- blank, outer Unicode-space, raw-control, Unicode, duplicate, reserved-command,
  and ordinary slash-prefixed titles;
- every rating and each unavailable or literal rating input;
- `Y`, `N`, mixed case, surrounding space, `/cancel`, blank, and invalid
  confirmation;
- childless, occupied, unoccupied, missing, header-only, and malformed occupancy;
- no journal, valid journal, malformed journal, each original/intended target
  combination, and divergent targets; and
- failures before journal publication, after publication, during each target
  replacement, verification, and cleanup.

Independent literal expected transcripts and file bytes are the source of truth.
Tests must not construct expected bytes by calling the production serializer or
recompute expected output using the production formatting rules.

Implementation must retain 100% aggregate line and branch coverage. Every
uncovered line or branch must be investigated, but coverage does not replace
inspection of the checklist, partitions, boundaries, assertions, and recovery
state interactions.

## Cross-workstream handoff

The Movie workstream supplies and tests typed global-command outcomes but does
not parse `/admin`, `/customer`, or `/exit` from raw Reader input. It also invokes
recovery on Movie-management entry but cannot yet gate the existing customer
entry point through the shared loop.

The role-routing workstream must therefore:

- provide the production Reader/Writer terminal adapter;
- recognize trimmed, case-insensitive global commands at every prompt and emit
  the typed terminal outcomes defined here;
- route `MovieManagementOutcome` values to their final destinations; and
- call `MovieDeletionTransaction.recover()` before customer or administrator
  access to catalogue or occupancy.

These obligations appear as explicitly deferred checklist rows. They are not
waived or considered implemented by approval of this TDD.

## Implementation verification gate

After later implementation, but not during this documentation-only task:

1. Run focused red-green cycles at the confirmed seams.
2. Run `mvn verify` and require 100% aggregate line and branch coverage.
3. Inspect the JaCoCo HTML report and every assertion.
4. Reconcile every checklist row with a passing test or its explicit role-routing
   deferral.
5. Inspect canonical catalogue, occupancy, and journal bytes from tests.
6. Review the complete implementation diff against the approved PRD, this TDD,
   `AdminInterfacePlan.md`, and both authoritative standards.

## Approval

This TDD is a draft awaiting separate owner approval. Approval authorizes only
the design recorded here. Branch creation, implementation, commits, integration,
and any later change to product behaviour remain subject to the repository
workflow and the owner's explicit directions.
