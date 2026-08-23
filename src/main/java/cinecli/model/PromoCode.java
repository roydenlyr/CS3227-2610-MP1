package cinecli.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a fixed promotion that discounts the complete bill.
 */
public enum PromoCode {
    CS2103("CS2103", 20),
    CS3227("CS3227", 99);

    private final String code;
    private final int discountPercentage;

    PromoCode(String code, int discountPercentage) {
        this.code = code;
        this.discountPercentage = discountPercentage;
    }

    /**
     * Parses a case-insensitive promotion code.
     *
     * @param value Promotion code to parse.
     * @return Matching promotion.
     * @throws IllegalArgumentException If the code is not supported.
     */
    public static PromoCode parse(String value) {
        Objects.requireNonNull(value, "value");
        String normalizedValue = value.strip().toUpperCase(Locale.ROOT);
        for (PromoCode promoCode : values()) {
            if (promoCode.code.equals(normalizedValue)) {
                return promoCode;
            }
        }
        throw new IllegalArgumentException(getCodeRequirement());
    }

    /**
     * Returns the customer-facing promotion code.
     *
     * @return Promotion code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the percentage discounted from the complete bill.
     *
     * @return Discount percentage.
     */
    public int getDiscountPercentage() {
        return discountPercentage;
    }

    private static String getCodeRequirement() {
        return "promo code must be CS2103 or CS3227";
    }
}
