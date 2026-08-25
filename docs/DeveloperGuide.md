# Developer Guide

## Project status

This repository implements a customer vertical slice from the welcome screen to
catalog display, screening selection, terminal seat selection, ticket demographic
selection, optional snacks and combos, optional promotion input, and an itemized
bill. Catalog and temporary seat occupancy data are strictly validated and
persisted as versioned plain text. Ticket, snack, promotion, and bill data remain
session-only; payment, booking persistence, and administration remain deferred.

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

On macOS or Linux, use:

```shell
java --version
sh ./mvnw --version
sh ./mvnw clean verify
java -jar target/cinecli-0.1.0-SNAPSHOT.jar
```

## Source layout

- Production Java: `src/main/java/cinecli/`
- Test Java: `src/test/java/cinecli/`
- Bundled default catalog: `src/main/resources/cinecli/default-catalog.tsv`
- Mutable local data: `data/runtime/`

The implemented responsibilities are intentionally small:

- `Main` wires UTF-8 system streams, runtime paths, the UI, and storage.
- `CustomerApplication` coordinates catalog selection, seat-selection rules,
  confirmation, ticket and snack selection, promo input, bill creation, and
  graceful storage failure.
- `CustomerUi` owns customer-facing input prompts and output formatting, including
  the fixed terminal seat map, menus, and four-section itemized bill.
- `Movie`, `Screening`, `ContentRating`, `ScreeningSelection`, `SeatCoordinate`,
  `TicketType`, `TicketSelection`, `SnackMenuItem`, `SnackSelection`, `PromoCode`,
  and `Bill` represent immutable domain, selection, and billing data.
- `CatalogStorage` initializes missing runtime data and reads the file.
- `CatalogParser` strictly parses and validates the versioned text format before
  returning any movies.
- `SeatStorage` initializes, reads, and atomically replaces temporary seat
  occupancy data.
- `SeatParser` strictly validates all temporary occupancy records before any seat
  state is returned or updated.

No booking or checkout persistence, payment processing, administration layer, or
production dependency has been introduced. The temporary seat writer is
intentionally isolated so booking-owned seat allocations can replace it without
changing the terminal map.

## Testing

JUnit tests use injected readers, writers, and temporary directories. They cover
the welcome-to-bill workflow, screening code parsing, exact seat-map orientation,
tentative selection, confirmation and cancellation, occupied-seat rejection,
deterministic seat-to-ticket assignment, all ticket prices, ticket retries, the
exact snack menu and prices, multiple snack and combo choices, quantity boundaries,
repeated-item replacement, promo parsing and retries, aggregate discounts,
half-cent rounding, checked long-cent arithmetic, exact receipt sections and
columns, combo-description wrapping, grouped large currency amounts, session
isolation, strict catalog and seat validation, deterministic seat writes, and
missing-file initialization.
Tests never read or write the real `data/runtime` directory.

Run the complete build with `clean verify` before considering an implementation
task complete. Also verify the bundled resource is present in the JAR and smoke-test
the JAR from an empty temporary working directory.

## Customer interaction flow

Catalog screenings are labelled `A`, `B`, and so on within each movie. A code such
as `3B` resolves to the second screening of the third movie. The selection parser
accepts lowercase input but stores one-based movie and timing positions.

Every screening uses the same 7-by-20 layout. The UI renders `SCREEN` above row
`G`, rows in the order `G` through `A`, and numbers `1` through `20` below row `A`.
Previously taken seats and tentative session selections both render as `X`.
Tentative seats are written only after the user confirms them with `Y`; declining
clears them and returns to the original persisted map.

After seat persistence succeeds, confirmed coordinates are sorted and each is
paired with exactly one `TicketType` in an immutable `TicketSelection`. The fixed
enum values are Adult at 1,100 cents, Senior at 450 cents, and Student at 700
cents. `TicketType` parses menu numbers, while `CustomerApplication` owns the
per-seat retry loop. No ticket prompt is shown if seat selection does not complete.

After every ticket type is selected, the application displays a fixed in-memory
menu of three individual items and two combos. Menu numbers are parsed by
`SnackMenuItem`; exact prices are stored as Singapore cents and formatted by the
UI with two decimal places. Each selected item is paired with a positive
whole-number quantity in an immutable `SnackSelection`.

