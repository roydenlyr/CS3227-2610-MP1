package cinecli.storage.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.Pricing;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import cinecli.storage.exception.PricingStorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class PricingParserTest {
    private final PricingParser parser = new PricingParser();

    @Test
    void parse_validCompleteFile_returnsPricingRegardlessOfRecordOrder() throws Exception {
        Pricing pricing = parse("""
                CINECLI-PRICING\t1
                PROMOTION\tCS3227\t99
                SNACK_PRICE\tNACHOS\t6.00
                TICKET_PRICE\tSTUDENT\t7.00
                TICKET_PRICE\tADULT\t11.00
                SNACK_PRICE\tPOPCORN_COMBO\t7.00
                SNACK_PRICE\tSOFT_DRINK\t3.00
                TICKET_PRICE\tSENIOR\t4.50
                SNACK_PRICE\tNACHOS_COMBO\t8.00
                PROMOTION\tCS2103\t20
                SNACK_PRICE\tPOPCORN\t5.00
                """);

        assertAll(
                () -> assertEquals(1100, pricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(600, pricing.snackPriceInCents(SnackMenuItem.NACHOS)),
                () -> assertEquals(20, pricing.findPromotion("CS2103").orElseThrow().discountPercentage()));
    }

    @Test
    void parse_utf8ByteOrderMarkAndBufferedReader_acceptsHeader() throws Exception {
        Pricing pricing = parser.parse(new BufferedReader(new StringReader(
                "\uFEFFCINECLI-PRICING\t1\n" + validPricing().substring("CINECLI-PRICING\t1\n".length()))),
                "buffered pricing");

        assertEquals(1100, pricing.ticketPriceInCents(TicketType.ADULT));
    }

    @Test
    void parse_headerOrRecordShapeFailures_rejectsFile() {
        assertAll(
                () -> assertMalformed(""),
                () -> assertMalformed("CINECLI-PRICING\t2\n"),
                () -> assertMalformed("NOT-PRICING\t1\n"),
                () -> assertMalformed("CINECLI-PRICING\t1\nUNKNOWN\tA\t1\n"),
                () -> assertMalformed("CINECLI-PRICING\t1\nTICKET_PRICE\tADULT\n"),
                () -> assertMalformed("CINECLI-PRICING\t1\n\n"));
    }

    @Test
    void parse_missingOrDuplicateFixedIdentity_rejectsFile() {
        assertAll(
                () -> assertMalformed(validPricing().replace("TICKET_PRICE\tADULT\t11.00\n", "")),
                () -> assertMalformed(validPricing() + "TICKET_PRICE\tADULT\t11.00\n"),
                () -> assertMalformed(validPricing() + "SNACK_PRICE\tPOPCORN\t5.00\n"),
                () -> assertMalformed(validPricing().replace("ADULT", "UNKNOWN_TICKET")),
                () -> assertMalformed(validPricing().replace("POPCORN", "UNKNOWN_SNACK")));
    }

    @Test
    void parse_priceAtBoundsAndNonCanonicalOrOutOfRangePrice_handlesBoundaries() throws Exception {
        Pricing pricing = parse(validPricing()
                .replace("TICKET_PRICE\tADULT\t11.00", "TICKET_PRICE\tADULT\t0.01")
                .replace("SNACK_PRICE\tPOPCORN\t5.00", "SNACK_PRICE\tPOPCORN\t9999.99"));

        assertAll(
                () -> assertEquals(1, pricing.ticketPriceInCents(TicketType.ADULT)),
                () -> assertEquals(999999, pricing.snackPriceInCents(SnackMenuItem.POPCORN)),
                () -> assertMalformed(validPricing().replace("11.00", "0.00")),
                () -> assertMalformed(validPricing().replace("11.00", "10000.00")),
                () -> assertMalformed(validPricing().replace("11.00", "01.00")),
                () -> assertMalformed(validPricing().replace("11.00", "11.0")));
    }

    @Test
    void parse_promotionValidation_rejectsInvalidPercentagesCodesAndDuplicates() {
        assertAll(
                () -> assertMalformed(validPricing().replace("CS2103\t20", "CS2103\t0")),
                () -> assertMalformed(validPricing().replace("CS2103\t20", "CS2103\t101")),
                () -> assertMalformed(validPricing().replace(
                        "CS2103\t20", "CS2103\t999999999999999999999")),
                () -> assertMalformed(validPricing().replace("CS2103\t20", "cs2103\t20")),
                () -> assertMalformed(validPricing().replace("CS2103\t20", "-CS2103\t20")),
                () -> assertMalformed(validPricing().replace("CS2103\t20", "A23456789012345678901234567890123\t20")),
                () -> assertMalformed(validPricing() + "PROMOTION\tCS2103\t50\n"));
    }

    @Test
    void parse_sourceReadFailure_wrapsCause() {
        IOException cause = new IOException("simulated read failure");

        PricingStorageException exception = assertThrows(
                PricingStorageException.class,
                () -> parser.parse(new FailingReader(cause), "failing pricing"));

        assertEquals(cause, exception.getCause());
    }

    private Pricing parse(String source) throws PricingStorageException {
        return parser.parse(new StringReader(source), "test pricing");
    }

    private void assertMalformed(String source) {
        PricingStorageException exception = assertThrows(
                PricingStorageException.class, () -> parse(source));
        assertTrue(exception.getMessage().contains("Malformed pricing"));
    }

    private String validPricing() {
        return """
                CINECLI-PRICING\t1
                TICKET_PRICE\tADULT\t11.00
                TICKET_PRICE\tSENIOR\t4.50
                TICKET_PRICE\tSTUDENT\t7.00
                SNACK_PRICE\tPOPCORN\t5.00
                SNACK_PRICE\tNACHOS\t6.00
                SNACK_PRICE\tSOFT_DRINK\t3.00
                SNACK_PRICE\tPOPCORN_COMBO\t7.00
                SNACK_PRICE\tNACHOS_COMBO\t8.00
                PROMOTION\tCS2103\t20
                PROMOTION\tCS3227\t99
                """;
    }

    private static final class FailingReader extends Reader {
        private final IOException exception;

        private FailingReader(IOException exception) {
            this.exception = exception;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            throw exception;
        }

        @Override
        public void close() {
        }
    }
}
