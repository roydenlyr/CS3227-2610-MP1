# Customer Catalog Vertical Slice - Interaction Summary

## Initial request

The user described CineCLI as having separate customer and administrator
interfaces. The broader customer journey included choosing a movie and screening,
selecting seats and optional snacks, checking promotional discounts, and viewing a
checkout summary. The administrator journey included managing movies, screenings,
snacks, and prices.

The user asked to start with the customer interface. The immediate workflow was a
welcome screen containing `Press ENTER to proceed`, followed by movie titles,
screening times, and content ratings (`PG13`, `M18`, or `R21`). Because movie
details required persistence, the user first asked Codex for an opinion on the
flow rather than authorizing the whole product journey.

## Clarifications and planning

Codex inspected the repository and found a foundation-only Maven application with
an empty `Main.main`, one smoke test, no domain or persistence implementation, and
the local branch named `main`. Codex also reviewed the required course and Java
standards.

The work was organized as a five-step internal plan: inspect the repository and
standards, define the catalog format and responsibilities, implement the vertical
slice, add tests and documentation, and perform final verification and review.
Read-only parallel reviews also produced repository, schema, testing,
documentation, standards, and packaging recommendations.

No separate clarification question was asked before implementation after the user
provided the detailed owner decisions below. During final self-review, Codex
recognized that the exact persistence schema had not been explicitly approved and
asked the user to ratify it. The user did not answer that question in this
conversation before requesting this interaction summary.

## Decisions made by me

The user explicitly approved the proposed customer flow and the first customer
vertical slice, then authorized direct implementation, testing, and self-review.
The user decided that:

- persistence would use structured plain text, not a database;
- movies and screenings would have stable IDs;
- a movie would contain only the title, rating, and available screenings needed
  for this milestone;
- every screening would contain both a date and a time;
- ratings would be display-only, with no age enforcement;
- fictional seed data would be bundled so a fresh JAR had movies on first launch;
- missing runtime data would initialize from the bundled defaults;
- a valid empty catalog would show a friendly no-movies message;
- malformed data would fail clearly without a crash or invented values;
- only customer mode was in scope; administration, seat selection, snacks,
  discounts, and checkout were deferred; and
- the repository would use `master` for the submission requirement.

The user instructed Codex to escalate decisions that materially changed product
behavior, architecture, persistence format, dependencies, or an existing owner
decision.

## Codex proposals and assumptions

Codex selected a candidate versioned UTF-8 TSV format consisting of one runtime
file, `data/runtime/catalog.tsv`, and one bundled seed resource. The proposed
records were:

```text
CINECLI-CATALOG<TAB>1
MOVIE<TAB>movieId<TAB>title<TAB>rating
SCREENING<TAB>screeningId<TAB>movieId<TAB>yyyy-MM-dd<TAB>HH:mm
```

Codex also proposed a restricted ASCII ID grammar, unique IDs, the closed rating
set `PG13`/`M18`/`R21`, strict dates and times, referential integrity, preserved
file order, and a header-only file as the valid empty catalog. It assumed that a
movie could have no screenings, titles could repeat, screenings could share a
date and time if their IDs differed, and fixed seed dates could eventually be in
the past.

The proposed responsibility split used immutable movie, screening, and rating
types; a pure parser; storage responsible only for missing-file initialization and
reading; a customer UI for input/output; and a small application coordinator. No
database, production dependency, writer API, repository abstraction, or
administrator feature was proposed for this slice.

## Follow-up prompts and corrections

The main follow-up prompt was the user's detailed authorization and owner-decision
list. Codex later asked one material clarification question: whether the user
approved the exact version 1 TSV schema and validation contract above. That
question was prompted by self-review and remained unanswered.

The final follow-up asked for this conversation-only interaction log, explicitly
forbidding reconstruction from other conversations or rewriting earlier choices
to match later implementation.

## Implementation performed

Before discovering the missing schema approval, Codex implemented the candidate
vertical slice. It added immutable catalog model types, strict parsing and
validation, missing-file seed initialization, fictional bundled data, the welcome
and catalog UI, application coordination, and real `Main` wiring with UTF-8
streams. The UI displayed movie titles, ratings, and formatted screening dates and
times, plus friendly empty and error messages.

Codex added parser, storage, application, and entry-point tests and updated the
README, user guide, developer guide, data documentation, reflections, and a
task-oriented implementation summary. It locally renamed `main` to `master`,
removed the stale `origin/main` upstream association, and later recorded the Unix
executable bit for `mvnw`. It did not commit, push, or implement any subsequent
customer or administrator feature.

## Testing and verification

The baseline `clean verify` succeeded with the original single test. An
implementation build later succeeded with 21 tests and no failures, errors, or
skips. Tests covered valid and empty catalogs, IDs and associations, malformed
records, ratings, dates and times, UTF-8 text, seed initialization, preservation
of existing files, idempotence, UI ordering, friendly empty output, and graceful
errors. A later storage-level UTF-8 test was added but was not included in a final
clean verification run.

Codex inspected the built JAR's manifest, entry point, and bundled seed. Isolated
JAR smoke runs exercised fresh initialization, a valid empty catalog, and a
malformed catalog; the malformed file's hash remained unchanged and no stack
trace was shown. A packaging review subsequently noted that this JAR and its test
reports predated the latest source edits, so those packaged results were stale.

Static review found no over-120-character Java lines, indentation tabs, or
trailing-whitespace errors. Review corrections consolidated import blocks,
renamed an imperative boolean method and singular collection variables, added
Unix run commands, and added byte-level UTF-8 coverage. No coverage report from a
pinned coverage tool was produced in this conversation.

## Issues / mistakes / lessons

The principal mistake was selecting and implementing the exact TSV persistence
contract before obtaining the owner's required schema approval. Codex caught this
during self-review and paused completion to request ratification rather than
claiming the slice was finished.

The original smoke test called the now-interactive entry point without input and
blocked. Codex stopped that run and replaced the test with injected input and
output. Maven initially could not create its configured local repository inside
the sandbox, so verification used the approved external execution path. The JDK
`jar` tool was also absent from `PATH`, so Codex used its explicit installed path.

Further review found that `ISO_LOCAL_DATE` accepted expanded signed years despite
documentation promising exactly four-digit `yyyy-MM-dd`, and that first-run seed
copying wrote directly to the final path rather than using a temporary file and
atomic move. These findings were not corrected before the approval pause.

## Deferred or unresolved items

- Owner ratification or revision of the proposed version 1 TSV schema remains
  required.
- Exact four-digit year validation and safer atomic first-run initialization
  remain review findings to resolve after schema approval.
- A final `clean verify`, coverage inspection, JAR-content check, and fresh
  packaged smoke test against the latest edits were not completed.
- Only the local repository was changed to `master`; the remote still exposed
  `origin/main`. No remote branch or hosted default-branch change was authorized.
- Administration, movie selection, seats, snacks, discounts, checkout, and all
  other later workflow steps remained outside this conversation's implemented
  scope.
