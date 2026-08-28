# CineCLI Movie Management Product Requirements Document

**Status:** Owner-approved

**Draft date:** 27 August 2026

**Approval date:** 28 August 2026

**Feature:** Movie management

**Planned branch:** `codex/admin-movie-management`

## Authority

This PRD defines the proposed product behaviour for Movie management from the
owner-confirmed grilling decisions. It is
subordinate to `AGENTS.md`, `docs/AdminInterfacePlan.md`, and the two authoritative
standards PDFs referenced by those documents. If this PRD conflicts with a
cross-feature decision, the conflict must be presented to the owner instead of
being resolved silently.

The owner approved this PRD on 28 August 2026. This approval does not authorize
implementation. A consistent TDD must be approved before implementation begins.

## Purpose and user

Movie management enables a cinema administrator to inspect and maintain the
movies shown in the customer catalogue. The target user is an administrator using
the same terminal application as the customer kiosk. Authentication is not
required in the approved scope.

The workflow must be guided and safe for an administrator who does not know
catalogue IDs or the persisted file format. Destructive consequences must be
visible before confirmation.

## Scope

### Included

- List movies in persisted display order.
- Add a movie with an immutable generated ID.
- Edit a movie's title and content rating while preserving its ID and position.
- Delete a movie and cascade the deletion through its child screenings and their
  persisted seat occupancy.
- Preview and confirm every add, edit, and delete before persistence.
- Recover a confirmed cascade deletion if its multi-file update is interrupted.

### Excluded

- Standalone screening list, add, edit, reschedule, and delete workflows.
- Movie or screening reordering.
- New content-rating values or age-eligibility enforcement.
- Movie-title uniqueness or a movie-title length limit.
- Authentication, authorization, payment, booking records, or inventory.
- Concurrent-process reservations or locking.
- A database, new production dependency, framework, or library.
- Public storage interfaces, canonical record grammar, journal grammar,
  transaction states, write ordering, recovery decision tables, and fault seams;
  these belong to the Movie-management TDD.

## Dependencies and constraints

- The administrator homepage exposes Movies as section 1. The later role-routing
  feature owns the shared application loop and global-command transitions.
- `catalog.tsv` format version 1 and `seats.tsv` format version 1 remain compatible.
- Existing catalogue IDs such as `MOV-001` remain valid. Only newly generated
  admin IDs use the narrower `MOV-<UUID>` form.
- A header-only catalogue is a valid empty catalogue.
- Every movie may contain zero or more screenings.
- Confirmed changes are visible when either role next loads the catalogue.
- The approved single-process assumption remains.

## Glossary

| Term | Meaning |
| --- | --- |
| Movie | A catalogue entry with an immutable ID, title, content rating, and ordered child screenings. |
| Display position | A movie's one-based position in persisted movie order. |
| Draft | Validated in-memory values that have not been confirmed or persisted. |
| Child screening | A screening whose persisted parent movie ID is the selected movie's ID. |
| Occupied seat | A persisted seat coordinate belonging to a child screening. |
| Durable deletion intent | A confirmed deletion whose complete intended snapshots have been recorded in the recovery journal. |
| Affected data | Catalogue, occupancy, and deletion-journal data used by Movie management. |

## Functional requirements

### MOV-FR-001 - Enter and list Movie management

When the administrator enters Movie management, CineCLI must recover any pending
valid deletion before loading affected data. It must then show all movies in
persisted display order.

The screen begins with:

```text
Movie Management

Movies
```

Each movie must use a non-truncating multi-line block:

```text
<number>. <full title>
   ID: <movie ID>
   Rating: <rating>
   Screenings: <screening count>
```

The title must be shown in full, including Unicode and preserved internal spaces.
The list does not show screening details or occupancy counts.

For an empty catalogue, CineCLI must show:

```text
No movies are currently available.
```

### MOV-FR-002 - Show the fixed action menu

After the movie list, CineCLI must show:

```text
Actions
1. Add movie
2. Edit movie
3. Delete movie
0. Back
Enter choice:
```

The four actions remain visible when the catalogue is empty. Selecting Edit or
Delete with an empty catalogue must not request a target. CineCLI must show the
applicable message and redraw Movie management:

```text
There are no movies to edit.
```

```text
There are no movies to delete.
```

Selecting Back returns to the administrator homepage without changing data.

