# Application Data

The customer movie catalog, global pricing, and temporary seat occupancy are
stored as structured UTF-8 plain text under `data/runtime/`. Runtime files are
mutable and ignored by Git. The fictional default catalog is bundled at
`src/main/resources/cinecli/default-catalog.tsv`.

## Catalog format version 1

Each line is tab-separated. `<TAB>` below represents one tab character.

```text
CINECLI-CATALOG<TAB>1
MOVIE<TAB>movieId<TAB>title<TAB>rating
SCREENING<TAB>screeningId<TAB>movieId<TAB>yyyy-MM-dd<TAB>HH:mm
```

The header must be the first line. A file containing only the header is a valid
empty catalog. Blank lines and unknown record types are invalid.

- IDs must match `[A-Za-z0-9][A-Za-z0-9_-]*`. Movie IDs and screening IDs must be
  unique within their respective record types.
- Titles must be nonblank and must not have leading or trailing whitespace.
- Ratings must be exactly `PG13`, `M18`, or `R21` and are display-only.
- Dates and 24-hour times must be real values in the exact shown formats.
- Every screening must refer to an existing movie. Record order determines display
  order.

Catalog version 1 remains the shared format for customer, Movie Management, and
Screening Management state. New administrator-created IDs use `MOV-<UUID>` and
`SCR-<UUID>`; valid legacy IDs remain accepted. Movies retain their file order;
screenings retain their order within their parent movie. Screening Management
therefore displays a movie-major flattened order, appends new screenings to the
chosen parent, and preserves a screening's parent and position when rescheduled.

When the runtime file is missing, CineCLI creates its parent directory if needed
and copies the bundled defaults. An existing valid empty file remains empty. An
existing malformed file is never overwritten or replaced with defaults; loading
fails with a clear error instead.

## Pricing format version 1

`data/runtime/pricing.tsv` stores the complete global prices for the fixed ticket
and snack/combo identities and the available percentage promotions. Each line is
tab-separated:

```text
CINECLI-PRICING<TAB>1
TICKET_PRICE<TAB>ticketIdentity<TAB>price
SNACK_PRICE<TAB>snackIdentity<TAB>price
PROMOTION<TAB>code<TAB>percentage
```

The header is compulsory. A valid file has exactly one `TICKET_PRICE` record for
each of `ADULT`, `SENIOR`, and `STUDENT`, and exactly one `SNACK_PRICE` record for
each of `POPCORN`, `NACHOS`, `SOFT_DRINK`, `POPCORN_COMBO`, and `NACHOS_COMBO`.
It may contain zero or more `PROMOTION` records.

- Prices use canonical decimal money syntax with exactly two decimal places and
  must be from `0.01` through `9999.99` inclusive.
- Promotion codes use 1 through 32 uppercase ASCII letters, digits, underscores,
  or hyphens; the first character must be a letter or digit. Codes are unique.
- Promotion percentages are whole numbers from `1` through `100` inclusive.
- Blank lines, unknown records, invalid field counts, duplicate identities or
  promotion codes, and unsupported versions are invalid.
- Saves emit ticket and snack records in their menu/domain order and promotions
  in code order, so the resulting bytes are deterministic.

When `pricing.tsv` is missing, CineCLI atomically seeds the complete default
state: Adult `11.00`, Senior `4.50`, Student `7.00`, Popcorn `5.00`, Nachos
`6.00`, Soft Drink `3.00`, Popcorn Combo `7.00`, Nachos Combo `8.00`, `CS2103`
at `20`, and `CS3227` at `99`. An existing valid file is read without rewriting
its bytes. An existing malformed file is preserved and rejected rather than
reseeded.

Every pricing save validates the complete intended state, creates and forces a
same-directory temporary file, and requires atomic replacement of the target.
If writing or atomic replacement fails, the original target data is preserved;
the implementation does not fall back to a non-atomic move.

Pricing and Promotions Management uses this same file and save protocol. It never
adds or removes fixed ticket/snack/combo identities. An administrator mutation
loads the complete state, constructs a replacement, previews it for confirmation,
and saves it only after `Y`. Promotion code input is normalized using the shared
`PromoCode` rule before duplicate checks; a promotion may be renamed or deleted.
These configuration changes apply only to future customer selections: captured
ticket, snack, promotion, and bill snapshots remain in memory with their original
values.

## Temporary seat occupancy format version 1

`data/runtime/seats.tsv` records confirmed seat occupancy until booking persistence
becomes the source of truth. Each line is tab-separated:

```text
CINECLI-SEATS<TAB>1
TAKEN_SEAT<TAB>screeningId<TAB>seatCoordinate
```

Example:

```text
CINECLI-SEATS<TAB>1
TAKEN_SEAT<TAB>SCR-001<TAB>A1
TAKEN_SEAT<TAB>SCR-001<TAB>G20
```

A header-only file means no seats are taken. The absence of a seat record means
that seat is available.

- Screening IDs follow the catalog ID syntax and must refer to current catalog
  screenings.
- Coordinates use an uppercase row `A` through `G` followed by a number `1`
  through `20`, without a leading zero.
- Each `(screeningId, seatCoordinate)` pair must be unique.
- Blank lines, unknown records, invalid field counts, and unsupported versions are
  invalid.
- Records are written deterministically by screening ID, row, and seat number.

When the seat file is missing, browsing treats it as empty and leaves it missing.
Only a successful final customer confirmation creates the header and first
occupancy record. Existing data is fully validated before use. A malformed file
is preserved and rejected rather than replaced with an empty layout. Confirmed
changes are written through a same-directory temporary file and require atomic
target replacement. If final persistence fails for a missing target, the target
remains missing; it must not leave an unintended new `seats.tsv`. If the file
system does not support atomic replacement, the update fails and existing data is
preserved. The approved runtime assumption is one CineCLI process, so
inter-process locking is not provided.

The terminal's `O` and `X` characters are derived from this data and are not stored
as a rendered grid. During interaction, `X` also represents seats tentatively
selected in the current session. Tentative choices remain in memory while ticket,
snack, and promotion choices are made; they are persisted only after the complete
bill text has been generated successfully.

Rescheduling a screening retains every `TAKEN_SEAT` record because the screening
ID is unchanged. Deleting a screening removes only its records; deleting a movie
removes records for all of its child screenings. A missing occupancy file remains
missing during deletion, while a present file with no affected records is retained
byte-for-byte. Before a deletion that requires occupancy access, the complete
existing file must validate; malformed or unavailable occupancy is preserved and
causes that deletion to fail.

## Catalogue deletion recovery journal

Movie and Screening deletion share one durable journal at
`data/runtime/catalog-transaction.journal` under the standard runtime paths. It
is an implementation-owned UTF-8 version-1 file,
not a hand-edited data format. It captures original and intended catalogue and
occupancy snapshots, and its operation is either `DELETE_MOVIE` or
`DELETE_SCREENING`.

The journal is atomically published before a deletion that coordinates catalogue
and occupancy state. Before either customer or administrator access to affected
catalogue or occupancy data, recovery verifies the
operation and snapshots, completes the intended state idempotently, and removes
the journal. A malformed journal or state that diverges from both recorded
snapshots blocks access and is retained for investigation; it is never replaced
or guessed at. There is no separate Screening recovery mechanism.

Ticket assignments, snack and combo selections, applied promo codes, and
calculated bills are session-only. Their captured prices and percentages do not
create additional runtime files or fields beyond the global `pricing.tsv` state.

Do not store credentials or other secrets here.
