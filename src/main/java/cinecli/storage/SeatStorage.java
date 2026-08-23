package cinecli.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.SeatCoordinate;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Initializes, reads, and updates temporary screening seat occupancy.
 */
public final class SeatStorage {
    private final Path runtimeSeatsPath;
    private final SeatParser seatParser = new SeatParser();

    /**
     * Creates seat storage for a mutable runtime path.
     *
     * @param runtimeSeatsPath Mutable runtime seat occupancy path.
     */
    public SeatStorage(Path runtimeSeatsPath) {
        this.runtimeSeatsPath = Objects.requireNonNull(runtimeSeatsPath).toAbsolutePath().normalize();
    }

    /**
     * Loads occupied seats for one known screening after validating the complete file.
     *
     * @param screeningId Screening whose occupied seats are requested.
     * @param knownScreeningIds Screening IDs in the current catalog.
     * @return Immutable set of occupied seats for the requested screening.
     * @throws IllegalArgumentException If the requested screening or known ID set is invalid.
     * @throws SeatStorageException If initialization, reading, or validation fails.
     */
    public Set<SeatCoordinate> loadTakenSeats(
            String screeningId, Set<String> knownScreeningIds) throws SeatStorageException {
        Set<String> validatedKnownScreeningIds = validateKnownScreeningIds(knownScreeningIds);
        validateRequestedScreeningId(screeningId, validatedKnownScreeningIds);
        initializeIfMissing();

        Map<String, Set<SeatCoordinate>> takenSeatsByScreening = readAll(validatedKnownScreeningIds);
        return takenSeatsByScreening.getOrDefault(screeningId, Set.of());
    }

    /**
     * Confirms seats for one screening after validating and reloading the complete file.
     *
     * @param screeningId Screening receiving the confirmed seats.
     * @param seats Seats to confirm.
     * @param knownScreeningIds Screening IDs in the current catalog.
     * @throws IllegalArgumentException If a requested value is invalid.
     * @throws SeatStorageException If data is malformed, a seat is already taken, or writing fails.
     */
    public void confirmSeats(
            String screeningId,
            Set<SeatCoordinate> seats,
            Set<String> knownScreeningIds) throws SeatStorageException {
        Set<String> validatedKnownScreeningIds = validateKnownScreeningIds(knownScreeningIds);
        validateRequestedScreeningId(screeningId, validatedKnownScreeningIds);
        Set<SeatCoordinate> validatedSeats = validateSeats(seats);
        initializeIfMissing();

        Map<String, Set<SeatCoordinate>> persistedState = readAll(validatedKnownScreeningIds);
        Set<SeatCoordinate> unavailableSeats = new TreeSet<>(validatedSeats);
        unavailableSeats.retainAll(persistedState.getOrDefault(screeningId, Set.of()));
        if (!unavailableSeats.isEmpty()) {
            throw new SeatStorageException(
                    "Seats are already taken for screening '" + screeningId + "': " + unavailableSeats + ".");
        }

        Map<String, Set<SeatCoordinate>> updatedState = mutableCopy(persistedState);
        updatedState.computeIfAbsent(screeningId, ignored -> new TreeSet<>()).addAll(validatedSeats);
        writeAll(updatedState);
    }

    private Set<String> validateKnownScreeningIds(Set<String> knownScreeningIds) {
        if (knownScreeningIds == null) {
            throw new IllegalArgumentException("known screening IDs must not be null");
        }
        for (String knownScreeningId : knownScreeningIds) {
            if (!SeatParser.isValidScreeningId(knownScreeningId)) {
                throw new IllegalArgumentException(
                        "known screening IDs must use letters, numbers, underscores, or hyphens");
            }
        }
        return Set.copyOf(knownScreeningIds);
    }

    private void validateRequestedScreeningId(
            String screeningId, Set<String> knownScreeningIds) {
        if (!SeatParser.isValidScreeningId(screeningId)) {
            throw new IllegalArgumentException("screening ID is invalid");
        }
        if (!knownScreeningIds.contains(screeningId)) {
            throw new IllegalArgumentException("unknown screening ID '" + screeningId + "'");
        }
    }

