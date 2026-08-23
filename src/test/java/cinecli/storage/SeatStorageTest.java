package cinecli.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.SeatCoordinate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeatStorageTest {
    private static final Set<String> KNOWN_SCREENING_IDS = Set.of("SCR-001", "SCR-002");

    @TempDir
    Path tempDirectory;

    @Test
    void loadTakenSeats_missingFile_initializesHeaderOnlyFile() throws Exception {
        Path runtimeSeats = tempDirectory.resolve("data/runtime/seats.tsv");
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        Set<SeatCoordinate> takenSeats = seatStorage.loadTakenSeats(
                "SCR-001", KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertTrue(Files.exists(runtimeSeats)),
                () -> assertEquals("CINECLI-SEATS\t1\n", Files.readString(runtimeSeats, UTF_8)),
                () -> assertTrue(takenSeats.isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> takenSeats.add(SeatCoordinate.parse("A1"))));
    }

    @Test
    void confirmSeats_validSeats_roundTripsAndPreservesOtherScreening() throws Exception {
        SeatStorage seatStorage = new SeatStorage(runtimeSeatsPath());
        seatStorage.confirmSeats(
                "SCR-001",
                Set.of(SeatCoordinate.parse("G4"), SeatCoordinate.parse("A20")),
                KNOWN_SCREENING_IDS);
        seatStorage.confirmSeats(
                "SCR-002", Set.of(SeatCoordinate.parse("B3")), KNOWN_SCREENING_IDS);

        Set<SeatCoordinate> firstScreeningSeats = seatStorage.loadTakenSeats(
                "SCR-001", KNOWN_SCREENING_IDS);
        Set<SeatCoordinate> secondScreeningSeats = seatStorage.loadTakenSeats(
                "SCR-002", KNOWN_SCREENING_IDS);

        assertAll(
                () -> assertEquals(Set.of(
                        SeatCoordinate.parse("A20"),
                        SeatCoordinate.parse("G4")), firstScreeningSeats),
                () -> assertEquals(Set.of(SeatCoordinate.parse("B3")), secondScreeningSeats));
    }

    @Test
    void confirmSeats_unsortedState_writesCanonicalDeterministicOrder() throws Exception {
        SeatStorage seatStorage = new SeatStorage(runtimeSeatsPath());
        seatStorage.confirmSeats(
                "SCR-002",
                Set.of(
                        SeatCoordinate.parse("G20"),
                        SeatCoordinate.parse("A10"),
                        SeatCoordinate.parse("A2")),
                KNOWN_SCREENING_IDS);
        seatStorage.confirmSeats(
                "SCR-001",
                Set.of(
                        SeatCoordinate.parse("G1"),
                        SeatCoordinate.parse("B20"),
                        SeatCoordinate.parse("A1")),
                KNOWN_SCREENING_IDS);

        String expected = """
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                TAKEN_SEAT\tSCR-001\tB20
                TAKEN_SEAT\tSCR-001\tG1
                TAKEN_SEAT\tSCR-002\tA2
                TAKEN_SEAT\tSCR-002\tA10
                TAKEN_SEAT\tSCR-002\tG20
                """;

        assertEquals(expected, Files.readString(runtimeSeatsPath(), UTF_8));
    }

    @Test
    void confirmSeats_alreadyTakenSeat_rejectsEntireUpdateWithoutChangingFile() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                """);
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001",
                        Set.of(SeatCoordinate.parse("A1"), SeatCoordinate.parse("G20")),
                        KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("already taken")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)),
                () -> assertFalse(Files.readString(runtimeSeats, UTF_8).contains("G20")));
    }

    @Test
    void confirmSeats_malformedExistingFile_failsWithoutOverwritingIt() throws Exception {
        Path runtimeSeats = writeRuntimeSeats("not seat occupancy\n");
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.confirmSeats(
                        "SCR-001", Set.of(SeatCoordinate.parse("A1")), KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("Malformed seat occupancy")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void loadTakenSeats_malformedUnrelatedRecord_validatesWholeFileWithoutOverwritingIt()
            throws Exception {
        Path runtimeSeats = writeRuntimeSeats("""
                CINECLI-SEATS\t1
                TAKEN_SEAT\tSCR-001\tA1
                TAKEN_SEAT\tSCR-404\tB2
                """);
        byte[] originalBytes = Files.readAllBytes(runtimeSeats);
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);

        SeatStorageException exception = assertThrows(
                SeatStorageException.class,
                () -> seatStorage.loadTakenSeats("SCR-001", KNOWN_SCREENING_IDS));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("unknown screening ID 'SCR-404'")),
                () -> assertArrayEquals(originalBytes, Files.readAllBytes(runtimeSeats)));
    }

    @Test
    void loadAndConfirm_invalidArguments_rejectedBeforeFileInitialization() {
        Path runtimeSeats = runtimeSeatsPath();
        SeatStorage seatStorage = new SeatStorage(runtimeSeats);
        Set<SeatCoordinate> seatsWithNull = new HashSet<>();
        seatsWithNull.add(null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("", KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-404", KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-001", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.loadTakenSeats("SCR-001", Set.of("_BAD"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats("SCR-001", null, KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats("SCR-001", Set.of(), KNOWN_SCREENING_IDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> seatStorage.confirmSeats(
                                "SCR-001", seatsWithNull, KNOWN_SCREENING_IDS)),
                () -> assertFalse(Files.exists(runtimeSeats)));
    }

    private Path runtimeSeatsPath() {
        return tempDirectory.resolve("data/runtime/seats.tsv");
    }

    private Path writeRuntimeSeats(String content) throws IOException {
        Path runtimeSeats = runtimeSeatsPath();
        Files.createDirectories(runtimeSeats.getParent());
        Files.writeString(runtimeSeats, content, UTF_8);
        return runtimeSeats;
    }
}