The application keeps selections in original selection order and keys them by
menu item. A customer can therefore add multiple a la carte items and combos, while
selecting the same item again replaces its earlier quantity without creating a
duplicate summary line. The item prompt repeats until the customer enters `0`;
immediate `0` skips, while `0` after one or more selections displays a summary with
unit prices. Malformed item numbers and quantities are retried at their respective
prompts. No snack menu is shown when seat selection or ticket selection is
cancelled, incomplete, unavailable, or fails to persist.

The promotion prompt accepts a blank line as an explicit skip. Nonblank input is
trimmed, normalized case-insensitively, and parsed by `PromoCode`; invalid values
return to the same prompt. `CS2103` stores a 20% discount and `CS3227` stores a 99%
discount. Exactly one optional code is passed to the bill, so discounts cannot
stack.

`Bill` takes defensive copies of the ticket and snack selections and keeps the
optional promo code. It calculates ticket and quantity-aware snack subtotals with
checked `long`-cent arithmetic, then applies the discount to their aggregate. The
final payable amount is rounded to the nearest cent with half cents rounded up;
the discount shown is the exact difference between the pre-discount subtotal and
that rounded payable amount. `CustomerUi` formats these domain results and does not
implement monetary rules.

`CustomerApplication` passes the selected `Movie` and `Screening` to the final UI
call so receipt context does not become part of the arithmetic-focused `Bill`
model. `CustomerUi` renders a 60-character-wide receipt with four sections:

1. `TICKETS` shows movie title, screening date and time, seat, ticket type, unit
   price, and ticket subtotal.
2. `SNACKS AND COMBOS` shows each distinct selection in original selection order,
   quantity, unit price, and snack subtotal. Parenthesized combo contents wrap
   beneath the item name.
3. `PROMOTION` shows the code or `None`, the discount percentage, and the amount
   saved.
4. The final totals block repeats the aggregate pre-discount subtotal and discount,
   then ends with the payable total.

Receipt tables and label-value rows are produced by shared formatting helpers so
their columns remain consistent. Currency keeps exact two-decimal-cent output and
adds comma grouping for large values. The amount saved and repeated discount are
printed as negative adjustments, including `-S$0.00` when no promotion applies.

## Persistence

The runtime catalog is `data/runtime/catalog.tsv`, resolved relative to the process
working directory. It is a UTF-8, tab-separated, versioned file:

```text
CINECLI-CATALOG<TAB>1
MOVIE<TAB>movieId<TAB>title<TAB>rating
SCREENING<TAB>screeningId<TAB>movieId<TAB>yyyy-MM-dd<TAB>HH:mm
```

The header is compulsory. A header-only file is the valid empty catalog. IDs match
`[A-Za-z0-9][A-Za-z0-9_-]*` and are unique within their record type. Titles are
nonblank without surrounding whitespace. Ratings are exactly `PG13`, `M18`, or
`R21`. Dates and times use strict ISO-style fields, and every screening must refer
to an existing movie. Movies and screenings retain persisted order.

Only a missing runtime file triggers initialization from the bundled resource.
Valid empty or malformed existing files are not overwritten. Parsing validates the
whole file before the UI receives data, preventing partial catalog display.

Temporary confirmed seat occupancy is stored in `data/runtime/seats.tsv`:

```text
CINECLI-SEATS<TAB>1
TAKEN_SEAT<TAB>screeningId<TAB>seatCoordinate
```

A header-only file means all seats are available. Coordinates are canonical values
from `A1` through `G20`, and every record must refer to an existing catalog
screening. Duplicate coordinates, blank or unknown records, unsupported versions,
and malformed fields reject the complete file.

The seat store reloads and validates the entire file before confirmation, rejects
an already-taken coordinate, writes the canonical state to a same-directory
temporary file, and then atomically replaces the target. If atomic replacement is
unsupported, the update fails and preserves the existing data. Inter-process
locking is deliberately deferred because only one CineCLI process is expected.

This occupancy file is transitional. Once booking persistence is implemented,
bookings will own seat allocations and availability will be derived from those
allocations rather than duplicated here.

Ticket assignments, snack and combo selections, promo codes, and calculated bills
remain in memory only. No schema was added for them; a future booking-persistence
requirement must define how these values are represented before they are stored.

The detailed data policy is recorded in `data/README.md`.
