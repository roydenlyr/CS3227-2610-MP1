# User Guide

## Project status

CineCLI currently provides a customer workflow from the welcome screen to the
movie catalog, screening selection, terminal seat selection, and an optional
snack or combo choice. Each movie displays its content rating and lettered
screening dates and times.

Seat occupancy is stored temporarily until booking records become the source of
truth. Snack and combo choices are session-only. Administration, quantities,
discounts, checkout, payment, and complete booking records are not part of this
milestone.

## Prerequisite

Install Java 25 LTS and ensure `java --version` reports Java 25.

## Build and run

From the project root on Windows:

```powershell
.\mvnw.cmd clean verify
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
```

On macOS or Linux, use:

```shell
sh ./mvnw clean verify
java -jar target/cinecli-0.1.0-SNAPSHOT.jar
```

## View the movie catalog

1. Start CineCLI.
2. At the welcome screen, the application displays:

   ```text
   Welcome to CineCLI
   Press ENTER to proceed
   ```

3. Press ENTER. CineCLI displays the movie catalog in persisted order. For
   example:

   ```text
   Movie Catalog
   1. Orbit of Echoes
      Rating: PG13
      Screenings:
      A. 29 Aug 2026, 13:30
      B. 29 Aug 2026, 18:00
   ```

If the catalog contains no movies, CineCLI displays `No movies are currently
available.`

## Select a screening and seats

1. Combine the movie number and timing letter to select a screening. For example,
   enter `3B` for the second timing of the third movie. Selection codes are
   case-insensitive.
2. CineCLI displays the fixed seating layout. `G` is closest to `SCREEN`, and the
   numeric axis is below row `A`, furthest from the screen:

   ```text
                                  SCREEN
       ============================================================
   G    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   F    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   E    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   D    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   C    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   B    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
   A    O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O  O
        1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20
   ```

3. `O` represents an available seat. `X` represents either a previously confirmed
   seat or a tentative selection in the current session.
4. Enter one or more coordinates separated by spaces, such as `G4 G5`.
5. Review the updated map and enter `Y` to confirm. Enter `N` to clear the
   tentative selection and choose again. Enter `CANCEL` at the coordinate prompt
   to leave without confirming seats.

Confirmed selections remain `X` on later runs. CineCLI rejects malformed,
duplicate, or already-taken coordinates and asks for another selection.

## Choose a snack or combo

After CineCLI successfully confirms the seats, it displays this fixed menu:

```text
Snack and Combo Menu
Snacks:
1. Popcorn - S$5.00
2. Nachos - S$6.00
3. Soft Drink - S$3.00
Combos:
4. Popcorn Combo (Popcorn + Soft Drink) - S$7.00
5. Nachos Combo (Nachos + Soft Drink) - S$8.00
0. Skip snacks and combos
```

Enter one item number from `1` through `5`, or enter `0` to continue without a
snack or combo. CineCLI rejects malformed or unavailable item numbers and asks
again. A valid choice is acknowledged with its exact price.

This milestone supports one optional choice per run. The choice is not persisted
and is not yet included in a booking, total, checkout, or payment flow.

## Runtime data

On first launch from a working directory without `data/runtime/catalog.tsv`,
CineCLI creates that file from fictional defaults bundled in the JAR. Later
launches use the existing runtime file without replacing it.

When seat selection is first opened, a missing `data/runtime/seats.tsv` is created
as an empty, versioned seat occupancy file. Confirmed selections are stored by
screening ID and seat coordinate. This file is temporary and will be replaced by
booking-owned seat allocations when booking persistence is implemented.

Snack and combo choices do not create or update a runtime data file.

If either runtime file is malformed, CineCLI displays a clear error and does not
use partial or invented data. A malformed file is not overwritten automatically.
Restore valid data, or remove a malformed catalog if the bundled catalog defaults
should be recreated on the next launch.