### MOV-FR-003 - Validate numbered input

Numbered action, movie-selection, rating, and edit-field input must:

- ignore surrounding whitespace;
- accept only `0` or an ASCII decimal number whose first digit is `1` through
  `9`;
- reject a sign, decimal point, exponent, internal whitespace, empty input, and
  any other character;
- reject values too large to parse safely; and
- reject a number not offered by the current prompt.

Leading zeroes are not accepted. `0` has special meaning only where the current
prompt explicitly offers it.

The action-menu error is:

```text
Enter 0, 1, 2, or 3.
```

A movie-target error is:

```text
Enter a movie number from 1 to <movie count>, or 0 to go back.
```

An edit-field-menu error is:

```text
Enter 0, 1, 2, or 3.
```

Invalid input must repeat the same prompt without discarding other valid draft
values.

### MOV-FR-004 - Validate movie titles

Movie-title input must first be checked in its submitted form. Any tab or ISO
control character anywhere in the submitted line is rejected. CineCLI must then
normalize the remaining input by removing surrounding Unicode whitespace, where
Unicode whitespace means a code point for which `Character.isWhitespace` or
`Character.isSpaceChar` returns true. The normalized title must:

- contain at least one non-whitespace character;
- otherwise preserve every Unicode character and internal space exactly.

Duplicate titles are valid under case-sensitive and case-insensitive comparison.
Movie identity is determined by the immutable ID, not by title. No length limit is
imposed.

The title prompt is:

```text
Enter movie title (/cancel to cancel):
```

The validation messages are:

```text
Movie title must not be blank.
```

```text
Movie title must not contain tabs or control characters.
```

Exact trimmed, case-insensitive `/admin`, `/customer`, and `/exit` inputs are
reserved global commands. Exact trimmed, case-insensitive `/cancel` is the local
form-cancellation command. These four exact values cannot be stored as titles
through the administrator UI. Other slash-prefixed titles remain valid when they
satisfy the title rules.

### MOV-FR-005 - Select a content rating

The only content ratings are `PG13`, `M18`, and `R21`. CineCLI must request a
rating through this numbered form:

```text
Content Ratings
1. PG13
2. M18
3. R21
Enter rating number (/cancel to cancel):
```

The invalid-rating message is:

```text
Enter 1 for PG13, 2 for M18, or 3 for R21.
```

Literal rating names are not accepted as rating input. `/cancel` abandons the
whole current Add or Edit operation.

### MOV-FR-006 - Add a movie

The Add flow must:

1. request and validate the title;
2. request and validate the rating;
3. generate an ID in the form `MOV-<UUID>`;
4. show the complete proposed movie and its display position;
5. request Y/N confirmation; and
6. append and persist the movie only after Y.

The UUID must be a random version-4 UUID in the standard lowercase, hyphenated
text form. The generated ID must not duplicate any existing movie ID. A collision
must cause generation to be retried without user intervention.

The new movie has no child screenings. Its display position is one greater than
the previous movie count. ID generation occurs after both fields are valid so the
same ID can be shown in the preview and persisted after confirmation.

The preview is:

```text
Add Movie Preview
ID: <generated movie ID>
Title: <title>
Rating: <rating>
Display position: <position>
Confirm add? (Y/N):
```

N or `/cancel` discards the generated ID and all entered values, shows:

```text
Movie addition cancelled.
```

and redraws Movie management.

A successfully persisted addition shows:

```text
Movie added: <title> [<movie ID>].
```

and redraws Movie management from persisted state.

### MOV-FR-007 - Select a movie for Edit or Delete

Edit and Delete target the current one-based display number, not a typed movie ID.
The prompts are:

```text
Enter movie number to edit (0 to go back):
```

```text
Enter movie number to delete (0 to go back):
```

`0` abandons target selection and redraws Movie management without a cancellation
message. `/cancel` at either target prompt cancels the active operation, shows
`Movie edit cancelled.` or `Movie deletion cancelled.` as applicable, and redraws
Movie management. Invalid input uses the MOV-FR-003 movie-target message and
repeats the same prompt.

### MOV-FR-008 - Build an Edit draft

After target selection, CineCLI must show the selected movie's immutable ID and
display position, followed by the current proposed title and rating and this menu:

