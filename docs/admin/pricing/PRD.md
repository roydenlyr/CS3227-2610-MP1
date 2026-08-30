# Pricing Storage PRD

**Status:** Owner-approved implementation contract

**Approval date:** 29 August 2026

## Scope

Provide versioned runtime persistence for CineCLI's global ticket, snack/combo,
and percentage-promotion pricing. Load it once after the customer welcome step
and before catalog or seat access. The workstream does not provide an
administrator pricing interface or promotion CRUD.

## Customer-visible behaviour

- A missing `data/runtime/pricing.tsv` is seeded with the existing customer
  values: Adult 11.00, Senior 4.50, Student 7.00, Popcorn 5.00, Nachos 6.00,
  Soft Drink 3.00, Popcorn Combo 7.00, Nachos Combo 8.00, `CS2103` at 20%, and
  `CS3227` at 99%.
- A valid existing pricing file supplies the menu prices and available
  promotions for that session without being rewritten on load.
- If pricing cannot be read, validated, or safely seeded, CineCLI reports the
  pricing-storage failure and ends the customer session before catalog display
  or any seat-state access.
- A ticket or snack/combo selection captures its unit price at creation. An
  applied promotion captures its code and percentage. Bills calculate from those
  snapshots, so a later global-pricing change cannot alter an existing selection
  or bill.

## Data contract

`pricing.tsv` is UTF-8 tab-separated text with this version-1 grammar:

```text
CINECLI-PRICING<TAB>1
TICKET_PRICE<TAB>ticketIdentity<TAB>price
SNACK_PRICE<TAB>snackIdentity<TAB>price
PROMOTION<TAB>code<TAB>percentage
```

The file contains exactly one price for each fixed ticket identity and each fixed
snack/combo identity, and zero or more unique promotions. Prices are canonical
two-decimal amounts from 0.01 through 9999.99. Promotion codes are uppercase,
unique, 1 through 32 characters, start with an ASCII letter or digit, and then
use only ASCII letters, digits, underscores, or hyphens. Percentages are whole
numbers from 1 through 100.

## Persistence and failure behaviour

An existing malformed file is rejected and left byte-for-byte unchanged. Saving
validates the complete intended state, creates the complete canonical bytes,
writes and forces a same-directory temporary file, and requires atomic target
replacement. A failure before replacement preserves existing durable target data;
there is no non-atomic fallback. Fixed prices serialize in enum display order and
promotions serialize by code.

## Deferred work

- Administrator ticket/snack/combo price editing.
- Administrator promotion listing and CRUD.
- Shared role-routing integration.
- Booking persistence of completed price and promotion snapshots.
