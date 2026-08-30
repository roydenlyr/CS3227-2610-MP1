package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillTest {
    private static final TicketSelection ADULT_TICKET = new TicketSelection(
            new SeatCoordinate('G', 4), TicketType.ADULT, 1100);
    private static final TicketSelection SENIOR_TICKET = new TicketSelection(
            new SeatCoordinate('G', 5), TicketType.SENIOR, 450);

    @Test
    void amounts_mixedTicketsAndSnacks_calculatesExactSubtotals() {
        Bill bill = new Bill(
                List.of(ADULT_TICKET, SENIOR_TICKET),
                List.of(
                        new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2, 700),
                        new SnackSelection(SnackMenuItem.NACHOS, 3, 600)));

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
                        new SnackSelection(SnackMenuItem.POPCORN_COMBO, 2, 700),
                        new SnackSelection(SnackMenuItem.NACHOS, 3, 600)),
                Optional.of(new PromoCode("CS2103", 20)));

        assertAll(
                () -> assertEquals(4750, bill.getSubtotalInCents()),
                () -> assertEquals(950, bill.getDiscountInCents()),
                () -> assertEquals(3800, bill.getTotalInCents()));
    }

    @Test
    void total_cs3227HalfCentResult_roundsPayableTotalHalfUp() {
        Bill bill = new Bill(
                List.of(SENIOR_TICKET), List.of(), Optional.of(new PromoCode("CS3227", 99)));

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
                        SnackMenuItem.NACHOS_COMBO, Integer.MAX_VALUE, 800)));

        assertEquals(1_717_986_918_700L, bill.getSubtotalInCents());
    }

    @Test
    void constructor_noTickets_exceptionThrown() {
        assertThrows(IllegalArgumentException.class, () -> new Bill(List.of(), List.of()));
    }

    @Test
    void amounts_capturedPrices_calculatesWithoutConsultingIdentityPrices() {
        Bill bill = new Bill(
                List.of(new TicketSelection(new SeatCoordinate('A', 1), TicketType.ADULT, 1234)),
                List.of(new SnackSelection(SnackMenuItem.POPCORN, 2, 432)),
                Optional.of(new PromoCode("FUTURE", 25)));

        assertAll(
                () -> assertEquals(1234, bill.getTicketSubtotalInCents()),
                () -> assertEquals(864, bill.getSnackSubtotalInCents()),
                () -> assertEquals(2098, bill.getSubtotalInCents()),
                () -> assertEquals(524, bill.getDiscountInCents()),
                () -> assertEquals(1574, bill.getTotalInCents()));
    }

    @Test
    void amounts_pricingChangesAfterSelection_leaveExistingBillUnchanged() {
        Pricing initialPricing = Pricing.defaults();
        TicketSelection selection = new TicketSelection(
                new SeatCoordinate('A', 1),
                TicketType.ADULT,
                initialPricing.ticketPriceInCents(TicketType.ADULT));
        Bill bill = new Bill(
                List.of(selection),
                List.of(),
                Optional.of(initialPricing.findPromotion("CS2103").orElseThrow()));
        Pricing updatedPricing = new Pricing(
                Map.of(
                        TicketType.ADULT, 999_999,
                        TicketType.SENIOR, 450,
                        TicketType.STUDENT, 700),
                Map.of(
                        SnackMenuItem.POPCORN, 500,
                        SnackMenuItem.NACHOS, 600,
                        SnackMenuItem.SOFT_DRINK, 300,
                        SnackMenuItem.POPCORN_COMBO, 700,
                        SnackMenuItem.NACHOS_COMBO, 800),
                List.of(new PromoCode("CS2103", 50)));

        assertAll(
                () -> assertEquals(999_999, updatedPricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(1100, selection.unitPriceInCents()),
                () -> assertEquals(20, bill.promoCode().orElseThrow().discountPercentage()),
                () -> assertEquals(1100, bill.getSubtotalInCents()),
                () -> assertEquals(880, bill.getTotalInCents()));
    }
}