```text
Edit Movie
ID: <movie ID>
Display position: <position>
Title: <current proposed title>
Rating: <current proposed rating>

1. Change title
2. Change rating
3. Review changes
0. Cancel edit
Enter choice:
```

The administrator may change either or both mutable fields. Selecting the same
field again replaces its earlier draft value. Re-entering the original persisted
value removes that field from the change set.

An unchanged title shows:

```text
The movie title is unchanged.
```

An unchanged rating shows:

```text
The movie rating is unchanged.
```

Selecting Review with no real change shows:

```text
No movie changes have been made.
```

and returns to the same Edit menu. It does not show a confirmation or write data.

`0` or `/cancel` discards the entire Edit draft, shows:

```text
Movie edit cancelled.
```

and redraws Movie management.

### MOV-FR-009 - Preview and confirm an Edit

Reviewing a nonempty Edit draft must show the immutable identity, display
position, and complete final state. Each changed field must show its old and new
values. Each unchanged field must be marked unchanged.

```text
Edit Movie Preview
ID: <movie ID>
Display position: <position>
Title: <old title> -> <new title>
Rating: <rating> (unchanged)
Confirm edit? (Y/N):
```

The title and rating lines use `<value> (unchanged)` when unchanged and
`<old value> -> <new value>` when changed.

N or `/cancel` discards the entire draft, shows `Movie edit cancelled.`, and
redraws Movie management. Y persists both proposed fields as one update while
preserving the ID, display position, and child screenings.

A successfully persisted edit shows:

```text
Movie updated: <final title> [<movie ID>].
```

and redraws Movie management from persisted state.

### MOV-FR-010 - Prepare a Delete preview

Before showing a Delete confirmation, CineCLI must validate the selected movie,
catalogue, and any occupancy required by the cascade.

For a movie with child screenings, the preview must show each screening in its
persisted order. Screening date-times use English month names and the established
`dd MMM uuuu, HH:mm` display form. The preview must show the occupied-seat count
for each screening and the total number of occupied seats to be removed. It must
not enumerate individual seat coordinates.

```text
Delete Movie Preview
ID: <movie ID>
Title: <title>
Rating: <rating>
Display position: <position>
Screenings to delete: <screening count>
1. <screening ID> - <dd MMM uuuu, HH:mm> - Occupied seats: <count>
<additional screening lines in persisted order>
Total occupied seats to delete: <total count>
WARNING: Deleting this movie also deletes all listed screenings and their occupied-seat data.
Confirm delete? (Y/N):
```

For a movie without child screenings, the preview shows `Screenings to delete: 0`
and `Total occupied seats to delete: 0`, omits screening lines, and replaces the
cascade warning with:

```text
This movie has no screenings or occupied seats.
```

If occupancy is missing, it is treated as empty for the preview and remains
missing. If occupancy is malformed or unreadable, a deletion with child
screenings is blocked before preview and confirmation. CineCLI shows:

```text
Unable to prepare movie deletion: <storage message>
```

and returns to the administrator homepage. Listing, Add, Edit, and a childless
Delete do not access occupancy and are not blocked solely by malformed occupancy.

### MOV-FR-011 - Confirm and persist a Delete

N or `/cancel` at the Delete confirmation shows:

```text
Movie deletion cancelled.
```

and redraws Movie management without changing data.

Y confirms deletion of the movie, all its child screenings, and all persisted
occupancy for those screenings. A childless movie uses a catalogue-only update. A
movie with one or more child screenings uses the recovery-journal workflow even
when the occupancy file is missing or contains no affected seat records.

The last movie may be deleted. A successful last-movie deletion leaves the valid
header-only empty catalogue. Later movie display numbers close the gap left by any
successful deletion.

Successful completion shows:

```text
Movie deleted: <title> [<movie ID>]; removed <screening count> screening(s) and <occupied-seat count> occupied seat(s).
```

and redraws Movie management from persisted state.

### MOV-FR-012 - Confirm mutations

Add, Edit, and Delete confirmations must:

- ignore surrounding whitespace;
- accept exact `Y` or `N` case-insensitively;
- treat `/cancel` as N;
- reject blank input and any other input; and
- repeat the same confirmation after invalid input.

The invalid-confirmation message is:

```text
Enter Y to confirm or N to cancel.
```

No mutation may begin before its complete preview has been written successfully
and Y has been read.

### MOV-FR-013 - Preserve ordering

