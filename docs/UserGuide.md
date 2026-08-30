# User Guide

CineCLI is a command-line cinema kiosk with customer and administrator modes.
Customers can select a screening, seats, tickets, snacks or combos, and an
optional promotion before receiving a bill. Administrators can manage movies,
screenings, prices, and promotions.

## Contents

- [Getting started](#getting-started)
- [Global commands](#global-commands)
- [Customer mode](#customer-mode)
  - [Choose a screening and seats](#choose-a-screening-and-seats)
  - [Choose tickets, snacks, and a promotion](#choose-tickets-snacks-and-a-promotion)
  - [Review the bill](#review-the-bill)
- [Administrator mode](#administrator-mode)
  - [Movie management](#movie-management)
  - [Screening management](#screening-management)
  - [Price management](#price-management)
  - [Promotion management](#promotion-management)
- [Data persistence](#data-persistence)
- [Troubleshooting](#troubleshooting)

## Getting started

Install Java 25 LTS, then build and run CineCLI from the project root.

On Windows:

~~~powershell
.\mvnw.cmd clean verify
java -jar target\cinecli-0.1.0-SNAPSHOT.jar
~~~

On macOS or Linux:

~~~shell
sh ./mvnw clean verify
java -jar target/cinecli-0.1.0-SNAPSHOT.jar
~~~

The application starts in Customer Mode. Press ENTER at the welcome screen to
display the movie catalog.

## Global commands

At every prompt, CineCLI accepts the following commands. Commands ignore letter
case and surrounding whitespace.

| Command | Result |
| --- | --- |
| <code>/admin</code> | Opens Administrator Mode. An unfinished customer purchase is discarded. |
| <code>/customer</code> | Opens a new Customer Mode session. |
| <code>/exit</code> | Exits CineCLI. |

Cancellation commands are different from global commands:

| Input | Where it works | Result |
| --- | --- | --- |
| <code>CANCEL</code> or <code>/cancel</code> | Customer selection and entry prompts | Discards the unfinished purchase and returns to the Customer Mode welcome screen. |
| <code>/cancel</code> | Administrator add, edit, delete, and price-entry workflows | Cancels the current administrator operation without saving it. |
| <code>N</code> | Administrator confirmation prompts | Cancels the displayed change without saving it. |
| <code>0</code> | Menu-specific | Follows the action shown by that menu. For example, it finishes customer snack selection, returns from a management menu, or returns to Customer Mode from the administrator home. |

<code>CANCEL</code> by itself is not an administrator cancellation command.
Likewise, <code>0</code> does not have one universal meaning.

## Customer Mode

The catalog lists movies in saved order, with each movie's content rating and
lettered screening times. If no movies are available, CineCLI displays
<code>No movies are currently available.</code>

### Choose a screening and seats

1. Enter the movie number followed by its screening letter, such as <code>3B</code>.
   The code is case-insensitive.
2. Enter one or more seat coordinates separated by spaces, such as
   <code>G4 G5</code>. The map has rows <code>A</code> through <code>G</code> and seat
   numbers <code>1</code> through <code>20</code>; row <code>G</code> is closest to the
   screen. <code>O</code> is available, while <code>X</code> is taken or selected
   tentatively in the current session.
3. Enter <code>Y</code> to keep the selected seats while you choose tickets and
   extras. Enter <code>N</code> to clear that selection and choose seats again.

CineCLI rejects malformed, duplicate, or already-taken seat coordinates and asks
you to try again. Selecting seats is not the final confirmation: seats are
recorded only after the bill has been prepared successfully.

### Choose tickets, snacks, and a promotion

For each selected seat, choose one ticket type. CineCLI prompts for seats in
coordinate order, regardless of the order in which you entered them.

~~~text
Ticket Types
1. Adult
2. Senior
3. Student
~~~

The menu shows the current prices. Enter <code>1</code>, <code>2</code>, or
<code>3</code>; invalid choices are rejected and the same seat is prompted again.

Next, choose zero or more snacks or combos. Enter an item number, then a positive
whole-number quantity. Enter <code>0</code> at the item prompt to finish; entering
<code>0</code> immediately skips snacks and combos. Choosing the same item again
replaces its previous quantity.

Finally, enter one available promo code or press ENTER to skip. Promo codes are
case-insensitive and surrounding whitespace is ignored. Only one promotion can be
applied.

### Review the bill

The bill shows the selected movie and screening, ticket assignments, snack and
combo quantities, any promotion, the discount, and the total payable amount.
CineCLI uses Singapore dollars and cents. It does not process payment or create a
booking record.

After a bill is displayed, press ENTER to start a new customer session, or use a
global command.

## Administrator Mode

Enter <code>/admin</code> to open the administrator home:

~~~text
Administrator Home
1. Movie Management
2. Screening Management
3. Pricing and Promotions Management
0. Return to customer mode
~~~

Administrator changes are shown in a preview and saved only after you enter
<code>Y</code>. Entering <code>N</code> or <code>/cancel</code> abandons the
proposed change.

### Movie management

Movie Management lists each movie with its ID, content rating, and number of
screenings.

~~~text
Movie Management

Actions
1. Add movie
2. Edit movie
3. Delete movie
0. Back
~~~

- Add: choose <code>1</code>, enter a nonblank title, choose <code>1</code>
  (PG13), <code>2</code> (M18), or <code>3</code> (R21), then confirm the preview.
- Edit: choose <code>2</code>, select a movie number, change its title or rating,
  review the changes, then confirm.
- Delete: choose <code>3</code>, select a movie number, and confirm the deletion.

Deleting a movie also deletes its screenings and their temporary occupied-seat
records. <code>0</code> returns to the administrator home.

### Screening management

Screening Management lists every screening with its parent movie, IDs, and start
time.

~~~text
Screening Management

Actions
1. Add screening
2. Edit screening
3. Delete screening
0. Back
~~~

- Add: choose <code>1</code>, select a parent movie, enter a date as
  <code>yyyy-MM-dd</code> and a time as <code>HH:mm</code>, then confirm the preview.
- Edit: choose <code>2</code>, select a screening number, change its date or time,
  review the changes, then confirm. The parent movie cannot be changed.
- Delete: choose <code>3</code>, select a screening number, and confirm the
  deletion.

Editing a screening retains its temporary occupied seats. Deleting a screening
removes only that screening's temporary occupied-seat records. <code>0</code>
returns to the administrator home.

### Price management

From Pricing and Promotions Management, choose the ticket or snack/combo price
section:

~~~text
Pricing and Promotions Management
1. Ticket Prices
2. Snack/Combo Prices
3. Promotions
0. Back
~~~

Each price-management section provides one edit action:

~~~text
Ticket Price Management
1. Edit ticket price
0. Back

Snack and Combo Price Management
1. Edit snack/combo price
0. Back
~~~

Choose <code>1</code>, select a displayed ticket (<code>1</code>-<code>3</code>) or
snack/combo (<code>1</code>-<code>5</code>), enter a new price such as
<code>11.50</code>, and confirm the preview. Prices must be from
<code>S$0.01</code> to <code>S$9,999.99</code> and have exactly two decimal places.
Ticket and snack/combo items are fixed; only their prices can be changed.

### Promotion management

From Pricing and Promotions Management, choose <code>3</code> to open Promotion
Management:

~~~text
Promotion Management

Actions
1. Add promotion
2. Edit promotion
3. Delete promotion
0. Back
~~~

- Add: choose <code>1</code>, enter a promotion code and a whole discount
  percentage from <code>1</code> through <code>100</code>, then confirm.
- Edit: choose <code>2</code>, select a promotion, change its code or percentage,
  review the changes, then confirm.
- Delete: choose <code>3</code>, select a promotion, and confirm.

Promotion codes are normalized to uppercase. They must contain 1 to 32 ASCII
letters, digits, underscores, or hyphens, begin with a letter or digit, and be
unique.

## Data persistence

CineCLI stores runtime data relative to the directory from which you start the
application:

- <code>data/runtime/catalog.tsv</code> stores the catalog and administrator movie
  and screening changes.
- <code>data/runtime/pricing.tsv</code> stores ticket prices, snack/combo prices,
  and promotions.
- <code>data/runtime/seats.tsv</code> stores confirmed seat occupancy.

On first use, CineCLI creates default catalog and pricing data if those files are
missing. The seat file is created only after a successful final seat confirmation.
Confirmed seats remain unavailable on later runs. Customer selections other than
confirmed seats are not saved.

If data cannot be read or saved, CineCLI displays an error and does not replace the
affected file with invented values. For the supported file formats, see
<code>data/README.md</code>.

## Troubleshooting

| Situation | What to do |
| --- | --- |
| A menu rejects your input. | Use the format displayed in the prompt. CineCLI keeps the current prompt active after invalid input. |
| You want to abandon an unfinished customer purchase. | Enter <code>CANCEL</code> or <code>/cancel</code> at a customer selection or entry prompt. |
| You want to abandon an administrator change. | Enter <code>/cancel</code> where offered, or enter <code>N</code> at its preview. |
| CineCLI reports that data cannot be loaded or saved. | Correct or restore the affected runtime file under <code>data/runtime/</code>, then start CineCLI again. |
| The catalog is empty. | Add movies and screenings in Administrator Mode, or restore a valid catalog file. |
