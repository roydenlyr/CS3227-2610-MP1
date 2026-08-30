package cinecli.storage.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.Bill;
import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import cinecli.model.SnackSelection;
import cinecli.model.TicketSelection;
import cinecli.model.TicketType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PricingCustomerSnapshotRegressionTest {
    @TempDir
    Path tempDirectory;

    @Test
    void save_replacedPricing_preservesExistingCustomerPriceAndPromotionSnapshots() throws Exception {
        PricingStorage storage = new PricingStorage(tempDirectory.resolve("runtime/pricing.tsv"));
        Pricing initialPricing = storage.load();
        TicketSelection ticketSelection = new TicketSelection(
                new SeatCoordinate('A', 1),
                TicketType.ADULT,
                initialPricing.ticketPriceInCents(TicketType.ADULT));
        SnackSelection snackSelection = new SnackSelection(
                SnackMenuItem.POPCORN,
                2,
                initialPricing.snackPriceInCents(SnackMenuItem.POPCORN));
        PromoCode promoCode = initialPricing.findPromotion("CS2103").orElseThrow();
        Bill bill = new Bill(List.of(ticketSelection), List.of(snackSelection), Optional.of(promoCode));

        storage.save(replacementPricing());
        Pricing savedPricing = storage.load();

        assertAll(
                () -> assertEquals(9_999, savedPricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(8_888, savedPricing.snackPriceInCents(SnackMenuItem.POPCORN)),
                () -> assertTrue(savedPricing.findPromotion("CS2103").isEmpty()),
                () -> assertEquals(1_100, ticketSelection.unitPriceInCents()),
                () -> assertEquals(500, snackSelection.unitPriceInCents()),
                () -> assertEquals("CS2103", promoCode.code()),
                () -> assertEquals(20, promoCode.discountPercentage()),
                () -> assertEquals(2_100, bill.getSubtotalInCents()),
                () -> assertEquals(420, bill.getDiscountInCents()),
                () -> assertEquals(1_680, bill.getTotalInCents()));
    }

    private Pricing replacementPricing() {
        return new Pricing(
                Map.of(
                        TicketType.ADULT, 9_999,
                        TicketType.SENIOR, 450,
                        TicketType.STUDENT, 700),
                Map.of(
                        SnackMenuItem.POPCORN, 8_888,
                        SnackMenuItem.NACHOS, 600,
                        SnackMenuItem.SOFT_DRINK, 300,
                        SnackMenuItem.POPCORN_COMBO, 700,
                        SnackMenuItem.NACHOS_COMBO, 800),
                List.of(new PromoCode("RENAMED", 100)));
    }
}