- Listing follows persisted movie order.
- Add appends to the end.
- Edit preserves the selected movie's position.
- Delete preserves the relative order of every remaining movie and naturally
  closes the one-based display-number gap.
- Child screenings shown in a Delete preview follow their persisted order.
- No Movie-management action sorts or manually reorders movies or screenings.

### MOV-FR-014 - Handle global commands and local cancellation

The shared application loop must retain precedence for trimmed,
case-insensitive `/admin`, `/customer`, and `/exit` at every Movie-management
prompt. Movie management must surrender those inputs as typed global transitions
and discard any unconfirmed target or draft without persistence.

`/cancel` is local to an active Add, Edit, or Delete form. `0` is Back or Cancel
only in numbered menus and movie-target prompts that explicitly offer it.

After a local cancellation or N, CineCLI redraws Movie management from persisted
state. The role-routing feature will define the final destination and presentation
of each global transition; this PRD does not redefine them.

### MOV-FR-015 - Handle storage and recovery failures truthfully

On entry, a valid pending deletion must be recovered before affected data is
listed. Successful recovery shows:

```text
Pending movie deletion recovery completed.
```

and then continues into Movie management. A malformed or unrecoverable journal
blocks affected data use, shows:

```text
Unable to access movie management: <storage message>
```

and returns to the administrator homepage.

An Add or Edit persistence failure must preserve the original catalogue, discard
the draft, show:

```text
Movie change was not saved: <storage message>
```

and return to the administrator homepage.

A Delete failure before durable deletion intent is recorded means the deletion is
not committed. CineCLI must preserve the original durable data, show:

```text
Movie deletion was not applied: <storage message>
```

and return to the administrator homepage.

A Delete failure after durable deletion intent is recorded means the confirmed
deletion remains pending and must be completed by recovery. CineCLI must show:

```text
Movie deletion is confirmed but recovery is pending: <storage message>
```

and return to the administrator homepage. It must not report deletion success
until recovery has made both intended snapshots durable.

### MOV-FR-016 - Fail closed on terminal I/O failure

- EOF or input failure before durable mutation must end the current interaction
  and leave persistent state unchanged.
- An output failure before durable mutation must prevent confirmation and
  persistence, make a best-effort report to the error stream, and end CineCLI.
- An input failure reports `Unable to read input. CineCLI will exit.` when the
  error stream remains usable. EOF may end without an error message.
- An output failure reports `Unable to write output. CineCLI will exit.` when the
  error stream remains usable.
- I/O failure after an Add or Edit has been atomically persisted does not roll it
  back.
- I/O failure after durable deletion intent does not cancel that intent; recovery
  must complete it before later affected access.

## Use cases

### UC-MOV-01 - List movies

**Actor:** Administrator

**Main success scenario:**

1. Administrator selects Movies from the administrator homepage.
2. CineCLI recovers any valid pending deletion.
3. CineCLI loads and validates the catalogue.
4. CineCLI lists movies in persisted order and shows the fixed action menu.

**Extensions:**

- 2a. Recovery cannot safely complete: CineCLI reports the access failure and
  returns home without listing partial data.
- 3a. Catalogue is empty: CineCLI shows the empty message and all fixed actions.
- 3b. Catalogue is malformed or unreadable: CineCLI reports the access failure
  and returns home without listing partial data.
- 4a. Output fails: CineCLI ends according to MOV-FR-016.

### UC-MOV-02 - Add a movie

**Actor:** Administrator

**Main success scenario:**

1. Administrator selects Add.
2. CineCLI obtains a valid title and rating.
3. CineCLI generates a unique immutable ID and previews the append.
4. Administrator confirms with Y.
5. CineCLI atomically persists the complete proposed catalogue.
6. CineCLI reports success and redraws Movie management.

**Extensions:**

- 2a. A field is invalid: CineCLI reports the applicable validation message and
  repeats that field.
- 2b. Administrator enters `/cancel`: CineCLI discards the form and redraws Movie
  management.
- 3a. ID generation collides: CineCLI generates another ID without user action.
- 4a. Administrator enters invalid confirmation: CineCLI reports the Y/N message
  and repeats confirmation.
- 4b. Administrator enters N or `/cancel`: CineCLI discards the form and generated
  ID, then redraws Movie management.
- 5a. Persistence fails: CineCLI preserves the original catalogue, reports that
  the change was not saved, and returns home.
