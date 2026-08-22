# Developer Guide

## Project status

This repository contains the project foundation only. Architecture and cinema
functionality will be designed incrementally from approved requirements.

## Toolchain

- Java 25 LTS, without preview features
- Maven 3.9.16 through the Maven Wrapper
- JUnit Jupiter 5.14.4
- Java package root `cinecli`

All selected build-plugin and dependency versions are pinned in `pom.xml`.

## Authoritative standards

The two authoritative PDFs remain outside this repository in the current
workspace's `../Resources/` directory. Read `AGENTS.md` and both PDFs before design,
implementation, refactoring, testing, or review work.

## Build and verification

On Windows:

```powershell
java --version
.\mvnw.cmd --version
.\mvnw.cmd clean verify
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
```

Use `sh ./mvnw` instead of `.\mvnw.cmd` on macOS or Linux.

## Source layout

- Production Java: `src/main/java/cinecli/`
- Test Java: `src/test/java/cinecli/`
- Mutable local data: `data/runtime/`
- Future test fixtures: `src/test/resources/`, created only when needed

No architectural layers or persistence schemas have been selected. Add packages
only when they have an approved, concrete responsibility.

## Testing

JUnit tests must be deterministic and should test observable behavior. Run the
complete build with `clean verify` before considering an implementation task
complete.

## Persistence

Persistence will use structured UTF-8 plain-text files. The file format, schema,
update strategy, and sample-data policy remain deferred until their requirements
are approved.
