package cinecli.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Calculates exact ticket, snack, discount, and payable amounts for one customer session.
 */
public record Bill(
        List<TicketSelection> ticketSelections,
        List<SnackSelection> snackSelections,
        Optional<PromoCode> promoCode) {
    private static final int PERCENTAGE_DIVISOR = 100;
    private static final int HALF_PERCENTAGE_DIVISOR = 50;

    /**
     * Creates a bill from completed selections and an optional promotion.
     *
     * @throws NullPointerException If a supplied value or list element is null.
     * @throws IllegalArgumentException If there are no ticket selections.
     */
    public Bill {
        ticketSelections = List.copyOf(Objects.requireNonNull(ticketSelections));
        snackSelections = List.copyOf(Objects.requireNonNull(snackSelections));
        promoCode = Objects.requireNonNull(promoCode);
        if (ticketSelections.isEmpty()) {
            throw new IllegalArgumentException("a bill requires at least one ticket");
        }
    }

    /**
     * Creates a bill without a promotion.
     *
     * @param ticketSelections Completed ticket selections.
     * @param snackSelections Completed snack and combo selections.
     */
    public Bill(
            List<TicketSelection> ticketSelections, List<SnackSelection> snackSelections) {
        this(ticketSelections, snackSelections, Optional.empty());
    }

    /**
     * Returns the sum of all ticket prices in Singapore cents.
     *
     * @return Ticket subtotal in cents.
     */
    public long getTicketSubtotalInCents() {
        long subtotal = 0;
        for (TicketSelection selection : ticketSelections) {
            subtotal = Math.addExact(subtotal, selection.unitPriceInCents());
        }
        return subtotal;
    }

    /**
     * Returns the sum of all snack and combo quantities in Singapore cents.
     *
     * @return Snack and combo subtotal in cents.
     */
    public long getSnackSubtotalInCents() {
        long subtotal = 0;
        for (SnackSelection selection : snackSelections) {
            long selectionSubtotal = Math.multiplyExact(
                    (long) selection.unitPriceInCents(), selection.quantity());
            subtotal = Math.addExact(subtotal, selectionSubtotal);
        }
        return subtotal;
    }

    /**
     * Returns the complete pre-discount subtotal in Singapore cents.
     *
     * @return Pre-discount subtotal in cents.
     */
    public long getSubtotalInCents() {
        return Math.addExact(getTicketSubtotalInCents(), getSnackSubtotalInCents());
    }

    /**
     * Returns the promotion discount in Singapore cents.
     *
     * @return Discount in cents, or zero when there is no promotion.
     */
    public long getDiscountInCents() {
        return Math.subtractExact(getSubtotalInCents(), getTotalInCents());
    }

    /**
     * Returns the payable total in Singapore cents.
     * Percentage results are rounded to the nearest cent, with half cents rounded up.
     *
     * @return Payable total in cents.
     */
    public long getTotalInCents() {
        long subtotal = getSubtotalInCents();
        if (promoCode.isEmpty()) {
            return subtotal;
        }

        int payablePercentage = PERCENTAGE_DIVISOR
                - promoCode.orElseThrow().discountPercentage();
        long scaledPayableTotal = Math.multiplyExact(subtotal, payablePercentage);
        return Math.floorDiv(
                Math.addExact(scaledPayableTotal, HALF_PERCENTAGE_DIVISOR),
                PERCENTAGE_DIVISOR);
    }
}
