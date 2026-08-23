package cinecli.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a customer demographic and its fixed ticket price.
 */
public enum TicketType {
    ADULT(1, "Adult", 1100),
    SENIOR(2, "Senior", 450),
    STUDENT(3, "Student", 700);

    private static final Pattern MENU_NUMBER_PATTERN = Pattern.compile("[1-9][0-9]*");

    private final int menuNumber;
    private final String displayName;
    private final int priceInCents;

    TicketType(int menuNumber, String displayName, int priceInCents) {
        this.menuNumber = menuNumber;
        this.displayName = displayName;
        this.priceInCents = priceInCents;
    }

    /**
     * Parses a displayed ticket type number.
     *
     * @param value Ticket type number to parse.
     * @return Matching ticket type.
     * @throws IllegalArgumentException If the value is malformed or not in the menu.
     */
    public static TicketType parse(String value) {
        Objects.requireNonNull(value, "value");
        String normalizedValue = value.strip();
        if (!MENU_NUMBER_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException(getSelectionRequirement());
        }

        int menuNumber;
        try {
            menuNumber = Integer.parseInt(normalizedValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(getSelectionRequirement(), exception);
        }

        for (TicketType ticketType : values()) {
            if (ticketType.menuNumber == menuNumber) {
                return ticketType;
            }
        }
        throw new IllegalArgumentException(getSelectionRequirement());
    }

    /**
     * Returns the number displayed beside this ticket type.
     *
     * @return One-based menu number.
     */
    public int getMenuNumber() {
        return menuNumber;
    }

    /**
     * Returns the customer-facing ticket type name.
     *
     * @return Display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the exact price in Singapore cents.
     *
     * @return Price in cents.
     */
    public int getPriceInCents() {
        return priceInCents;
    }

    private static String getSelectionRequirement() {
        return "selection must be a ticket type number from 1 through " + values().length;
    }
}
