package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingTest {
    @Test
    void defaults_existingCustomerValues_exposesCompleteImmutableState() {
        Pricing pricing = Pricing.defaults();

        assertAll(
                () -> assertEquals(1100, pricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(450, pricing.ticketPriceInCents(TicketType.SENIOR)),
                () -> assertEquals(700, pricing.ticketPriceInCents(TicketType.STUDENT)),
                () -> assertEquals(500, pricing.snackPriceInCents(SnackMenuItem.POPCORN)),
                () -> assertEquals(600, pricing.snackPriceInCents(SnackMenuItem.NACHOS)),
                () -> assertEquals(300, pricing.snackPriceInCents(SnackMenuItem.SOFT_DRINK)),
                () -> assertEquals(700, pricing.snackPriceInCents(SnackMenuItem.POPCORN_COMBO)),
                () -> assertEquals(800, pricing.snackPriceInCents(SnackMenuItem.NACHOS_COMBO)),
                () -> assertEquals(List.of(new PromoCode("CS2103", 20), new PromoCode("CS3227", 99)),
                        pricing.promotions()),
                () -> assertEquals(new PromoCode("CS2103", 20),
                        pricing.findPromotion("CS2103").orElseThrow()),
                () -> assertTrue(pricing.findPromotion("UNKNOWN").isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> pricing.promotions().add(new PromoCode("OTHER", 1))));
    }

    @Test
    void constructor_missingIdentityOrInvalidPriceOrPromotion_rejectsState() {
        Map<TicketType, Integer> ticketPrices = new EnumMap<>(TicketType.class);
        ticketPrices.put(TicketType.ADULT, 1100);
        Map<SnackMenuItem, Integer> snackPrices = completeSnackPrices();
        Map<TicketType, Integer> zeroTicketPrices = completeTicketPrices();
        zeroTicketPrices.put(TicketType.ADULT, 0);
        Map<TicketType, Integer> excessiveTicketPrices = completeTicketPrices();
        excessiveTicketPrices.put(TicketType.ADULT, 1_000_000);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Pricing(ticketPrices, snackPrices, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Pricing(completeTicketPrices(), incompleteSnackPrices(), List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Pricing(zeroTicketPrices, snackPrices, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Pricing(excessiveTicketPrices, snackPrices, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Pricing(completeTicketPrices(), snackPrices, List.of(
                                new PromoCode("CS2103", 20), new PromoCode("CS2103", 99)))));
    }

    @Test
    void constructor_validBoundaryPricesAndPromotion_acceptsState() {
        Map<TicketType, Integer> ticketPrices = completeTicketPrices();
        ticketPrices.put(TicketType.ADULT, 1);
        Map<SnackMenuItem, Integer> snackPrices = completeSnackPrices();
        snackPrices.put(SnackMenuItem.POPCORN, 999999);

        Pricing pricing = new Pricing(ticketPrices, snackPrices, List.of(new PromoCode("A-1_", 100)));

        assertAll(
                () -> assertEquals(1, pricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(999999, pricing.snackPriceInCents(SnackMenuItem.POPCORN)),
                () -> assertFalse(pricing.findPromotion("A-1_").isEmpty()));
    }

    @Test
    void constructor_mutatedSourceOrDifferentValue_doesNotChangeOrEqualPricing() {
        Map<TicketType, Integer> ticketPrices = completeTicketPrices();
        Map<SnackMenuItem, Integer> snackPrices = completeSnackPrices();
        Pricing pricing = new Pricing(ticketPrices, snackPrices, List.of());
        ticketPrices.put(TicketType.ADULT, 200);

        assertAll(
                () -> assertEquals(100, pricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(pricing, new Pricing(completeTicketPrices(), completeSnackPrices(), List.of())),
                () -> assertEquals(pricing.hashCode(),
                        new Pricing(completeTicketPrices(), completeSnackPrices(), List.of()).hashCode()),
                () -> assertFalse(pricing.equals(Pricing.defaults())),
                () -> assertFalse(pricing.equals("pricing")),
                () -> assertTrue(pricing.equals(pricing)));
    }

    @Test
    void equals_differentSnackOrPromotionIsFalse() {
        Map<SnackMenuItem, Integer> differentSnackPrices = completeSnackPrices();
        differentSnackPrices.put(SnackMenuItem.POPCORN, 101);

        Pricing pricing = new Pricing(completeTicketPrices(), completeSnackPrices(), List.of());

        assertAll(
                () -> assertFalse(pricing.equals(
                        new Pricing(completeTicketPrices(), differentSnackPrices, List.of()))),
                () -> assertFalse(pricing.equals(new Pricing(
                        completeTicketPrices(),
                        completeSnackPrices(),
                        List.of(new PromoCode("SAVE", 10))))));
    }

    private Map<TicketType, Integer> completeTicketPrices() {
        Map<TicketType, Integer> ticketPrices = new EnumMap<>(TicketType.class);
        for (TicketType ticketType : TicketType.values()) {
            ticketPrices.put(ticketType, 100);
        }
        return ticketPrices;
    }

    private Map<SnackMenuItem, Integer> completeSnackPrices() {
        Map<SnackMenuItem, Integer> snackPrices = new EnumMap<>(SnackMenuItem.class);
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            snackPrices.put(menuItem, 100);
        }
        return snackPrices;
    }

    private Map<SnackMenuItem, Integer> incompleteSnackPrices() {
        Map<SnackMenuItem, Integer> snackPrices = new EnumMap<>(SnackMenuItem.class);
        snackPrices.put(SnackMenuItem.POPCORN, 100);
        return snackPrices;
    }
}
