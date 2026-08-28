package cinecli.admin;

import java.util.regex.Pattern;

/** Interprets input rules shared by guided administrator workflows. */
final class AdminInputRules {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");

    enum Confirmation {
        CONFIRMED,
        CANCELLED,
        INVALID
    }

    Integer parseNumber(String input, int minimum, int maximum) {
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

    boolean isCancel(String input) {
        return input.strip().equalsIgnoreCase("/cancel");
    }

    Confirmation parseConfirmation(String input) {
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
