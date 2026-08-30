package cinecli.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.Test;

class Utf8TerminalTest {
    @Test
    void readLine_globalCommands_areTrimmedAndCaseInsensitive() {
        Utf8Terminal terminal = terminal(" /AdMiN \n/customer\n\t/EXIT\t\n");

        assertAll(
                () -> assertEquals(
                        new GlobalCommand(GlobalCommand.Type.ADMIN), terminal.readLine()),
                () -> assertEquals(
                        new GlobalCommand(GlobalCommand.Type.CUSTOMER), terminal.readLine()),
                () -> assertEquals(
                        new GlobalCommand(GlobalCommand.Type.EXIT), terminal.readLine()));
    }

    @Test
    void readLine_nonCommandsAndEndOfInput_preservesLineOrReturnsTypedResult() {
        Utf8Terminal terminal = terminal(" /admin-extra \n");

        assertAll(
                () -> assertEquals(new SubmittedLine(" /admin-extra "), terminal.readLine()),
                () -> assertInstanceOf(EndOfInput.class, terminal.readLine()));
    }

    @Test
    void readLine_readerFailure_returnsInputFailure() {
        Reader failingReader = new Reader() {
            @Override
            public int read(char[] characters, int offset, int length) throws IOException {
                throw new IOException("injected read failure");
            }

            @Override
            public void close() {
                // Nothing to close.
            }
        };
        Utf8Terminal terminal = new Utf8Terminal(
                failingReader, new StringWriter(), new StringWriter());

        assertInstanceOf(InputFailure.class, terminal.readLine());
    }

    @Test
    void write_destinations_flushSuccessfullyOrReportFailure() {
        StringWriter output = new StringWriter();
        StringWriter errorOutput = new StringWriter();
        Utf8Terminal terminal = new Utf8Terminal(new StringReader(""), output, errorOutput);
        Writer failingWriter = new Writer() {
            @Override
            public void write(char[] characters, int offset, int length) throws IOException {
                throw new IOException("injected write failure");
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("injected flush failure");
            }

            @Override
            public void close() {
                // Nothing to close.
            }
        };
        Utf8Terminal failingTerminal = new Utf8Terminal(
                new StringReader(""), failingWriter, failingWriter);

        assertAll(
                () -> assertTrue(terminal.write("normal output")),
                () -> assertTrue(terminal.writeError("error output")),
                () -> assertEquals("normal output", output.toString()),
                () -> assertEquals("error output", errorOutput.toString()),
                () -> assertFalse(failingTerminal.write("normal output")),
                () -> assertFalse(failingTerminal.writeError("error output")));
    }

    private Utf8Terminal terminal(String input) {
        return new Utf8Terminal(new StringReader(input), new StringWriter(), new StringWriter());
    }
}
