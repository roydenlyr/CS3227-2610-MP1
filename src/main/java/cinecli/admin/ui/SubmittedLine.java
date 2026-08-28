package cinecli.admin.ui;

import java.util.Objects;

/** A line submitted by the administrator. */
public record SubmittedLine(String value) implements TerminalInput {
    /** Creates a submitted line. */
    public SubmittedLine {
        Objects.requireNonNull(value);
    }
}
