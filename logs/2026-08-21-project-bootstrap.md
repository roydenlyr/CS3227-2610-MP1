# Project Bootstrap Summary

## Scope

Established the approved Java/Maven project foundation only. No cinema feature,
domain model, persistence schema, or user-facing workflow was implemented.

## Owner decisions applied

- Java 25 LTS without preview features
- Maven coordinates `cinecli:cinecli:0.1.0-SNAPSHOT`
- Maven Wrapper pinned to Maven 3.9.16
- Maven distribution checksum verified and pinned
- JUnit 5 with pinned dependency and build-plugin versions
- `cinecli` package root
- Git repository initialized with `master` as the default branch
- Structured plain-text persistence reserved without choosing a schema
- External authoritative PDFs preserved in `../Resources/`
- IntelliJ metadata ignored without deletion

## Implementation assumptions

- The bootstrap entry point exits silently to avoid inventing product behavior.
- Mutable application data will eventually live under `data/runtime/`; its contents
  are ignored until a persistence format is approved.
- Maven wrapper scripts use explicit line-ending attributes for reliable Windows
  and Unix checkouts.
- Unix documentation invokes `mvnw` through `sh` because the executable Git mode
  cannot be recorded until the untracked bootstrap files are staged or committed.
- A task-specific temporary Maven cache was used for verification because the
  sandbox did not expose a writable default Maven user repository.
- The generated Maven Wrapper 3.3.4 Windows script required a narrowly scoped fix
  to distinguish a normal `.m2` directory from a symbolic link.

## Created foundation

- Root project instructions, readme, Git ignore rules, and line-ending rules
- Pinned Maven project and cross-platform Maven Wrapper scripts
- Minimal `cinecli.Main` entry point and `MainTest` JUnit smoke test
- Required user, developer, and reflection documents
- Task-log guidance and this bootstrap summary
- Reserved runtime-data directory and data-policy readme

## Verification

- `java --version`: Java 25.0.4 LTS
- `.\mvnw.cmd --version`: Maven 3.9.16 running on Java 25.0.4
- Fresh wrapper download: SHA-256 validation succeeded
- `.\mvnw.cmd clean verify`: build succeeded
- Surefire: 1 test run, 0 failures, 0 errors, 0 skipped
- `java -jar target\cinecli-0.1.0-SNAPSHOT.jar`: exited successfully
- JAR manifest: `Main-Class: cinecli.Main`, Java release 25
- Compiled class: major version 69, minor version 0 (non-preview Java 25)
- Git: initialized on `master`; `.idea/` and `target/` ignored
- Both external PDF SHA-256 hashes matched their pre-bootstrap values

## Issues encountered

The system had no `mvn` command. The IntelliJ-bundled Maven installation was used
once to generate the official wrapper. Initial wrapper setup attempts could not use
the sandbox's default Maven repository or network; using a task-specific temporary
cache and permitted dependency downloads resolved both issues. The generated
Windows script also indexed an absent symbolic-link target for a normal `.m2`
directory; its path check was corrected and verified with the normal default Maven
user home. The resulting project does not depend on IntelliJ's Maven installation.

## Deferred work

Architecture, cinema requirements, persistence format, sample data, additional
testing layers, static-analysis tooling, runtime logging, and production
dependencies remain deliberately undecided.
