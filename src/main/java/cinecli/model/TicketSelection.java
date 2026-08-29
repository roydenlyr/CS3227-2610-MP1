package cinecli.model;

import java.util.Objects;

/**
 * Associates one confirmed seat with its customer ticket type and captured unit price.
 *
 * @param seat Confirmed seat.
 * @param ticketType Selected customer demographic.
 * @param unitPriceInCents Captured unit ticket price in Singapore cents.
 */
public record TicketSelection(SeatCoordinate seat, TicketType ticketType, int unitPriceInCents) {
    private static final int MINIMUM_UNIT_PRICE_IN_CENTS = 1;
    private static final int MAXIMUM_UNIT_PRICE_IN_CENTS = 999_999;

    /**
     * Creates a ticket selection for one confirmed seat.
     *
     * @throws NullPointerException If {@code seat} or {@code ticketType} is null.
     * @throws IllegalArgumentException If the unit price is outside the supported range.
     */
    public TicketSelection {
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(ticketType, "ticketType");
        if (unitPriceInCents < MINIMUM_UNIT_PRICE_IN_CENTS
                || unitPriceInCents > MAXIMUM_UNIT_PRICE_IN_CENTS) {
            throw new IllegalArgumentException(getUnitPriceRequirement());
        }
    }

    private static String getUnitPriceRequirement() {
        return "unit price must be from " + MINIMUM_UNIT_PRICE_IN_CENTS + " through "
                + MAXIMUM_UNIT_PRICE_IN_CENTS + " cents";
    }
}
