package cinecli.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Captures an applied promotion that discounts the complete bill.
 *
 * @param code Canonical promotion code.
 * @param discountPercentage Percentage discounted from the complete bill.
 */
public record PromoCode(String code, int discountPercentage) {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,31}");
    private static final int MINIMUM_DISCOUNT_PERCENTAGE = 1;
    private static final int MAXIMUM_DISCOUNT_PERCENTAGE = 100;

    /**
     * Creates an applied promotion snapshot.
     *
     * @throws NullPointerException If {@code code} is null.
     * @throws IllegalArgumentException If the code or percentage is invalid.
     */
    public PromoCode {
        Objects.requireNonNull(code, "code");
        code = code.strip().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(getCodeRequirement());
        }
        if (discountPercentage < MINIMUM_DISCOUNT_PERCENTAGE
                || discountPercentage > MAXIMUM_DISCOUNT_PERCENTAGE) {
            throw new IllegalArgumentException(getPercentageRequirement());
        }
    }

    private static String getCodeRequirement() {
        return "promo code must be 1 through 32 ASCII letters, digits, underscores, or hyphens"
                + " and start with a letter or digit";
    }

    private static String getPercentageRequirement() {
        return "discount percentage must be from " + MINIMUM_DISCOUNT_PERCENTAGE + " through "
                + MAXIMUM_DISCOUNT_PERCENTAGE;
    }
}
