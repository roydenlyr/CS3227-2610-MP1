package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnackMenuItemTest {
    @Test
    void fixedMenu_itemsHaveExpectedNamesAndCategories() {
        assertAll(
                () -> assertEquals("Popcorn", SnackMenuItem.POPCORN.getDisplayName()),
                () -> assertFalse(SnackMenuItem.POPCORN.isCombo()),
                () -> assertEquals("Nachos", SnackMenuItem.NACHOS.getDisplayName()),
                () -> assertFalse(SnackMenuItem.NACHOS.isCombo()),
                () -> assertEquals("Soft Drink", SnackMenuItem.SOFT_DRINK.getDisplayName()),
                () -> assertFalse(SnackMenuItem.SOFT_DRINK.isCombo()),
                () -> assertEquals(
                        "Popcorn Combo (Popcorn + Soft Drink)",
                        SnackMenuItem.POPCORN_COMBO.getDisplayName()),
                () -> assertTrue(SnackMenuItem.POPCORN_COMBO.isCombo()),
                () -> assertEquals(
                        "Nachos Combo (Nachos + Soft Drink)",
                        SnackMenuItem.NACHOS_COMBO.getDisplayName()),
                () -> assertTrue(SnackMenuItem.NACHOS_COMBO.isCombo()));
    }

    @Test
    void parse_everyDisplayedNumber_returnsMatchingItem() {
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            String menuNumber = Integer.toString(menuItem.getMenuNumber());
            assertEquals(menuItem, SnackMenuItem.parse(menuNumber));
        }
        assertEquals(SnackMenuItem.NACHOS, SnackMenuItem.parse(" 2 "));
    }

    @Test
    void parse_malformedNumbers_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("two")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("1.0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("2147483648")));
    }

    @Test
    void parse_outOfRangeNumbers_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SnackMenuItem.parse("6")));
    }
}
