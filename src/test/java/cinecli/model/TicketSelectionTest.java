package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TicketSelectionTest {
    @Test
    void constructor_validSeatTypeAndPrice_createsPriceSnapshot() {
        SeatCoordinate seat = new SeatCoordinate('G', 4);

        TicketSelection selection = new TicketSelection(seat, TicketType.STUDENT, 700);

        assertAll(
                () -> assertEquals(seat, selection.seat()),
                () -> assertEquals(TicketType.STUDENT, selection.ticketType()),
                () -> assertEquals(700, selection.unitPriceInCents()),
                () -> assertEquals(1,
                        new TicketSelection(seat, TicketType.ADULT, 1).unitPriceInCents()),
                () -> assertEquals(999_999,
                        new TicketSelection(seat, TicketType.ADULT, 999_999).unitPriceInCents()));
    }

    @Test
    void constructor_nullComponent_exceptionThrown() {
        SeatCoordinate seat = new SeatCoordinate('A', 1);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new TicketSelection(null, TicketType.ADULT, 1100)),
                () -> assertThrows(NullPointerException.class,
                        () -> new TicketSelection(seat, null, 1100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TicketSelection(seat, TicketType.ADULT, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TicketSelection(seat, TicketType.ADULT, 1_000_000)));
    }
}
