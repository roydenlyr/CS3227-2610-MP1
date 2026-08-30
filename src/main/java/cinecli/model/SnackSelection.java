package cinecli.model;

import java.util.Objects;

/**
 * Represents the quantity selected for one snack or combo.
 *
 * @param menuItem Selected menu item.
 * @param quantity Positive quantity of the item.
 * @param unitPriceInCents Captured unit snack or combo price in Singapore cents.
 */
public record SnackSelection(SnackMenuItem menuItem, int quantity, int unitPriceInCents) {
    private static final int MINIMUM_UNIT_PRICE_IN_CENTS = 1;
    private static final int MAXIMUM_UNIT_PRICE_IN_CENTS = 999_999;

    /**
     * Creates a quantity selection for one menu item.
     *
     * @throws NullPointerException If {@code menuItem} is null.
     * @throws IllegalArgumentException If {@code quantity} or unit price is invalid.
     */
    public SnackSelection {
        Objects.requireNonNull(menuItem, "menuItem");
        if (quantity < 1) {
            throw new IllegalArgumentException(getQuantityRequirement());
        }
        if (unitPriceInCents < MINIMUM_UNIT_PRICE_IN_CENTS
                || unitPriceInCents > MAXIMUM_UNIT_PRICE_IN_CENTS) {
            throw new IllegalArgumentException(getUnitPriceRequirement());
        }
    }

    /**
     * Parses a positive whole-number quantity.
     *
     * @param value Quantity to parse.
     * @return Parsed positive quantity.
     * @throws IllegalArgumentException If the value is not a positive whole number.
     */
    public static int parseQuantity(String value) {
        Objects.requireNonNull(value, "value");
        String normalizedValue = value.strip();

        int quantity;
        try {
            quantity = Integer.parseInt(normalizedValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(getQuantityRequirement(), exception);
        }
        if (quantity < 1) {
            throw new IllegalArgumentException(getQuantityRequirement());
        }
        return quantity;
    }

    private static String getQuantityRequirement() {
        return "quantity must be a whole number from 1 through " + Integer.MAX_VALUE;
    }

    private static String getUnitPriceRequirement() {
        return "unit price must be from " + MINIMUM_UNIT_PRICE_IN_CENTS + " through "
                + MAXIMUM_UNIT_PRICE_IN_CENTS + " cents";
    }
}