- *a. A global command or pre-commit I/O failure occurs: CineCLI discards the
  unconfirmed form and follows MOV-FR-014 or MOV-FR-016.

### UC-MOV-03 - Edit a movie

**Actor:** Administrator

**Main success scenario:**

1. Administrator selects Edit and a valid movie number.
2. Administrator changes the title, rating, or both in the draft.
3. CineCLI previews the complete final state and old-to-new differences.
4. Administrator confirms with Y.
5. CineCLI atomically persists the complete proposed catalogue.
6. CineCLI reports success and redraws Movie management.

**Extensions:**

- 1a. Catalogue is empty: CineCLI reports that there are no movies to edit.
- 1b. Selection is invalid: CineCLI reports the dynamic target-range message and
  repeats selection.
- 2a. A field is invalid: CineCLI reports the applicable message and repeats that
  field while preserving other draft changes.
- 2b. A proposed field equals its persisted value: CineCLI reports it as unchanged
  and does not add it to the change set.
- 3a. No real change exists: CineCLI reports that no changes were made and returns
  to the Edit menu without confirmation.
- 4a. Administrator enters N or `/cancel`: CineCLI discards the complete draft and
  redraws Movie management.
- 5a. Persistence fails: CineCLI preserves the original catalogue, reports that
  the change was not saved, and returns home.
- *a. Administrator enters `0` at selection/Edit menu: CineCLI abandons Edit and
  redraws Movie management.
- *b. A global command or pre-commit I/O failure occurs: CineCLI discards the
  draft and follows MOV-FR-014 or MOV-FR-016.

### UC-MOV-04 - Delete a movie

**Actor:** Administrator

**Main success scenario:**

1. Administrator selects Delete and a valid movie number.
2. CineCLI validates cascade data and previews the movie, screenings, and occupied
   seat counts.
3. Administrator confirms with Y.
4. CineCLI persists the confirmed deletion through the applicable atomic or
   journaled path.
5. CineCLI reports success and redraws Movie management.

**Extensions:**

- 1a. Catalogue is empty: CineCLI reports that there are no movies to delete.
- 1b. Selection is invalid: CineCLI reports the dynamic target-range message and
  repeats selection.
- 2a. The movie has no screenings: CineCLI previews zero cascade counts and uses
  the catalogue-only path after confirmation.
- 2b. Occupancy is missing: CineCLI previews zero occupancy and preserves the
  missing-file state.
- 2c. Required occupancy is malformed or unreadable: CineCLI blocks deletion,
  reports that it cannot prepare the deletion, and returns home.
- 3a. Administrator enters N or `/cancel`: CineCLI cancels without persistence and
  redraws Movie management.
- 4a. Failure occurs before durable intent: CineCLI reports that deletion was not
  applied and returns home.
- 4b. Failure occurs after durable intent: CineCLI reports confirmed pending
  recovery and returns home; later affected access must recover it.
- 5a. The deleted movie was last: CineCLI redraws the valid empty state.
- *a. A global command or pre-commit I/O failure occurs: CineCLI abandons the
  unconfirmed deletion and follows MOV-FR-014 or MOV-FR-016.

## Non-functional requirements

### MOV-NFR-001 - Compatibility

Movie management must preserve the approved compatibility guarantees of
catalogue and occupancy format version 1. It must not require existing IDs to use
the new generated UUID form.

### MOV-NFR-002 - Data safety

No invalid, cancelled, unconfirmed, or failed single-file proposal may alter
durable data. A durable journal is an explicit confirmed-deletion commitment and
must be recoverable idempotently.

### MOV-NFR-003 - Usability

Every actionable choice is numbered, every rejected value states the accepted
form, all destructive impact is visible before confirmation, and immutable IDs
remain visible for traceability without being required as input.

### MOV-NFR-004 - Testability

Every functional requirement and use-case extension must be observable through
terminal output, workflow outcome, returned role transition, persistent bytes,
missing-file state, or checked storage failure. The TDD must provide deterministic
seams for otherwise nondeterministic IDs and storage faults.

### MOV-NFR-005 - Maintainability

The later technical design must keep terminal interaction, input interpretation,
movie rules, catalogue persistence, occupancy persistence, and recovery concerns
separate. No speculative framework, database, dependency, or unrelated feature
may be introduced.

