package cinecli.model;

import java.util.Objects;

/**
 * Associates one confirmed seat with its customer ticket type.
 *
 * @param seat Confirmed seat.
 * @param ticketType Selected customer demographic.
 */
public record TicketSelection(SeatCoordinate seat, TicketType ticketType) {
    /**
     * Creates a ticket selection for one confirmed seat.
     *
     * @throws NullPointerException If {@code seat} or {@code ticketType} is null.
     */
    public TicketSelection {
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(ticketType, "ticketType");
    }
}
