# CineCLI Project Instructions

These instructions apply to the entire repository.

## Authoritative standards

Before architecture, design, implementation, refactoring, testing, or code review,
read both authoritative documents in the current workspace:

- `../Resources/CS2113 - Software Engineering for Self-Directed Learners [Printable Version for CS2113].pdf`
- `../Resources/Java coding standard.pdf`

Keep those PDFs unchanged and outside this repository. Refer to them instead of
copying their contents here. If they conflict, or a requested change conflicts with
them, report the conflict rather than choosing silently. The owner has resolved one
known ambiguity: the Java standard's written rule of 4 spaces for block indentation
and an additional 8 spaces for continuation indentation takes precedence over
conflicting rendered examples. Do not use tabs for Java indentation.

## Fixed owner decisions

- Build a Java CLI cinema kiosk and administration system, implementing only
  explicitly approved requirements.
- Use Java 25 LTS without preview features.
- Use Maven with coordinates `cinecli:cinecli:0.1.0-SNAPSHOT` and keep selected
  tool and dependency versions pinned.
- Use `cinecli` as the Java package root.
- Use JUnit 5 for tests.
- Use structured plain-text files for persistence. Do not introduce a database or
  decide a data schema without owner approval.
- Do not add production dependencies, frameworks, or libraries without owner
  approval.
- Use `master` as the repository's default branch.
- Preserve `docs/UserGuide.md`, `docs/DeveloperGuide.md`, `docs/Reflections.md`,
  and task-specific summaries under `logs/`.

## Engineering workflow

- Before grilling, designing, or implementing an admin-interface feature, read
  `docs/AdminInterfacePlan.md` as the approved cross-feature scope, sequence, and
  handoff reference.
- Inspect the relevant repository state before proposing or making changes.
- Distinguish explicit requirements, assumptions, and recommendations.
- For substantial changes, explain the task, ambiguities, approach, relevant
  standards, and tests before implementation; wait for approval unless the owner
  explicitly authorizes direct implementation.
- After approval, keep the change scoped, add appropriate tests, run relevant
  checks, review the result against the requirements and standards, and report
  failures or limitations honestly.
- Make reasonable routine and reversible implementation decisions without asking.
  Escalate decisions that materially affect product behavior, scope, architecture,
  persistence format, production dependencies, security, destructive operations,
  or an existing owner decision.

## Git branching and commits

- `master` is the stable integration and submission branch.
- Perform substantial feature, fix, and refactoring work on short-lived branches
  created from the latest approved `master`.
- For an approved substantial integration task, create sequential child feature
  branches from the latest integration-branch tip. Fast-forward each verified
  child into the integration branch with `--ff-only` before starting the next.
- Scope branches to user-visible features or coherent engineering tasks, not to
  individual classes or files.
- Codex may create branches and local commits only when explicitly authorized as
  part of a development task.
- When local commits are authorized, commit each completed logical unit atomically.
  Use a short, descriptive, imperative commit message.
- Before committing, run the relevant automated tests and review the diff for
  unrelated changes.
- Keep child-branch commits incremental, coherent, and green. Run the complete
  Maven verification and review the child diff before integration.
- Never commit failing changes merely to make progress unless the owner explicitly
  requests a work-in-progress commit.
- Unless explicitly authorized by the owner, Codex must not push, merge into
  `master`, rebase shared history, amend existing commits, force-push, delete
  branches, or perform destructive Git operations.
- After an approved committed task, leave the working tree clean and report the
  resulting branch, commits, and verification performed.

## Testing and coverage

- Design tests from requirements and risks. Cover happy paths, alternative valid
  paths, invalid inputs, retries, errors, exceptions, state transitions, and
  interactions between modules.
- Apply equivalence partitioning and boundary value analysis. Include values below,
  at, and above every relevant boundary, and test invalid inputs independently
  before combining them.
- Run `mvn verify` with the pinned coverage tool. Production code must retain 100%
  aggregate line and branch coverage, and every uncovered line or branch must be
  investigated. Coverage exclusions require owner approval.
- Inspect assertions and the coverage report. A 100% metric is evidence that code
  executed, not proof that requirements, edge cases, or failure behavior are
  tested well.
- For workflows with tentative state, verify that cancellation, role switching,
  input failure, EOF, and forced termination leave persistent state unchanged.

## Design and implementation rules

- Prefer the simplest maintainable design that satisfies approved requirements.
- Preserve clear responsibilities, separation of concerns, low coupling, high
  cohesion, and testability. Do not create speculative layers, abstractions,
  patterns, packages, or features.
- Keep CLI input/output separate from business rules when those rules are added.
- Validate assumptions against existing code and preserve established conventions.
- Avoid broad unrelated refactoring.
- Follow the authoritative Java naming, formatting, import, documentation, and
  readability rules, including the explicit 4/+8 indentation decision.
- Treat warnings, tests, and coverage as engineering evidence, not substitutes for
  review or clear requirements.
- Never commit generated build output, mutable runtime data, machine-specific IDE
  metadata, secrets, or the external standards PDFs.
