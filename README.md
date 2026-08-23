# CineCLI

CineCLI is a Java command-line cinema kiosk and administration system under
incremental development. The current milestone implements the customer welcome
screen, a persisted movie catalog, screening selection, and a fixed terminal seat
map with temporary persisted occupancy. Customers assign an Adult, Senior, or
Student ticket to each confirmed seat, optionally choose snacks and combos, enter
one optional promo code, and receive an itemized bill.

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
as `3B`. Select seats using coordinates such as `G4 G5` and confirm with `Y`.
Choose one ticket type for each confirmed seat, then choose a snack or combo by its
menu number and enter a positive whole-number quantity. Repeat for each option,
then enter `0` to finish. Entering `0` before adding an item skips snacks and
combos. Choosing the same option again replaces its earlier quantity. At the promo
prompt, enter `CS2103`, `CS3227`, or press ENTER to skip.

Adult tickets cost S$11.00, Senior tickets cost S$4.50, and Student tickets cost
S$7.00. The fixed snack menu offers Popcorn for S$5.00, Nachos for S$6.00, a Soft
Drink for S$3.00, a Popcorn Combo for S$7.00, and a Nachos Combo for S$8.00.
`CS2103` discounts the complete ticket-and-snack subtotal by 20%; `CS3227`
discounts it by 99%. The final bill shows ticket and snack subtotals, the applied
discount, and the payable total.

On first launch, a missing `data/runtime/catalog.tsv` is initialized from fictional
defaults bundled inside the JAR. A missing `data/runtime/seats.tsv` is initialized
when seat selection is first opened.

Seat occupancy is a temporary source of truth until booking persistence is
implemented. Ticket assignments, snack choices, promo codes, and bills are
session-only. Administration, payment, and complete booking records are not yet
implemented.

## Documentation

- [User Guide](docs/UserGuide.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Reflections](docs/Reflections.md)
- [Task logs](logs/README.md)
