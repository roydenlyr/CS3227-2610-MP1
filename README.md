# CineCLI

CineCLI is a Java command-line cinema kiosk and administration system with
Customer Mode and Administrator Mode. Customers choose a screening, tentative
seats, ticket types, snacks or combos, and an optional promotion before receiving
a bill. Administrators manage movies, screenings, ticket and snack/combo prices,
and percentage promotions.

## Prerequisite

- Java 25 LTS

## Run the packaged application

From the project root, run the user-facing release JAR:

On Windows:

```powershell
java -jar release\cinecli.jar
```

On macOS or Linux:

```shell
java -jar release/cinecli.jar
```

Run CineCLI from the project root because it stores runtime data relative to the
process working directory.

## Build and test from source

The Maven Wrapper downloads and uses the Maven version pinned by this project.
It produces the development/build artifact
`target/cinecli-0.1.0-SNAPSHOT.jar`; this is distinct from the normal packaged
release at `release/cinecli.jar`.

On Windows:

```powershell
.\mvnw.cmd clean verify
```

On macOS or Linux:

```shell
sh ./mvnw clean verify
```

## Run the Maven-built development artifact

After a successful build:

```powershell
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
```

On macOS or Linux:

```shell
java -jar target/cinecli-0.1.0-SNAPSHOT.jar
```

The application starts in Customer Mode and displays `Press ENTER to proceed`.
Press ENTER to load the movie catalog. Select a screening by combining its movie
number and timing letter, such as `3B`, then select seats using coordinates such as
`G4 G5` and confirm with `Y`. Choose one ticket type for each selected seat, then
choose snacks or combos by menu number and positive whole-number quantity. Enter
`0` to finish snack selection (or skip it), then enter an available promotion code
or press ENTER to skip it. The menus show the current administrator-configured
prices and promotions.

Enter `/admin` at any prompt to open Administrator Mode. Its Movie Management,
Screening Management, and Pricing and Promotions Management workflows preview
every addition, edit, or deletion and save it only after confirmation with `Y`.
Enter `/customer` to start a new Customer Mode session or `/exit` to exit CineCLI.
See the User Guide for the complete role workflows and cancellation rules.

Runtime data is stored relative to the directory from which CineCLI is started:
`data/runtime/catalog.tsv` stores the catalog and administrator movie and screening
changes; `data/runtime/pricing.tsv` stores ticket and snack/combo prices and
promotions; and `data/runtime/seats.tsv` stores confirmed seat occupancy. Missing
catalog and pricing files are initialized from bundled defaults. A missing seat file
is treated as empty and is created only after successful final seat confirmation.
Screening deletion and movie deletion with child screenings use a recovery journal
in `data/runtime/` to protect their coordinated catalog and seat-occupancy updates.

Confirmed seats remain unavailable across later runs, but seat occupancy is only a
temporary source of truth until booking persistence is implemented. Ticket
assignments, snack and combo selections, applied promotion codes, and bills are
session-only. Payment, complete booking records, cross-process locking, and seat
reservations are deferred.

## Documentation

- [User Guide](docs/UserGuide.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Reflections](docs/Reflections.md)
- [Task logs](logs/README.md)
