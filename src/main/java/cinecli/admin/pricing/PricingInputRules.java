package cinecli.admin.pricing;

import java.util.regex.Pattern;

/** Interprets exact money and percentage input for administrator pricing workflows. */
final class PricingInputRules {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(0|[1-9][0-9]{0,4})\\.[0-9]{2}");
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("[1-9][0-9]*");
    private static final int MINIMUM_PRICE_IN_CENTS = 1;
    private static final int MAXIMUM_PRICE_IN_CENTS = 999_999;
    private static final int MAXIMUM_PERCENTAGE = 100;

    /**
     * Parses a price with exactly two decimal places within the permitted range.
     *
     * @param input Submitted price text.
     * @return Price in cents, or {@code null} when the input is invalid.
     */
    Integer parsePriceInCents(String input) {
        String normalized = input.strip();
        if (!PRICE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        int decimalPoint = normalized.indexOf('.');
        int priceInCents = Integer.parseInt(normalized.substring(0, decimalPoint)) * 100
                + Integer.parseInt(normalized.substring(decimalPoint + 1));
        return priceInCents >= MINIMUM_PRICE_IN_CENTS && priceInCents <= MAXIMUM_PRICE_IN_CENTS
                ? priceInCents : null;
    }

    /**
     * Parses a whole discount percentage from one through one hundred.
     *
     * @param input Submitted percentage text.
     * @return Parsed percentage, or {@code null} when the input is invalid.
     */
    Integer parsePercentage(String input) {
        String normalized = input.strip();
        if (!PERCENTAGE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            int percentage = Integer.parseInt(normalized);
        return percentage <= MAXIMUM_PERCENTAGE ? percentage : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
