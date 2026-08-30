# Bootstrap and Governance - Interaction Summary

## Initial request

The owner introduced CineCLI as a Java CLI cinema kiosk and administration system.
The initial scope was deliberately high level: customer movie/showtime/seat/snack/
promotion/checkout flows and administrator management capabilities. The owner
explicitly prohibited assuming unspecified behavior and required the two external
standards PDFs to govern future work.

The first requested task was planning only: inspect the project and standards,
summarize actionable rules and conflicts, and propose a root `AGENTS.md` without
creating files or implementing cinema functionality.

## Clarifications and planning

Codex inspected the initially minimal project and found that the authoritative PDFs
were in sibling `../Resources/`, not a project `resource/` directory. It read the
documents, identified the Java indentation example conflict, and proposed a concise
`AGENTS.md` plus a Java 25/Maven bootstrap plan.

Codex did not ask broad product-design questions. It instead identified decisions
that materially affected the foundation: Java and Maven versions, Maven
coordinates, package root, testing, persistence, Git setup, external PDF location,
IDE handling, and the required logging format. No PRD, technical design document,
or requirements-to-tests mapping was produced because no cinema feature had yet
been approved.

## Decisions made by me

The owner decided or approved:

- written 4/+8 Java indentation overrides conflicting rendered examples;
- Java 25 LTS, Maven, package root `cinecli`, JUnit 5, and pinned versions;
- structured plain-text persistence, no preview features, and no unapproved
  frameworks, databases, or production dependencies;
- Maven coordinates `cinecli:cinecli:0.1.0-SNAPSHOT`;
- Git initialization on `master`, external standards PDFs left unchanged outside
  the repository, ignored IntelliJ metadata, and task-specific summary logs;
- the bootstrap plan and direct foundation implementation, while continuing to
  defer cinema behavior and persistence schemas;
- later, explicit branching and atomic-commit rules for `AGENTS.md`.

The owner subsequently replaced the effective `AGENTS.md` instructions in this
conversation. The replacement added the `docs/AdminInterfacePlan.md` prerequisite
for administrator work, sequential child-branch integration for approved
integration tasks, and a 100% aggregate line/branch coverage requirement.

## Codex proposals and assumptions

Codex proposed a minimal Maven project with no production dependencies, a no-op
`cinecli.Main`, one JUnit smoke test, root documentation, `data/runtime/`, a Maven
Wrapper, and a targeted `.gitignore`. The entry point was intentionally silent to
avoid inventing customer behavior.

Codex proposed using short-lived task branches and atomic commits only when the
owner explicitly authorizes local Git operations. For the bootstrap it assumed a
temporary Maven cache could be used when the sandbox prevented normal repository
access.

## Follow-up prompts and corrections

The owner first added fixed technology and persistence decisions, then requested a
bootstrap planning pass before approving implementation. After the bootstrap, the
owner requested a dedicated Git branching/commit section in `AGENTS.md`.

The owner later supplied a replacement instruction set that extended the prior
`AGENTS.md` rules. Finally, the owner asked for a development interaction log. An
initial log mistakenly summarized archived work outside this chat. The owner
corrected that request to require a summary of this conversation only; this file is
the correction.

## Implementation performed

After approval, Codex created the initial project foundation: `AGENTS.md`, Maven
configuration and wrapper, `.gitignore`, `.gitattributes`, source/test placeholders,
required guides, data/log directories, and Git repository metadata on `master`.
It also added the requested Git workflow section to `AGENTS.md` and task-summary
logs for those changes.

No cinema feature, persistence schema, database, framework, or production
dependency was implemented in this conversation. The later replacement instructions
were supplied as conversation guidance; this task did not implement their new
administrator-plan or coverage-tool requirements.

## Testing and verification

Bootstrap verification confirmed Java 25.0.4, Maven Wrapper 3.9.16, Java 25
non-preview class files, one passing JUnit test, a successful `clean verify`, and a
successfully launched executable JAR. The external PDF hashes were checked before
and after bootstrap work.

Documentation-only Git-policy changes were checked with `git diff --check` and a
Markdown whitespace scan. No branch or commit was created for that documentation
task because it was not explicitly authorized. This corrected log was reviewed for
scope, tabs, and trailing whitespace; no application test is relevant to it.

## Issues / mistakes / lessons

The environment had no system `mvn`. Maven wrapper generation initially failed due
to an unwritable sandbox Maven repository and blocked network access; a temporary
cache and permitted downloads resolved it. The generated Windows wrapper also had
a normal-directory/symbolic-link handling defect. A first narrow fix was incomplete;
the final check used the directory link type and the wrapper then worked normally.
The wrapper checksum was added only after a review identified that omission.

The earlier broad interaction-history log was a scope mistake because it used
archived work from outside this chat. It is deliberately removed and replaced by
this conversation-only record.

## Deferred or unresolved items

At the end of this conversation, cinema product requirements, detailed architecture,
plain-text schemas, sample-data policy, and further customer/admin functionality
remain outside the original bootstrap scope. The later instruction replacement
introduces coverage-tool and administrator-planning obligations that were not
implemented by a separate authorized task here.

No local commit was made for this log, and no push, merge, rebase, branch deletion,
or other destructive Git operation occurred.
