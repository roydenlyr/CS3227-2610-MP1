# CineCLI

CineCLI is a Java command-line cinema kiosk and administration system under
incremental development. The current milestone implements the customer welcome
screen, a persisted movie catalog, screening selection, and a fixed terminal seat
map with temporary persisted occupancy.

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

On macOS or Linux:

```shell
java -jar target/cinecli-0.1.0-SNAPSHOT.jar
```

The application displays `Press ENTER to proceed`. Press ENTER to load the movie
catalog. Select a screening by combining its movie number and timing letter, such
as `3B`. Select seats using coordinates such as `G4 G5`, then confirm with `Y`.

On first launch, a missing `data/runtime/catalog.tsv` is initialized from fictional
defaults bundled inside the JAR. A missing `data/runtime/seats.tsv` is initialized
when seat selection is first opened.

Seat occupancy is a temporary source of truth until booking persistence is
implemented. Administration, snacks, discounts, checkout, and complete booking
records are not yet implemented.

## Documentation

- [User Guide](docs/UserGuide.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Reflections](docs/Reflections.md)
- [Task logs](logs/README.md)
