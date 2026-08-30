package cinecli.admin;

import java.util.regex.Pattern;

/** Interprets input rules shared by guided administrator workflows. */
public final class AdminInputRules {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");

    /** Represents the outcome of parsing a confirmation response. */
    public enum Confirmation {
        CONFIRMED,
        CANCELLED,
        INVALID
    }

    /** Creates the shared administrator input interpreter. */
    public AdminInputRules() {
    }

    /** Parses a whole number within the specified inclusive bounds. */
    public Integer parseNumber(String input, int minimum, int maximum) {
        String normalized = input.strip();
        if (!NUMBER_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return value >= minimum && value <= maximum ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Returns whether the input requests cancellation of the current operation. */
    public boolean isCancel(String input) {
        return input.strip().equalsIgnoreCase("/cancel");
    }

    /** Interprets a confirmation response. */
    public Confirmation parseConfirmation(String input) {
        String normalized = input.strip();
        if (normalized.equalsIgnoreCase("Y")) {
            return Confirmation.CONFIRMED;
        }
        if (normalized.equalsIgnoreCase("N") || normalized.equalsIgnoreCase("/cancel")) {
            return Confirmation.CANCELLED;
        }
        return Confirmation.INVALID;
    }
}
