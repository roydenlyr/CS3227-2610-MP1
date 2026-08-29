package cinecli.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents one fixed snack or combo available after seat confirmation.
 */
public enum SnackMenuItem {
    POPCORN(1, "Popcorn", false),
    NACHOS(2, "Nachos", false),
    SOFT_DRINK(3, "Soft Drink", false),
    POPCORN_COMBO(4, "Popcorn Combo (Popcorn + Soft Drink)", true),
    NACHOS_COMBO(5, "Nachos Combo (Nachos + Soft Drink)", true);

    private static final Pattern MENU_NUMBER_PATTERN = Pattern.compile("[1-9][0-9]*");

    private final int menuNumber;
    private final String displayName;
    private final boolean isCombo;

    SnackMenuItem(int menuNumber, String displayName, boolean isCombo) {
        this.menuNumber = menuNumber;
        this.displayName = displayName;
        this.isCombo = isCombo;
    }

    /**
     * Parses a menu item number such as {@code 2}.
     *
     * @param value Menu item number to parse.
     * @return Matching menu item.
     * @throws IllegalArgumentException If the value is malformed or not in the menu.
     */
    public static SnackMenuItem parse(String value) {
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

        for (SnackMenuItem menuItem : values()) {
            if (menuItem.menuNumber == menuNumber) {
                return menuItem;
            }
        }
        throw new IllegalArgumentException(getSelectionRequirement());
    }

    /**
     * Returns the number displayed beside this menu item.
     *
     * @return One-based menu number.
     */
    public int getMenuNumber() {
        return menuNumber;
    }

    /**
     * Returns the customer-facing item name.
     *
     * @return Display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns whether this item is a combo.
     *
     * @return True for a combo, or false for an individual snack.
     */
    public boolean isCombo() {
        return isCombo;
    }

    private static String getSelectionRequirement() {
        return "selection must be an item number from 1 through " + values().length;
    }
}
