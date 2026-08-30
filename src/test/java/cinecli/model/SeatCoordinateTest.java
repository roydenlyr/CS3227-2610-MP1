package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeatCoordinateTest {
    @Test
    void constructor_boundaryCoordinates_created() {
        SeatCoordinate firstSeat = new SeatCoordinate('A', 1);
        SeatCoordinate lastSeat = new SeatCoordinate('G', 20);

        assertAll(
                () -> assertEquals('A', firstSeat.row()),
                () -> assertEquals(1, firstSeat.number()),
                () -> assertEquals("A1", firstSeat.toString()),
                () -> assertEquals("G20", lastSeat.toString()));
    }

    @Test
    void constructor_outOfRangeComponents_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SeatCoordinate('@', 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SeatCoordinate('H', 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SeatCoordinate('A', 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SeatCoordinate('A', 21)));
    }

    @Test
    void parse_canonicalCoordinates_parsed() {
        assertAll(
                () -> assertEquals(new SeatCoordinate('A', 1), SeatCoordinate.parse("A1")),
                () -> assertEquals(new SeatCoordinate('G', 20), SeatCoordinate.parse("G20")),
                () -> assertEquals("G4", SeatCoordinate.parse("G4").toString()));
    }

    @Test
    void parse_nonCanonicalOrOutOfRangeCoordinates_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("g4")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse(" G4")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("G04")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("A0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("G21")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SeatCoordinate.parse("H1")));
    }

    @Test
    void compareTo_mixedCoordinates_ordersByRowThenNumber() {
        List<SeatCoordinate> coordinates = List.of(
                SeatCoordinate.parse("G1"),
                SeatCoordinate.parse("A20"),
                SeatCoordinate.parse("B1"),
                SeatCoordinate.parse("A2"),
                SeatCoordinate.parse("A1"));

        List<SeatCoordinate> sortedCoordinates = coordinates.stream().sorted().toList();

        assertEquals(
                List.of(
                        SeatCoordinate.parse("A1"),
                        SeatCoordinate.parse("A2"),
                        SeatCoordinate.parse("A20"),
                        SeatCoordinate.parse("B1"),
                        SeatCoordinate.parse("G1")),
                sortedCoordinates);
    }
}
