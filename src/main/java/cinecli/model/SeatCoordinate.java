package cinecli.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one seat in the fixed CineCLI seating layout.
 */
public record SeatCoordinate(char row, int number) implements Comparable<SeatCoordinate> {
    /** First row in the fixed layout. */
    public static final char FIRST_ROW = 'A';
    /** Last row in the fixed layout. */
    public static final char LAST_ROW = 'G';
    /** First seat number in each row. */
    public static final int FIRST_NUMBER = 1;
    /** Last seat number in each row. */
    public static final int LAST_NUMBER = 20;
    /** Total number of seats in the fixed layout. */
    public static final int TOTAL_SEATS =
            (LAST_ROW - FIRST_ROW + 1) * (LAST_NUMBER - FIRST_NUMBER + 1);
    private static final Pattern CANONICAL_PATTERN = Pattern.compile("[A-G](?:[1-9]|1[0-9]|20)");

    /**
     * Creates a validated seat coordinate.
     *
     * @param row Seat row from A through G.
     * @param number Seat number from 1 through 20.
     * @throws IllegalArgumentException If either component is outside the fixed layout.
     */
    public SeatCoordinate {
        if (row < FIRST_ROW || row > LAST_ROW) {
            throw new IllegalArgumentException("seat row must be from A through G");
        }
        if (number < FIRST_NUMBER || number > LAST_NUMBER) {
            throw new IllegalArgumentException("seat number must be from 1 through 20");
        }
    }

    /**
     * Parses an uppercase canonical coordinate such as G4.
     *
     * @param value Coordinate to parse.
     * @return Parsed coordinate.
     * @throws IllegalArgumentException If the value is not in canonical form.
     */
    public static SeatCoordinate parse(String value) {
        if (value == null || !CANONICAL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "seat coordinate must use a row A-G followed by a number 1-20, for example G4");
        }
        return new SeatCoordinate(value.charAt(0), Integer.parseInt(value.substring(1)));
    }

    @Override
    public int compareTo(SeatCoordinate other) {
        Objects.requireNonNull(other, "other");
        int rowComparison = Character.compare(row, other.row);
        return rowComparison != 0 ? rowComparison : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return Character.toString(row) + number;
    }
}
