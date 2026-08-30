# Snack and Combo Selection with Quantities - Interaction Summary

## Initial request

The user first asked for a simple list of snacks and combos, including prices,
that customers could choose from after selecting their seats. The user also asked
Codex to create meaningful commits during the work.

This was a direct implementation request. It did not specify the exact products,
currency, number of selections, quantities, skip behavior, totals, or persistence.

## Clarifications and planning

Codex did not ask the user any clarification questions before implementing the
initial version or the later quantity enhancement.

For the initial version, Codex inspected the existing post-seat workflow, Git
state, tests, documentation, and the required engineering and Java standards. It
identified successful seat confirmation and persistence as the boundary after
which the snack menu should appear. It planned a fixed in-memory menu, CLI and
application tests, documentation updates, verification, and separate meaningful
commits.

After the quantity follow-up, Codex created a four-step working plan: inspect the
existing snack flow and define bounded behavior; implement quantities and repeated
selection with tests; update documentation and commit logical units; then run the
clean build, packaged-JAR smoke test, and final review. Read-only reviewers were
also used to audit requirements, implementation seams, tests, documentation, Git
state, and build evidence.

## Decisions made by me

- The user explicitly requested a priced snack and combo choice after seat
  selection.
- The user explicitly authorized meaningful local commits as part of the task.
- In the follow-up, the user explicitly rejected the single-choice interaction as
  too restrictive and required quantities for both a la carte items and combos.
- The user explicitly required customers to be able to choose more than one menu
  option.
- At the end, the user requested this interaction-only summary and instructed
  Codex not to reconstruct history from other conversations or unrelated work.

The user did not explicitly choose the exact menu, prices, currency, duplicate-item
behavior, finish command, persistence behavior, or whether totals should be shown.

## Codex proposals and assumptions

For the initial implementation, Codex made the following bounded assumptions:

- The fixed menu would contain Popcorn at S$5.00, Nachos at S$6.00, Soft Drink at
  S$3.00, Popcorn Combo at S$7.00, and Nachos Combo at S$8.00.
- The menu would appear only after seats were successfully confirmed and persisted,
  not after a tentative seat choice or a failed/cancelled confirmation.
- The first version would accept one numbered item or `0` to skip.
- The choice would remain in memory for the current run. Quantities, totals,
  checkout, payment, inventory, booking integration, and snack persistence would
  remain outside scope.

For the follow-up enhancement, Codex proposed and implemented these additional
assumptions:

- Quantities must be positive whole numbers within the Java integer range.
- The item prompt repeats so different a la carte items and combos can be selected.
- `0` finishes a non-empty selection and still acts as skip when entered before any
  item is added.
- Selecting the same item again replaces its earlier quantity instead of adding a
  duplicate line or accumulating the quantities.
- Different items remain in the order in which they were first selected.
- The final summary shows quantities and exact unit prices, but no subtotal or
  order total.

These were Codex design assumptions made to keep the feature small; the user did
not separately approve each one in an additional message.

## Follow-up prompts and corrections

After the initial single-choice menu had been implemented, the user said the menu
was too restrictive. The user requested quantity entry for either a la carte items
or combos and requested selection of more than one option. This changed the earlier
single-choice/no-quantity boundary rather than merely adding documentation.

During review of the enhancement, reviewers noted that the first quantity prompt
did not state the positive-whole-number rule and that the `0` menu text did not
make the immediate-skip behavior obvious. They also noted that the first parser
rejected values such as `01` and `+1` while its error only said "positive whole
number." Codex corrected all three points before committing: the prompts became
explicit, leading-zero and optional-plus forms were accepted, and invalid values
reported the exact supported integer range.

The final prompt in this conversation requested this chronological development
interaction summary in `logs/`.

## Implementation performed

The initial implementation:

- Added a fixed `SnackMenuItem` model containing menu numbers, categories, names,
  and prices stored as integer cents.
- Added the snack and combo menu after successful seat confirmation, with item
  parsing, retry behavior, and `0` skip handling.
- Updated the customer UI, application workflow, model/UI/application/wiring tests,
  README, user guide, developer guide, reflections, and a task summary log.
- Created commits `bc27872` (`Add snack and combo selection`) and `faeacb6`
  (`Record snack selection verification`).

The follow-up implementation:

- Added immutable `SnackSelection` data containing a menu item and validated
  quantity.
- Changed the application from returning after one valid item to maintaining an
  insertion-ordered, session-local set of item selections.
- Added separate quantity prompting and retry behavior, add/update acknowledgements,
  repeated item selection, immediate skip, explicit finish, and a final multi-item
  summary.
- Added tests for multiple distinct items, replacement of a repeated item's
  quantity, malformed/non-positive/overflow quantities, session isolation, exact
  prices, and absence of snack persistence or totals.
- Updated the README, guides, reflections, and a second task summary log without
  rewriting the earlier single-choice log as if quantities had always existed.
- Created commits `4efe929` (`Support snack quantities and multiple choices`) and
  `7d2968c` (`Document multiple snack quantities`).

## Testing and verification

- The initial snack implementation finished with 69 passing JUnit tests.
- Before the follow-up change, Codex reran that 69-test suite as a clean baseline.
- Focused model, UI, application, and real-wiring tests passed during development.
- An intermediate full verification passed 78 tests before the final
  session-isolation regression test was added.
- The final `.\mvnw.cmd clean verify` run passed 79 tests with no failures, errors,
  or skipped tests and built the executable JAR.
- `git diff --check`, Java tab scans, and Java line-length scans found no errors.
- Codex inspected the JAR for the application, UI, menu, quantity model, and bundled
  catalog classes/resources.
- A packaged-JAR smoke test ran from an empty temporary working directory. It
  confirmed a seat, selected an a la carte item and a combo with different
  quantities, replaced one quantity, finished the selection, displayed the exact
  unit prices, and created only the expected catalog and seat files.
- Read-only implementation, requirements, documentation, and Git/build reviews
  reported no remaining blockers after the corrections.

## Issues / mistakes / lessons

- The initial single-choice design was a Codex scope assumption, not a user-selected
  product rule. The follow-up showed that this assumption was too restrictive and
  had to be replaced with an order-building loop.
- Because Codex asked no upfront clarification questions, it had to label the menu,
  prices, currency, skip behavior, and session-only boundary as assumptions rather
  than user decisions.
- Quantity validation initially described its accepted input imprecisely. Review
  caught the mismatch before commit, and the parser, prompt, error, and tests were
  aligned.
- Current documentation initially still described one choice and no quantities
  while the enhancement was under development. It was updated and reviewed before
  the documentation commit; the earlier task log was deliberately preserved as a
  historical record.
- The feature branch was already stacked on the unmerged seat-selection branch
  because the snack flow depended on successful seat confirmation. Codex disclosed
  this Git-policy tension and did not merge, rebase, push, or alter `master`.

## Deferred or unresolved items

- Subtotals, order totals, inventory, persisted snack selections, booking
  integration, checkout, and payment were deliberately deferred.
- No snack persistence schema or production dependency was introduced.
- Integration of the stacked feature branch into the stable branch was not
  performed in this conversation.
- The requested implementation tests and both factual task logs were created; no
  expected test or task log was left outstanding at the feature handoff.
