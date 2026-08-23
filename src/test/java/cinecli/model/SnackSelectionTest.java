package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SnackSelectionTest {
    @Test
    void constructor_positiveQuantity_createsSelection() {
        SnackSelection selection = new SnackSelection(SnackMenuItem.POPCORN, 2);

        assertAll(
                () -> assertEquals(SnackMenuItem.POPCORN, selection.menuItem()),
                () -> assertEquals(2, selection.quantity()));
    }

    @Test
    void constructor_invalidFields_exceptionThrown() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new SnackSelection(null, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SnackSelection(SnackMenuItem.POPCORN, -1)));
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