    private Set<SeatCoordinate> validateSeats(Set<SeatCoordinate> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("seats must not be null or empty");
        }
        TreeSet<SeatCoordinate> validatedSeats = new TreeSet<>();
        for (SeatCoordinate seat : seats) {
            if (seat == null) {
                throw new IllegalArgumentException("seats must not contain null");
            }
            validatedSeats.add(seat);
        }
        return validatedSeats;
    }

    private void initializeIfMissing() throws SeatStorageException {
        if (Files.exists(runtimeSeatsPath)) {
            return;
        }

        try {
            Files.createDirectories(runtimeSeatsPath.getParent());
            Files.writeString(
                    runtimeSeatsPath,
                    SeatParser.HEADER_LINE + "\n",
                    UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            // Another caller initialized the file after the existence check.
        } catch (IOException exception) {
            throw new SeatStorageException(
                    "Unable to initialize seat occupancy '" + runtimeSeatsPath + "'.", exception);
        }
    }

    private Map<String, Set<SeatCoordinate>> readAll(Set<String> knownScreeningIds)
            throws SeatStorageException {
        try (BufferedReader reader = Files.newBufferedReader(runtimeSeatsPath, UTF_8)) {
            return seatParser.parse(reader, runtimeSeatsPath.toString(), knownScreeningIds);
        } catch (IOException exception) {
            throw new SeatStorageException(
                    "Unable to read seat occupancy '" + runtimeSeatsPath + "'.", exception);
        }
    }

    private Map<String, Set<SeatCoordinate>> mutableCopy(
            Map<String, Set<SeatCoordinate>> persistedState) {
        Map<String, Set<SeatCoordinate>> mutableState = new TreeMap<>();
        for (Map.Entry<String, Set<SeatCoordinate>> entry : persistedState.entrySet()) {
            mutableState.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return mutableState;
    }

    private void writeAll(Map<String, Set<SeatCoordinate>> takenSeatsByScreening)
            throws SeatStorageException {
        String serializedState = serialize(takenSeatsByScreening);
        Path temporaryPath = null;
        try {
            temporaryPath = Files.createTempFile(
                    runtimeSeatsPath.getParent(), "cinecli-seats-", ".tmp");
            Files.writeString(
                    temporaryPath,
                    serializedState,
                    UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            replaceWithTemporaryFile(temporaryPath);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new SeatStorageException(
                    "Unable to update seat occupancy '" + runtimeSeatsPath
                            + "' because the file system does not support atomic replacement."
                            + " The existing data was preserved.",
                    exception);
        } catch (IOException exception) {
            throw new SeatStorageException(
                    "Unable to update seat occupancy '" + runtimeSeatsPath + "'.", exception);
        } finally {
            deleteTemporaryFileIfPresent(temporaryPath);
        }
    }

    private String serialize(Map<String, Set<SeatCoordinate>> takenSeatsByScreening) {
        StringBuilder serializedState = new StringBuilder(SeatParser.HEADER_LINE).append('\n');
        Map<String, Set<SeatCoordinate>> sortedState = new TreeMap<>(takenSeatsByScreening);
        for (Map.Entry<String, Set<SeatCoordinate>> entry : sortedState.entrySet()) {
            for (SeatCoordinate seat : new TreeSet<>(entry.getValue())) {
                serializedState.append("TAKEN_SEAT\t")
                        .append(entry.getKey())
                        .append('\t')
                        .append(seat)
                        .append('\n');
            }
        }
        return serializedState.toString();
    }

    private void replaceWithTemporaryFile(Path temporaryPath) throws IOException {
        Files.move(
                temporaryPath,
                runtimeSeatsPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteTemporaryFileIfPresent(Path temporaryPath) {
        if (temporaryPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryPath);
        } catch (IOException exception) {
            // The update result is more important than cleanup of an uncommitted temporary file.
        }
    }
}
