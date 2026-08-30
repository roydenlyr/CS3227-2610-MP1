package cinecli.storage.pricing;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import cinecli.storage.exception.PricingStorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses and validates version 1 CineCLI pricing files.
 */
final class PricingParser {
    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final String HEADER_NAME = "CINECLI-PRICING";
    private static final String FORMAT_VERSION = "1";
    private static final String TICKET_PRICE_RECORD = "TICKET_PRICE";
    private static final String SNACK_PRICE_RECORD = "SNACK_PRICE";
    private static final String PROMOTION_RECORD = "PROMOTION";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(0|[1-9][0-9]{0,4})\\.[0-9]{2}");
    private static final Pattern PROMOTION_CODE_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,31}");

    /**
     * Parses all records before returning a complete pricing state.
     *
     * @param source Pricing text source.
     * @param sourceDescription Description used in validation errors.
     * @return Complete validated pricing state.
     * @throws PricingStorageException If any record is malformed.
     */
    Pricing parse(Reader source, String sourceDescription) throws PricingStorageException {
        BufferedReader reader = source instanceof BufferedReader bufferedReader
                ? bufferedReader
                : new BufferedReader(source);
        Map<TicketType, Integer> ticketPrices = new EnumMap<>(TicketType.class);
        Map<SnackMenuItem, Integer> snackPrices = new EnumMap<>(SnackMenuItem.class);
        List<PromoCode> promotions = new ArrayList<>();

        try {
            parseHeader(reader.readLine(), sourceDescription);
            parseRecords(reader, sourceDescription, ticketPrices, snackPrices, promotions);
        } catch (IOException exception) {
            throw new PricingStorageException(
                    "Unable to read pricing '" + sourceDescription + "'.", exception);
        }

        try {
            return new Pricing(ticketPrices, snackPrices, promotions);
        } catch (IllegalArgumentException exception) {
            throw malformed(sourceDescription, exception.getMessage());
        }
    }

    private void parseHeader(String header, String sourceDescription) throws PricingStorageException {
        if (header == null) {
            throw malformed(sourceDescription, 1, "missing pricing header");
        }
        String normalizedHeader = header.startsWith(BYTE_ORDER_MARK)
                ? header.substring(BYTE_ORDER_MARK.length())
                : header;
        String[] fields = normalizedHeader.split("\\t", -1);
        if (fields.length != 2 || !HEADER_NAME.equals(fields[0])) {
            throw malformed(sourceDescription, 1,
                    "expected header '" + HEADER_NAME + "<TAB>" + FORMAT_VERSION + "'");
        }
        if (!FORMAT_VERSION.equals(fields[1])) {
            throw malformed(sourceDescription, 1,
                    "unsupported pricing format version '" + fields[1] + "'");
        }
    }

    private void parseRecords(
            BufferedReader reader,
            String sourceDescription,
            Map<TicketType, Integer> ticketPrices,
            Map<SnackMenuItem, Integer> snackPrices,
            List<PromoCode> promotions) throws IOException, PricingStorageException {
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                throw malformed(sourceDescription, lineNumber, "blank records are not allowed");
            }
            String[] fields = line.split("\\t", -1);
            switch (fields[0]) {
                case TICKET_PRICE_RECORD -> parseTicketPrice(
                        fields, sourceDescription, lineNumber, ticketPrices);
                case SNACK_PRICE_RECORD -> parseSnackPrice(
                        fields, sourceDescription, lineNumber, snackPrices);
                case PROMOTION_RECORD -> parsePromotion(
                        fields, sourceDescription, lineNumber, promotions);
                default -> throw malformed(sourceDescription, lineNumber,
                        "unknown record type '" + fields[0] + "'");
            }
        }
    }

    private void parseTicketPrice(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            Map<TicketType, Integer> ticketPrices) throws PricingStorageException {
        requireFieldCount(fields, TICKET_PRICE_RECORD, sourceDescription, lineNumber);
        TicketType ticketType = parseTicketType(fields[1], sourceDescription, lineNumber);
        int priceInCents = parsePriceInCents(fields[2], sourceDescription, lineNumber);
        if (ticketPrices.putIfAbsent(ticketType, priceInCents) != null) {
            throw malformed(sourceDescription, lineNumber,
                    "duplicate ticket identity '" + ticketType.name() + "'");
        }
    }

    private void parseSnackPrice(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            Map<SnackMenuItem, Integer> snackPrices) throws PricingStorageException {
        requireFieldCount(fields, SNACK_PRICE_RECORD, sourceDescription, lineNumber);
        SnackMenuItem menuItem = parseSnackMenuItem(fields[1], sourceDescription, lineNumber);
        int priceInCents = parsePriceInCents(fields[2], sourceDescription, lineNumber);
        if (snackPrices.putIfAbsent(menuItem, priceInCents) != null) {
            throw malformed(sourceDescription, lineNumber,
                    "duplicate snack identity '" + menuItem.name() + "'");
        }
    }

    private void parsePromotion(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            List<PromoCode> promotions) throws PricingStorageException {
        requireFieldCount(fields, PROMOTION_RECORD, sourceDescription, lineNumber);
        String code = fields[1];
        if (!PROMOTION_CODE_PATTERN.matcher(code).matches()) {
            throw malformed(sourceDescription, lineNumber,
                    "promotion code must use 1 to 32 uppercase letters, numbers, underscores, or hyphens");
        }
        int discountPercentage = parsePromotionPercentage(fields[2], sourceDescription, lineNumber);
        if (promotions.stream().anyMatch(promotion -> promotion.code().equals(code))) {
            throw malformed(sourceDescription, lineNumber,
                    "duplicate promotion code '" + code + "'");
        }
        promotions.add(new PromoCode(code, discountPercentage));
    }

    private void requireFieldCount(
            String[] fields, String recordType, String sourceDescription, int lineNumber)
            throws PricingStorageException {
        if (fields.length != 3) {
            throw malformed(sourceDescription, lineNumber,
                    recordType + " record requires 3 tab-separated fields");
        }
    }

    private TicketType parseTicketType(String value, String sourceDescription, int lineNumber)
            throws PricingStorageException {
        try {
            return TicketType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw malformed(sourceDescription, lineNumber,
                    "unknown ticket identity '" + value + "'");
        }
    }

    private SnackMenuItem parseSnackMenuItem(String value, String sourceDescription, int lineNumber)
            throws PricingStorageException {
        try {
            return SnackMenuItem.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw malformed(sourceDescription, lineNumber,
                    "unknown snack identity '" + value + "'");
        }
    }

    private int parsePriceInCents(String value, String sourceDescription, int lineNumber)
            throws PricingStorageException {
        if (!PRICE_PATTERN.matcher(value).matches()) {
            throw malformed(sourceDescription, lineNumber,
                    "price must use canonical money format from 0.01 through 9999.99");
        }
        int decimalPoint = value.indexOf('.');
        int priceInCents = Integer.parseInt(value.substring(0, decimalPoint)) * 100
                + Integer.parseInt(value.substring(decimalPoint + 1));
        if (priceInCents < 1 || priceInCents > 999_999) {
            throw malformed(sourceDescription, lineNumber,
                    "price must be from 0.01 through 9999.99");
        }
        return priceInCents;
    }

    private int parsePromotionPercentage(String value, String sourceDescription, int lineNumber)
            throws PricingStorageException {
        try {
            int discountPercentage = Integer.parseInt(value);
            if (discountPercentage < 1 || discountPercentage > 100) {
                throw malformed(sourceDescription, lineNumber,
                        "promotion percentage must be from 1 through 100");
            }
            return discountPercentage;
        } catch (NumberFormatException exception) {
            throw malformed(sourceDescription, lineNumber,
                    "promotion percentage must be a whole number from 1 through 100");
        }
    }

    private PricingStorageException malformed(
            String sourceDescription, int lineNumber, String reason) {
        return malformed(sourceDescription, "at line " + lineNumber + ": " + reason);
    }

    private PricingStorageException malformed(String sourceDescription, String reason) {
        return new PricingStorageException(
                "Malformed pricing '" + sourceDescription + "' " + reason + ".");
    }
}
