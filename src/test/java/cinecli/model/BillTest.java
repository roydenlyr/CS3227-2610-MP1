package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillTest {
    private static final TicketSelection ADULT_TICKET = new TicketSelection(
            new SeatCoordinate('G', 4), TicketType.ADULT);
    private static final TicketSelection SENIOR_TICKET = new TicketSelection(
            new SeatCoordinate('G', 5), TicketType.SENIOR);

    @Test
    void amounts_mixedTicketsAndSnacks_calculatesExactSubtotals() {
        Bill bill = new Bill(
                List.of(ADULT_TICKET, SENIOR_TICKET),
                List.of(
                        new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2),
                        new SnackSelection(SnackMenuItem.NACHOS, 3)));

        assertAll(
                () -> assertEquals(1550, bill.getTicketSubtotalInCents()),
                () -> assertEquals(3200, bill.getSnackSubtotalInCents()),
                () -> assertEquals(4750, bill.getSubtotalInCents()),
                () -> assertEquals(0, bill.getDiscountInCents()),
                () -> assertEquals(4750, bill.getTotalInCents()));
    }

    @Test
    void total_cs2103_discountsCompleteBillByTwentyPercent() {
        Bill bill = new Bill(
                List.of(ADULT_TICKET, SENIOR_TICKET),
                List.of(
                        new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2),
                        new SnackSelection(SnackMenuItem.NACHOS, 3)),
                Optional.of(PromoCode.CS2103));

        assertAll(
                () -> assertEquals(4750, bill.getSubtotalInCents()),
                () -> assertEquals(950, bill.getDiscountInCents()),
                () -> assertEquals(3800, bill.getTotalInCents()));
    }

    @Test
    void total_cs3227HalfCentResult_roundsPayableTotalHalfUp() {
        Bill bill = new Bill(
                List.of(SENIOR_TICKET), List.of(), Optional.of(PromoCode.CS3227));

        assertAll(
                () -> assertEquals(450, bill.getSubtotalInCents()),
                () -> assertEquals(445, bill.getDiscountInCents()),
                () -> assertEquals(5, bill.getTotalInCents()));
    }

    @Test
    void subtotal_maximumSnackQuantity_usesLongArithmetic() {
        Bill bill = new Bill(
                List.of(ADULT_TICKET),
                List.of(new SnackSelection(
                        SnackMenuItem.NACHOS_COMBO, Integer.MAX_VALUE)));

        assertEquals(1_717_986_918_700L, bill.getSubtotalInCents());
    }

    @Test
    void constructor_noTickets_exceptionThrown() {
        assertThrows(IllegalArgumentException.class, () -> new Bill(List.of(), List.of()));
    }
}