## Acceptance criteria

The Movie-management PRD is satisfied only when all of the following observable
behaviour is implemented and verified:

1. Empty and nonempty catalogues render the exact list and action behaviour.
2. Valid Add, single-field Edit, multi-field Edit, childless Delete, cascading
   Delete, and last-movie Delete persist the specified result and ordering.
3. Every numbered prompt rejects malformed, overflow, below-range, and above-range
   input independently and retries the same prompt.
4. Title validation covers blank, outer whitespace, tabs, controls, Unicode,
   internal spacing, duplicate titles, and exact reserved commands.
5. Every rating is selectable through its number and literal or unavailable values
   are rejected.
6. Add and Edit previews show complete proposed state; Delete previews show every
   child screening and the required counts without seat-coordinate disclosure.
7. N, `/cancel`, `0` where offered, global commands, EOF, and pre-commit I/O
   failures leave durable state unchanged.
8. Confirmed Add/Edit failures preserve original catalogue bytes.
9. Childless Delete does not access occupancy; a cascade Delete validates
   occupancy and uses durable recovery even when no affected seat records exist.
10. Missing occupancy remains missing, while malformed occupancy blocks only a
    cascade-dependent deletion when no pending journal already blocks access.
11. Delete failures before and after durable intent produce different truthful
    outcomes, and pending recovery completes idempotently before later access.
12. Successful persistence is visible when Movie management or the customer role
    next reloads the catalogue.

## Requirements index

| ID | Requirement | Primary acceptance evidence |
| --- | --- | --- |
| MOV-FR-001 | Enter and list Movie management | Ordered multi-line list, empty state, recovery-before-read |
| MOV-FR-002 | Show fixed actions | Exact menu, Back, empty Edit/Delete behaviour |
| MOV-FR-003 | Validate numbered input | Syntax, range, overflow, and retry outcomes |
| MOV-FR-004 | Validate titles | Normalization, controls, Unicode, duplicates, reserved commands |
| MOV-FR-005 | Select rating | Exact menu mapping and invalid-value retry |
| MOV-FR-006 | Add movie | UUID preview, append, confirm/cancel, success |
| MOV-FR-007 | Select Edit/Delete target | One-based selection, dynamic bounds, Back |
| MOV-FR-008 | Build Edit draft | Multi-field draft, replacement, no-op, cancellation |
| MOV-FR-009 | Preview and confirm Edit | Complete state, deltas, atomic confirmed edit |
| MOV-FR-010 | Prepare Delete preview | Screening details, occupancy counts, preparation failure |
| MOV-FR-011 | Confirm and persist Delete | Childless/cascade paths, last movie, success summary |
| MOV-FR-012 | Confirm mutations | Y/N grammar, retry, preview-before-write |
| MOV-FR-013 | Preserve ordering | Append, stable edit, gap-closing delete, no reorder |
| MOV-FR-014 | Handle commands/cancellation | Global precedence, `/cancel`, `0`, draft discard |
| MOV-FR-015 | Handle storage/recovery failures | Recovery blocking and truthful failure status |
| MOV-FR-016 | Fail closed on I/O failure | Pre-commit preservation and post-commit durability |
| MOV-NFR-001 | Compatibility | Version-1 and legacy-ID preservation |
| MOV-NFR-002 | Data safety | No partial unconfirmed updates; recoverable confirmed delete |
| MOV-NFR-003 | Usability | Guided choices, actionable errors, destructive warning |
| MOV-NFR-004 | Testability | Observable outcomes and deterministic TDD seams |
| MOV-NFR-005 | Maintainability | Separated responsibilities and no speculative dependencies |

## TDD handoff

The TDD must translate this product behaviour into a design without changing it.
It must decide at least:

- public and internal interfaces for catalogue mutation, read-only occupancy,
  administrative clearing, and recovery coordination;
- complete-state validation and canonical catalogue serialization;
- generated-ID and deterministic test seams;
- the deletion journal's path, version 1 grammar, representation of a missing
  occupancy file, transaction states, write ordering, and durability rules;
- the recovery decision table, malformed/unrecoverable blocking behaviour, and
  idempotent retry rules; and
- deterministic fault injection for every atomic-update and recovery stage.

The TDD must not add a new product field, validation rule, command, persistence
format, dependency, or scope item without owner approval and a corresponding PRD
amendment.
