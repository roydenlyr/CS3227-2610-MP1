# Application Data

The customer movie catalog and temporary seat occupancy are stored as structured
UTF-8 plain text under `data/runtime/`. Runtime files are mutable and ignored by
Git. The fictional default catalog is bundled at
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

When the runtime file is missing, CineCLI creates its parent directory if needed
and copies the bundled defaults. An existing valid empty file remains empty. An
existing malformed file is never overwritten or replaced with defaults; loading
fails with a clear error instead.

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

When the seat file is missing, CineCLI creates a header-only file. Existing data
is fully validated before use. A malformed file is preserved and rejected rather
than replaced with an empty layout. Confirmed changes are written through a
same-directory temporary file and require atomic target replacement. If the file
system does not support atomic replacement, the update fails and existing data is
preserved. The approved runtime assumption is one CineCLI process, so
inter-process locking is not provided.

The terminal's `O` and `X` characters are derived from this data and are not stored
as a rendered grid. During interaction, `X` also represents seats tentatively
selected in the current session; tentative choices are persisted only after the
user confirms them.

Ticket assignments, snack and combo selections, promo codes, and calculated bills
are session-only. They do not create additional runtime files or fields in either
version 1 format.

Do not store credentials or other secrets here.
