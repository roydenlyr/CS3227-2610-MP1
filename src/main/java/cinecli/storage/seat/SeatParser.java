package cinecli.storage.seat;

import cinecli.model.SeatCoordinate;
import cinecli.storage.exception.SeatStorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Parses and validates version 1 CineCLI seat occupancy files.
 */
final class SeatParser {
    static final String HEADER_LINE = "CINECLI-SEATS\t1";

    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final String HEADER_NAME = "CINECLI-SEATS";
    private static final String FORMAT_VERSION = "1";
    private static final String TAKEN_SEAT_RECORD = "TAKEN_SEAT";
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    /**
     * Parses all records before returning the complete seat state.
     *
     * @param source Seat occupancy text source.
     * @param sourceDescription Description used in validation errors.
     * @param knownScreeningIds Screening IDs accepted by the current catalog.
     * @return Immutable occupied seats grouped by screening ID.
     * @throws SeatStorageException If any record is malformed.
     */
    Map<String, Set<SeatCoordinate>> parse(
            Reader source,
            String sourceDescription,
            Set<String> knownScreeningIds) throws SeatStorageException {
        BufferedReader reader = source instanceof BufferedReader bufferedReader
                ? bufferedReader
                : new BufferedReader(source);
        Map<String, Set<SeatCoordinate>> takenSeatsByScreening = new LinkedHashMap<>();

        try {
            parseHeader(reader.readLine(), sourceDescription);
            parseRecords(reader, sourceDescription, knownScreeningIds, takenSeatsByScreening);
        } catch (IOException exception) {
            throw new SeatStorageException(
                    "Unable to read seat occupancy '" + sourceDescription + "'.", exception);
        }

        return immutableCopy(takenSeatsByScreening);
    }

    static boolean isValidScreeningId(String screeningId) {
        return screeningId != null && ID_PATTERN.matcher(screeningId).matches();
    }

    private void parseHeader(String header, String sourceDescription) throws SeatStorageException {
        if (header == null) {
            throw formatError(sourceDescription, 1, "missing seat occupancy header");
        }
        String normalizedHeader = header.startsWith(BYTE_ORDER_MARK)
                ? header.substring(BYTE_ORDER_MARK.length())
                : header;

        String[] fields = normalizedHeader.split("\t", -1);
        if (fields.length != 2 || !HEADER_NAME.equals(fields[0])) {
            throw formatError(sourceDescription, 1,
                    "expected header '" + HEADER_NAME + "<TAB>" + FORMAT_VERSION + "'");
        }
        if (!FORMAT_VERSION.equals(fields[1])) {
            throw formatError(sourceDescription, 1,
                    "unsupported seat occupancy format version '" + fields[1] + "'");
        }
    }

    private void parseRecords(
            BufferedReader reader,
            String sourceDescription,
            Set<String> knownScreeningIds,
            Map<String, Set<SeatCoordinate>> takenSeatsByScreening)
            throws IOException, SeatStorageException {
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                throw formatError(sourceDescription, lineNumber, "blank records are not allowed");
            }

            String[] fields = line.split("\t", -1);
            if (!TAKEN_SEAT_RECORD.equals(fields[0])) {
                throw formatError(sourceDescription, lineNumber,
                        "unknown record type '" + fields[0] + "'");
            }
            parseTakenRecord(
                    fields, sourceDescription, lineNumber, knownScreeningIds, takenSeatsByScreening);
        }
    }

    private void parseTakenRecord(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            Set<String> knownScreeningIds,
            Map<String, Set<SeatCoordinate>> takenSeatsByScreening) throws SeatStorageException {
        if (fields.length != 3) {
            throw formatError(sourceDescription, lineNumber,
                    TAKEN_SEAT_RECORD + " record requires 3 tab-separated fields");
        }

        String screeningId = fields[1];
        if (!isValidScreeningId(screeningId)) {
            throw formatError(sourceDescription, lineNumber,
                    "screening ID must match " + ID_PATTERN.pattern());
        }
        if (!knownScreeningIds.contains(screeningId)) {
            throw formatError(sourceDescription, lineNumber,
                    "unknown screening ID '" + screeningId + "'");
        }

        SeatCoordinate seat = parseSeatCoordinate(fields[2], sourceDescription, lineNumber);
        Set<SeatCoordinate> takenSeats = takenSeatsByScreening.computeIfAbsent(
                screeningId, ignored -> new LinkedHashSet<>());
        if (!takenSeats.add(seat)) {
            throw formatError(sourceDescription, lineNumber,
                    "duplicate taken seat '" + seat + "' for screening '" + screeningId + "'");
        }
    }

    private SeatCoordinate parseSeatCoordinate(
            String value, String sourceDescription, int lineNumber) throws SeatStorageException {
        try {
            return SeatCoordinate.parse(value);
        } catch (IllegalArgumentException exception) {
            throw formatError(sourceDescription, lineNumber,
                    "seat coordinate must use an uppercase row A-G and a number 1-20");
        }
    }

    private Map<String, Set<SeatCoordinate>> immutableCopy(
            Map<String, Set<SeatCoordinate>> takenSeatsByScreening) {
        Map<String, Set<SeatCoordinate>> immutableState = new TreeMap<>();
        for (Map.Entry<String, Set<SeatCoordinate>> entry : takenSeatsByScreening.entrySet()) {
            Set<SeatCoordinate> seats = Collections.unmodifiableSet(new TreeSet<>(entry.getValue()));
            immutableState.put(entry.getKey(), seats);
        }
        return Collections.unmodifiableMap(immutableState);
    }

    private SeatStorageException formatError(
            String sourceDescription, int lineNumber, String reason) {
        return new SeatStorageException(
                "Malformed seat occupancy '" + sourceDescription + "' at line " + lineNumber
                        + ": " + reason + ".");
    }
}
