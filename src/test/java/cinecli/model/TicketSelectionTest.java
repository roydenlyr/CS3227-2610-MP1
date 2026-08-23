package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TicketSelectionTest {
    @Test
    void constructor_validSeatAndType_created() {
        SeatCoordinate seat = new SeatCoordinate('G', 4);

        TicketSelection selection = new TicketSelection(seat, TicketType.STUDENT);

        assertAll(
                () -> assertEquals(seat, selection.seat()),
                () -> assertEquals(TicketType.STUDENT, selection.ticketType()));
    }

    @Test
    void constructor_nullComponent_exceptionThrown() {
        SeatCoordinate seat = new SeatCoordinate('A', 1);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new TicketSelection(null, TicketType.ADULT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new TicketSelection(seat, null)));
    }
}
