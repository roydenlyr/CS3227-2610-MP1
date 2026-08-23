package cinecli.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a screening by its one-based movie and timing positions in the catalog.
 */
public record ScreeningSelection(int movieNumber, int timingNumber) {
    private static final Pattern CODE_PATTERN = Pattern.compile("([1-9][0-9]*)([A-Z]+)");
    private static final int ALPHABET_SIZE = 26;

    /**
     * Creates a catalog screening selection.
     *
     * @param movieNumber One-based movie number.
     * @param timingNumber One-based timing number.
     */
    public ScreeningSelection {
        if (movieNumber < 1) {
            throw new IllegalArgumentException("movie number must be at least 1");
        }
        if (timingNumber < 1) {
            throw new IllegalArgumentException("timing number must be at least 1");
        }
    }

    /**
     * Parses a case-insensitive catalog code such as {@code 3B}.
     *
     * @param value Catalog selection code.
     * @return Parsed selection.
     * @throws IllegalArgumentException If the code is malformed or too large.
     */
    public static ScreeningSelection parse(String value) {
        Objects.requireNonNull(value, "value");
        String normalizedValue = value.strip().toUpperCase(Locale.ROOT);
        Matcher matcher = CODE_PATTERN.matcher(normalizedValue);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "selection must contain a movie number followed by a timing letter, such as 3B");
        }

        int movieNumber;
        try {
            movieNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("movie number is too large", exception);
        }
        int timingNumber = parseTimingLabel(matcher.group(2));
        return new ScreeningSelection(movieNumber, timingNumber);
    }

    /**
     * Returns the alphabetic label for a one-based timing number.
     *
     * @param timingNumber One-based timing number.
     * @return Timing label such as {@code A}, {@code B}, or {@code AA}.
     */
    public static String formatTimingLabel(int timingNumber) {
        if (timingNumber < 1) {
            throw new IllegalArgumentException("timing number must be at least 1");
        }

        StringBuilder label = new StringBuilder();
        int remainingNumber = timingNumber;
        while (remainingNumber > 0) {
            remainingNumber--;
            label.append((char) ('A' + remainingNumber % ALPHABET_SIZE));
            remainingNumber /= ALPHABET_SIZE;
        }
        return label.reverse().toString();
    }

    private static int parseTimingLabel(String label) {
        int timingNumber = 0;
        try {
            for (int i = 0; i < label.length(); i++) {
                int letterValue = label.charAt(i) - 'A' + 1;
                timingNumber = Math.addExact(
                        Math.multiplyExact(timingNumber, ALPHABET_SIZE), letterValue);
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("timing label is too large", exception);
        }
        return timingNumber;
    }
}
