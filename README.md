# CineCLI

CineCLI is planned as a Java command-line cinema kiosk and administration system.
The repository currently contains only the verified project foundation; cinema
functionality has not been implemented.

## Prerequisite

- Java 25 LTS

The Maven Wrapper downloads and uses the Maven version pinned by this project.

## Build and test

On Windows:

```powershell
.\mvnw.cmd clean verify
```

On macOS or Linux:

```shell
sh ./mvnw clean verify
```

## Run

After a successful build:

```powershell
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
```

The bootstrap entry point intentionally exits without starting a cinema workflow.

## Documentation

- [User Guide](docs/UserGuide.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Reflections](docs/Reflections.md)
- [Task logs](logs/README.md)
