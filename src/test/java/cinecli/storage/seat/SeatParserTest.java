package cinecli.storage.seat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.SeatCoordinate;
import cinecli.storage.exception.SeatStorageException;
import java.io.StringReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeatParserTest {
    private static final Set<String> KNOWN_SCREENING_IDS = Set.of("SCR-001", "SCR_002");

    private final SeatParser seatParser = new SeatParser();

    @Test
    void parse_validFile_returnsImmutableCompleteState() throws SeatStorageException {
        String seatData = """
                \uFEFFCINECLI-SEATS\t1
                TAKEN_SEAT\tSCR_002\tG20
                TAKEN_SEAT\tSCR-001\tA10
                TAKEN_SEAT\tSCR-001\tA2
                """;

        Map<String, Set<SeatCoordinate>> takenSeats = parse(seatData);

        assertAll(
                () -> assertEquals(Set.of(
                        SeatCoordinate.parse("A2"),
                        SeatCoordinate.parse("A10")), takenSeats.get("SCR-001")),
                () -> assertEquals(Set.of(SeatCoordinate.parse("G20")), takenSeats.get("SCR_002")),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> takenSeats.put("NEW", Set.of())),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> takenSeats.get("SCR-001").add(SeatCoordinate.parse("B1"))));
    }

    @Test
    void parse_headerOnlyFile_returnsEmptyState() throws SeatStorageException {
        assertTrue(parse("CINECLI-SEATS\t1\n").isEmpty());
    }

    @Test
    void parse_readerFailure_preservesCause() {
        IOException cause = new IOException("simulated read failure");
        Reader failingReader = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw cause;
            }

            @Override
            public void close() {
                // Nothing to close.
            }
        };

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatParser.parse(failingReader, "failing seats", KNOWN_SCREENING_IDS));

        assertEquals(cause, exception.getCause());
    }

    @Test
    void isValidScreeningId_nullAndValid_coverBothShortCircuitOutcomes() {
        assertAll(
                () -> assertTrue(SeatParser.isValidScreeningId("SCR-1")),
                () -> org.junit.jupiter.api.Assertions.assertFalse(
                        SeatParser.isValidScreeningId(null)));
    }

    @ParameterizedTest
    @MethodSource("malformedSeatFiles")
    void parse_malformedFile_exceptionThrown(String seatData, String expectedMessagePart) {
        SeatStorageException exception = assertThrows(
                SeatStorageException.class, () -> parse(seatData));

        assertTrue(exception.getMessage().contains(expectedMessagePart));
    }

    private static Stream<Arguments> malformedSeatFiles() {
        return Stream.of(
                Arguments.of("", "missing seat occupancy header"),
                Arguments.of("NOT-SEATS\t1\n", "expected header"),
                Arguments.of("CINECLI-SEATS\t2\n", "unsupported seat occupancy format version"),
                Arguments.of("CINECLI-SEATS\t1\n\n", "blank records are not allowed"),
                Arguments.of("CINECLI-SEATS\t1\nOTHER\tSCR-001\tA1\n", "unknown record type"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\n", "requires 3 tab-separated fields"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\t_BAD\tA1\n", "screening ID must match"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-404\tA1\n", "unknown screening ID"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\ta1\n", "seat coordinate"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA0\n", "seat coordinate"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tG21\n", "seat coordinate"),
                Arguments.of("CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\t A1\n", "seat coordinate"),
                Arguments.of(
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA1\n"
                                + "TAKEN_SEAT\tSCR-001\tA1\n",
                        "duplicate taken seat"),
                Arguments.of(
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-001\tA1\n"
                                + "\uFEFFTAKEN_SEAT\tSCR-001\tA2\n",
                        "unknown record type"));
    }

    private Map<String, Set<SeatCoordinate>> parse(String seatData) throws SeatStorageException {
        return seatParser.parse(
                new StringReader(seatData), "test seat data", KNOWN_SCREENING_IDS);
    }
}
