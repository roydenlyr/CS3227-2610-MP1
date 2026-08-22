# User Guide

## Project status

CineCLI currently contains a buildable application entry point only. Customer and
administrator cinema workflows have not yet been implemented.

## Prerequisite

Install Java 25 LTS and ensure `java --version` reports Java 25.

## Build and run

From the project root on Windows:

```powershell
.\mvnw.cmd clean verify
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
```

Use `sh ./mvnw` instead of `.\mvnw.cmd` on macOS or Linux.

The bootstrap application exits successfully without displaying a cinema menu.
Usage instructions will be added when user-facing functionality is approved and
implemented.
