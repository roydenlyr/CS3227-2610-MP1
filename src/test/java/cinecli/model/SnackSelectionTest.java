package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SnackSelectionTest {
    @Test
    void constructor_positiveQuantityAndPrice_createsPriceSnapshot() {
        SnackSelection selection = new SnackSelection(SnackMenuItem.POPCORN, 2, 500);

        assertAll(
                () -> assertEquals(SnackMenuItem.POPCORN, selection.menuItem()),
                () -> assertEquals(2, selection.quantity()),
                () -> assertEquals(500, selection.unitPriceInCents()),
                () -> assertEquals(1,
                        new SnackSelection(SnackMenuItem.POPCORN, 1, 1).unitPriceInCents()),
                () -> assertEquals(999_999,
                        new SnackSelection(SnackMenuItem.POPCORN, 1, 999_999)
                                .unitPriceInCents()));
    }

    @Test
    void constructor_invalidFields_exceptionThrown() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new SnackSelection(null, 1, 500)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, 0, 500)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, -1, 500)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, 1, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, 1, 1_000_000)));
    }

    @Test
    void parseQuantity_positiveWholeNumbers_returnsQuantity() {
        assertAll(
                () -> assertEquals(1, SnackSelection.parseQuantity("1")),
                () -> assertEquals(25, SnackSelection.parseQuantity(" 25 ")),
                () -> assertEquals(1, SnackSelection.parseQuantity("01")),
                () -> assertEquals(1, SnackSelection.parseQuantity("+1")),
                () -> assertEquals(
                        Integer.MAX_VALUE,
                        SnackSelection.parseQuantity(Integer.toString(Integer.MAX_VALUE))));
    }

    @Test
    void parseQuantity_malformedOrNonPositiveValues_exceptionThrown() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("two")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("1.5")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("0")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("-1")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SnackSelection.parseQuantity("2147483648")));
    }
}
