# User Guide

## Project status

CineCLI currently provides a customer workflow from the welcome screen to the
movie catalog, screening selection, terminal seat selection, demographic ticket
selection, optional snacks and combos, an optional promo code, and an itemized
bill. Each movie displays its content rating and lettered screening dates and
times.

Seat occupancy is stored temporarily until booking records become the source of
truth. Ticket assignments, snack and combo choices, promo codes, and bills are
session-only. Administration, payment, and complete booking records are not part
of this milestone.

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

## Choose a ticket type

After seats are confirmed, CineCLI displays the fixed ticket menu:

```text
Ticket Types
1. Adult - S$11.00
2. Senior - S$4.50
3. Student - S$7.00
```

CineCLI requests one ticket type for each confirmed seat, in coordinate order. For
example, seats `G4 G5` are prompted as `G4` followed by `G5`, even if they were
entered in a different order. Enter `1`, `2`, or `3` at each prompt. Invalid,
blank, or unavailable numbers are rejected, and CineCLI asks again for the same
seat before moving on.

## Choose a snack or combo

After every confirmed seat has a ticket type, CineCLI displays this fixed menu:

```text
Snack and Combo Menu
Snacks:
1. Popcorn - S$5.00
2. Nachos - S$6.00
3. Soft Drink - S$3.00
Combos:
4. Popcorn Combo (Popcorn + Soft Drink) - S$7.00
5. Nachos Combo (Nachos + Soft Drink) - S$8.00
0. Finish selection (or skip if none selected)
```

1. Enter an item number from `1` through `5`.
2. Enter a positive whole-number quantity when prompted. CineCLI acknowledges the
   item, quantity, and unit price.
3. Repeat the item and quantity steps to choose more a la carte items or combos.
4. Enter `0` at the item prompt to finish. CineCLI displays all selected items in
   their original selection order. Entering `0` before adding an item skips snacks
   and combos.

Choosing the same item again replaces its earlier quantity instead of creating a
duplicate line. CineCLI rejects malformed or unavailable item numbers and asks for
another item. It rejects zero, negative, fractional, nonnumeric, and out-of-range
quantities, then asks again for the quantity of the same item.

Displayed prices beside selections remain unit prices. Quantities contribute to
the snack subtotal in the final bill. Selections are not persisted or attached to
a booking record.

## Apply a promo code and review the bill

After snack selection, CineCLI accepts one optional promo code:

- `CS2103` applies 20% off the complete ticket-and-snack subtotal.
- `CS3227` applies 99% off the complete ticket-and-snack subtotal.

Promo codes are case-insensitive, and surrounding whitespace is ignored. Press
ENTER on a blank line to skip the promotion. A nonblank unsupported code is
rejected and the same prompt is shown again. Only one code can be applied; promo
codes cannot be stacked.

CineCLI then displays every ticket and snack selection, separate ticket and snack
subtotals, the pre-discount subtotal, any promotion and discount, and the payable
total. For example:

```text
Bill Summary
Tickets:
- G4: Adult - S$11.00
- G5: Senior - S$4.50
Ticket subtotal: S$15.50
Snacks and Combos:
- 2 x Popcorn Combo (Popcorn + Soft Drink) - S$7.00 each
- 3 x Nachos - S$6.00 each
Snack subtotal: S$32.00
Subtotal: S$47.50
Promo code: CS2103 (20% off)
Discount: -S$9.50
Total: S$38.00
```

All calculations use exact Singapore cents. If applying a percentage produces a
fraction of a cent, the final payable total is rounded to the nearest cent, with a
half cent rounded up. For example, `CS3227` reduces a S$4.50 Senior ticket to
S$0.05. CineCLI displays the bill but does not process payment.

## Runtime data

On first launch from a working directory without `data/runtime/catalog.tsv`,
CineCLI creates that file from fictional defaults bundled in the JAR. Later
launches use the existing runtime file without replacing it.

When seat selection is first opened, a missing `data/runtime/seats.tsv` is created
as an empty, versioned seat occupancy file. Confirmed selections are stored by
screening ID and seat coordinate. This file is temporary and will be replaced by
booking-owned seat allocations when booking persistence is implemented.

Ticket assignments, snack and combo choices, promo codes, and bills do not create
or update a runtime data file.

If either runtime file is malformed, CineCLI displays a clear error and does not
use partial or invented data. A malformed file is not overwritten automatically.
Restore valid data, or remove a malformed catalog if the bundled catalog defaults
should be recreated on the next launch.
