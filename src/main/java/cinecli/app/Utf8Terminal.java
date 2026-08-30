package cinecli.app;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapts UTF-8 character streams to the typed terminal contract shared by both roles.
 */
public final class Utf8Terminal implements AdminTerminal {
    private final BufferedReader input;
    private final Writer output;
    private final Writer errorOutput;

    /**
     * Creates a terminal adapter over the supplied character streams.
     *
     * @param input User input source.
     * @param output Normal output destination.
     * @param errorOutput Error output destination.
     */
    public Utf8Terminal(Reader input, Writer output, Writer errorOutput) {
        this.input = new BufferedReader(Objects.requireNonNull(input));
        this.output = Objects.requireNonNull(output);
        this.errorOutput = Objects.requireNonNull(errorOutput);
    }

    @Override
    public TerminalInput readLine() {
        try {
            String line = input.readLine();
            if (line == null) {
                return new EndOfInput();
            }
            return commandFor(line);
        } catch (IOException exception) {
            return new InputFailure();
        }
    }

    @Override
    public boolean write(String text) {
        return writeTo(output, text);
    }

    @Override
    public boolean writeError(String text) {
        return writeTo(errorOutput, text);
    }

    private TerminalInput commandFor(String line) {
        String normalizedLine = line.strip().toLowerCase(Locale.ROOT);
        return switch (normalizedLine) {
            case "/admin" -> new GlobalCommand(GlobalCommand.Type.ADMIN);
            case "/customer" -> new GlobalCommand(GlobalCommand.Type.CUSTOMER);
            case "/exit" -> new GlobalCommand(GlobalCommand.Type.EXIT);
            default -> new SubmittedLine(line);
        };
    }

    private boolean writeTo(Writer destination, String text) {
        try {
            destination.write(text);
            destination.flush();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
