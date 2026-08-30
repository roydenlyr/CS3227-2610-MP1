package cinecli.admin.screenings;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/** Interprets Screening-specific administrator input without performing terminal I/O. */
final class ScreeningInputRules {
    private static final Pattern DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern TIME_PATTERN = Pattern.compile("[0-9]{2}:[0-9]{2}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Parses a strict ISO local date.
     *
     * @param input Submitted date text.
     * @return Parsed date, or {@code null} when the input is invalid.
     */
    LocalDate parseDate(String input) {
        String normalized = input.strip();
        if (!DATE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * Parses a strict 24-hour local time.
     *
     * @param input Submitted time text.
     * @return Parsed time, or {@code null} when the input is invalid.
     */
    LocalTime parseTime(String input) {
        String normalized = input.strip();
        if (!TIME_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            return LocalTime.parse(normalized, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
