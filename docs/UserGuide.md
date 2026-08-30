# User Guide

## Project status

CineCLI currently provides a customer workflow from the welcome screen to the
movie catalog, screening selection, terminal seat selection, demographic ticket
selection, optional snacks and combos, an optional promo code, and an itemized
bill. Each movie displays its content rating and lettered screening dates and
times.

Seat occupancy is stored temporarily until booking records become the source of
truth. Global ticket, snack/combo, and promotion pricing is stored separately in
runtime data. Ticket assignments, snack and combo choices, applied promo codes,
and bills remain session-only price snapshots. CineCLI also provides an
unauthenticated administrator interface for movie, screening, and pricing/
promotion management. Payment and complete booking records are not part of this
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
5. Review the updated map and enter `Y` to keep the selection tentative while
   you choose ticket types, snacks, and an optional promotion. Enter `N` to
   clear the tentative selection and choose again. Enter `CANCEL` at the
   coordinate prompt to leave without confirming seats.

The selected seats are not written at this point. CineCLI rejects malformed,
duplicate, or already-taken coordinates and asks for another selection.

## Choose a ticket type

After the tentative seats are selected, CineCLI displays the fixed ticket
identities with the prices loaded for the current session. A new runtime data directory receives the
following seeded prices:

```text
Ticket Types
1. Adult - S$11.00
2. Senior - S$4.50
3. Student - S$7.00
```

CineCLI requests one ticket type for each tentatively selected seat, in coordinate order. For
example, seats `G4 G5` are prompted as `G4` followed by `G5`, even if they were
entered in a different order. Enter `1`, `2`, or `3` at each prompt. Invalid,
blank, or unavailable numbers are rejected, and CineCLI asks again for the same
seat before moving on.

## Choose a snack or combo

After every tentatively selected seat has a ticket type, CineCLI displays the fixed
snack/combo identities with the prices loaded for the current session. A new
runtime data directory receives the following seeded prices:

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

After snack selection, CineCLI accepts one optional promo code. In a newly
initialized runtime data directory, the available codes are:

- `CS2103` applies 20% off the complete ticket-and-snack subtotal.
- `CS3227` applies 99% off the complete ticket-and-snack subtotal.

Promo codes are case-insensitive, and surrounding whitespace is ignored. Press
ENTER on a blank line to skip the promotion. A nonblank unsupported code is
rejected and the same prompt is shown again. Only one code can be applied; promo
codes cannot be stacked.

CineCLI constructs the complete four-section bill in memory, atomically confirms
the selected seats, and then displays the bill. If the final confirmation detects
that a seat was taken in the meantime, no bill is displayed and CineCLI returns
to seat selection. If final persistence fails, no bill is displayed. The ticket section identifies the movie
and screening time before listing each seat, demographic ticket type, and unit
price. The snack section lists each distinct selection, its quantity, and its unit
price. Combo contents wrap onto an indented second line. The promotion section
shows the applied code, discount percentage, and amount saved. The final section
repeats the pre-discount subtotal and discount before the payable total. For
example:

```text
============================================================
                        BILL SUMMARY
============================================================

TICKETS
------------------------------------------------------------
Movie: Orbit of Echoes
Time: 29 Aug 2026, 13:30

Seat        Type                                  Unit Price
------------------------------------------------------------
G4          Adult                                    S$11.00
G5          Senior                                    S$4.50
------------------------------------------------------------
Ticket Subtotal:                                     S$15.50

SNACKS AND COMBOS
------------------------------------------------------------
Qty         Item                                  Unit Price
------------------------------------------------------------
2           Popcorn Combo                             S$7.00
            (Popcorn + Soft Drink)
3           Nachos                                    S$6.00
------------------------------------------------------------
Snack Subtotal:                                      S$32.00

PROMOTION
------------------------------------------------------------
Promo Code:                                           CS2103
Discount:                                            20% OFF
Amount Saved:                                        -S$9.50

============================================================
Subtotal:                                            S$47.50
Discount:                                            -S$9.50
------------------------------------------------------------
TOTAL:                                               S$38.00
============================================================
```

All calculations use exact Singapore cents. If applying a percentage produces a
fraction of a cent, the final payable total is rounded to the nearest cent, with a
half cent rounded up. For example, `CS3227` reduces a S$4.50 Senior ticket to
S$0.05. Currency amounts use comma grouping when necessary. When no code is
entered, the promotion section shows `None`, `0% OFF`, and `-S$0.00`. Discount
values use a leading minus sign because they are adjustments subtracted from the
subtotal. CineCLI displays the bill but does not process payment.

## Runtime data

On first launch from a working directory without `data/runtime/catalog.tsv`,
CineCLI creates that file from fictional defaults bundled in the JAR. Later
launches use the existing runtime file without replacing it.

Browsing a screening treats a missing `data/runtime/seats.tsv` as empty and does
not create it. The file is created only by the first successful final seat
confirmation. Confirmed selections are stored by screening ID and seat coordinate.
This file is temporary and will be replaced by booking-owned seat allocations
when booking persistence is implemented.

After the welcome screen and before CineCLI displays the catalog, a missing
`data/runtime/pricing.tsv` is atomically seeded with the current default ticket,
snack/combo, and promotion values. Later launches load the existing pricing file;
they do not replace it. The menu and promo prompt show the values loaded at the
start of the session.

The price attached to each ticket or snack/combo selection, and the code and
percentage of an applied promotion, are captured when selected. A later pricing
change therefore cannot alter an already-created selection or bill.

Ticket assignments, snack and combo choices, applied promo codes, and bills do
not create additional runtime data files.

If a catalog, pricing, or seat file is malformed, CineCLI displays a clear error
and does not use partial or invented data. A malformed file is not overwritten
automatically. A pricing failure ends the customer session before catalog or seat
state is accessed, so it cannot initialize or alter `seats.tsv`. Restore valid
data, or remove a malformed catalog if the bundled catalog defaults should be
recreated on the next launch. See `data/README.md` for the supported
`pricing.tsv` format.

## Switch roles or exit

At every customer or administrator prompt, you may enter `/admin`, `/customer`,
or `/exit`. Commands ignore surrounding whitespace and letter case. `/admin`
discards any unfinished customer purchase and opens the administrator home.
`/customer` opens a fresh customer session; `/exit` ends CineCLI. Local
`/cancel` and Back/`0` choices only abandon the current uncommitted workflow.

## Administrator home

Enter `/admin` from any prompt. The administrator home provides:

```text
Administrator Home
1. Movie Management
2. Screening Management
3. Pricing and Promotions Management
0. Return to customer mode
```

Choose `0` or enter `/customer` to return to the kiosk. The same global commands
remain available within every management workflow.

### Manage movies and screenings

Movie Management lists movies in persisted order and provides List, Add, Edit,
and Delete actions. Screening Management lists screenings with their parent movie
and provides the corresponding actions. Follow the displayed numbered prompts.
Each change is previewed and requires `Y` confirmation; `N` or `/cancel` leaves
the persisted data unchanged. Deleting a movie removes its child screenings and
their temporary occupied-seat records. Deleting a screening removes only that
screening's temporary occupied-seat records; editing its date or time retains
them.

### Manage pricing and promotions

Pricing and Promotions Management leads to ticket-price, snack/combo-price, and
promotion workflows. Ticket and snack/combo identities are fixed; only their
prices may change. Prices must be between `S$0.01` and `S$9,999.99` with two
decimal places. Promotions can be listed, added, edited, or deleted; percentages
must be whole numbers from `1` through `100`. Every change is previewed and
requires `Y` confirmation. A failed save is reported and does not replace the
existing `pricing.tsv`.
