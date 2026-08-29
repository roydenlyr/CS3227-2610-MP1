package cinecli.admin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingInputRulesAndReplacementsTest {
    private final PricingInputRules inputRules = new PricingInputRules();

    @Test
    void parsePriceInCents_acceptsExactBoundariesAndRejectsMalformedOrOutOfRangeValues() {
        assertAll(
                () -> assertEquals(1, inputRules.parsePriceInCents(" 0.01 ")),
                () -> assertEquals(999_999, inputRules.parsePriceInCents("9999.99")),
                () -> assertNull(inputRules.parsePriceInCents("0.00")),
                () -> assertNull(inputRules.parsePriceInCents("10000.00")),
                () -> assertNull(inputRules.parsePriceInCents("1.0")),
                () -> assertNull(inputRules.parsePriceInCents("1.000")),
                () -> assertNull(inputRules.parsePriceInCents("01.00")),
                () -> assertNull(inputRules.parsePriceInCents("not-a-price")));
    }

    @Test
    void parsePercentage_acceptsBoundariesAndRejectsInvalidAndOverflowValues() {
        assertAll(
                () -> assertEquals(1, inputRules.parsePercentage(" 1 ")),
                () -> assertEquals(100, inputRules.parsePercentage("100")),
                () -> assertNull(inputRules.parsePercentage("0")),
                () -> assertNull(inputRules.parsePercentage("101")),
                () -> assertNull(inputRules.parsePercentage("1.0")),
                () -> assertNull(inputRules.parsePercentage("-1")),
                () -> assertNull(inputRules.parsePercentage("999999999999999999999999")));
    }

    @Test
    void replacements_createIndependentCompletePricingStatesForEveryMutation() {
        Pricing original = Pricing.defaults();
        PromoCode added = new PromoCode("new-code", 100);
        List<PromoCode> withAdded = PricingReplacements.promotionsWithAdded(original, added);
        Pricing ticketChanged = PricingReplacements.withTicketPrice(original, TicketType.ADULT, 1200);
        Pricing snackChanged = PricingReplacements.withSnackPrice(original, SnackMenuItem.NACHOS, 650);
        Pricing promotionsChanged = PricingReplacements.withPromotions(original, withAdded);
        List<PromoCode> replaced = PricingReplacements.promotionsWithReplaced(
                promotionsChanged, 0, new PromoCode("renamed", 1));
        List<PromoCode> removed = PricingReplacements.promotionsWithRemoved(
                promotionsChanged, 1);

        assertAll(
                () -> assertEquals(1100, original.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(500, original.snackPriceInCents(SnackMenuItem.POPCORN)),
                () -> assertEquals(1200, ticketChanged.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(650, snackChanged.snackPriceInCents(SnackMenuItem.NACHOS)),
                () -> assertEquals(List.of("CS2103", "CS3227", "NEW-CODE"),
                        promotionsChanged.promotions().stream().map(PromoCode::code).toList()),
                () -> assertEquals("RENAMED", replaced.getFirst().code()),
                () -> assertEquals(List.of("CS2103", "NEW-CODE"),
                        removed.stream().map(PromoCode::code).toList()));
    }
}
