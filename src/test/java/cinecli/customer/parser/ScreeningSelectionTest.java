package cinecli.customer.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScreeningSelectionTest {
    @Test
    void parse_validCodes_returnsOneBasedPositions() {
        assertAll(
                () -> assertEquals(new ScreeningSelection(3, 2), ScreeningSelection.parse("3B")),
                () -> assertEquals(new ScreeningSelection(3, 2), ScreeningSelection.parse(" 3b ")),
                () -> assertEquals(new ScreeningSelection(12, 27), ScreeningSelection.parse("12AA")));
    }

    @Test
    void parse_malformedCodes_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("B3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("0A")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("3-B")));
    }

    @Test
    void formatTimingLabel_boundaryValues_returnsAlphabeticLabels() {
        assertAll(
                () -> assertEquals("A", ScreeningSelection.formatTimingLabel(1)),
                () -> assertEquals("Z", ScreeningSelection.formatTimingLabel(26)),
                () -> assertEquals("AA", ScreeningSelection.formatTimingLabel(27)));
    }

    @Test
    void constructor_belowMinimumValues_rejectsEachField() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ScreeningSelection(0, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ScreeningSelection(1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.formatTimingLabel(0)));
    }

    @Test
    void parse_valuesBeyondIntegerCapacity_reportsSpecificField() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("999999999999999999999A")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ScreeningSelection.parse("1ZZZZZZZZ")));
    }
}
