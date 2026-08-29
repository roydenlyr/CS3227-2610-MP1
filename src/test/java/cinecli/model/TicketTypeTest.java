package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TicketTypeTest {
    @Test
    void fixedTypes_haveExpectedNames() {
        assertAll(
                () -> assertEquals("Adult", TicketType.ADULT.getDisplayName()),
                () -> assertEquals("Senior", TicketType.SENIOR.getDisplayName()),
                () -> assertEquals("Student", TicketType.STUDENT.getDisplayName()));
    }

    @Test
    void parse_everyDisplayedNumber_returnsMatchingType() {
        for (TicketType ticketType : TicketType.values()) {
            String menuNumber = Integer.toString(ticketType.getMenuNumber());
            assertEquals(ticketType, TicketType.parse(menuNumber));
        }
        assertEquals(TicketType.SENIOR, TicketType.parse(" 2 "));
    }

    @Test
    void parse_malformedNumbers_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("adult")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("1.0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("2147483648")));
    }

    @Test
    void parse_outOfRangeNumbers_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TicketType.parse("4")));
    }
}
