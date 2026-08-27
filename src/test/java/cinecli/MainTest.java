package cinecli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path tempDirectory;

    @Test
    void main_endOfInput_usesProcessStreamsAndExitsCleanly() throws Exception {
        var originalInput = System.in;
        var originalOutput = System.out;
        var originalError = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(new PrintStream(output, true, UTF_8));
            System.setErr(new PrintStream(error, true, UTF_8));

            Main.main(new String[0]);
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
            System.setErr(originalError);
        }

        assertAll(
                () -> assertTrue(output.toString(UTF_8).contains("Welcome to CineCLI")),
                () -> assertEquals("", error.toString(UTF_8)));
    }

    @Test
    void run_missingRuntimeCatalog_initializesAndDisplaysSeed() throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("data/runtime/catalog.tsv");
        StringWriter output = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        Main.run(new StringReader("\n"), output, errorOutput, runtimeCatalog);

        String displayedOutput = output.toString();
        assertAll(
                () -> assertTrue(Files.exists(runtimeCatalog)),
                () -> assertTrue(displayedOutput.contains("Welcome to CineCLI")),
                () -> assertTrue(displayedOutput.contains("Press ENTER to proceed")),
                () -> assertTrue(displayedOutput.contains("Orbit of Echoes")),
                () -> assertTrue(displayedOutput.contains("Rating: PG13")),
                () -> assertTrue(displayedOutput.contains("29 Aug 2026, 13:30")),
                () -> assertEquals("", errorOutput.toString()),
                () -> assertTrue(Files.readString(runtimeCatalog, UTF_8)
                        .startsWith("CINECLI-CATALOG\t1")));
    }

    @Test
    void run_completeSeatSelection_initializesAndUpdatesTemporarySeatData() throws IOException {
        Path runtimeCatalog = tempDirectory.resolve("data/runtime/catalog.tsv");
        Path runtimeSeats = tempDirectory.resolve("data/runtime/seats.tsv");
        StringWriter output = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        Main.run(
                new StringReader("\n3A\nA1\nY\n3\n5\n2\n0\ncs3227\n"),
                output,
                errorOutput,
                runtimeCatalog,
                runtimeSeats);

        assertAll(
                () -> assertTrue(output.toString().contains("Movie: Crimson Harbor")),
                () -> assertTrue(output.toString().contains("Seats confirmed: A1")),
                () -> assertTrue(output.toString().contains(
                        "- A1: Student - S$7.00")),
                () -> assertTrue(output.toString().contains("Snack and Combo Menu")),
                () -> assertTrue(output.toString().contains(
                        "- 2 x Nachos Combo (Nachos + Soft Drink) - S$8.00 each")),
                () -> assertTrue(hasBillLine(output.toString(), "Promo Code:", "CS3227")),
                () -> assertTrue(hasBillLine(output.toString(), "Discount:", "99% OFF")),
                () -> assertTrue(hasBillLine(output.toString(), "TOTAL:", "S$0.23")),
                () -> assertEquals(
                        "CINECLI-SEATS\t1\nTAKEN_SEAT\tSCR-005\tA1\n",
                        Files.readString(runtimeSeats, UTF_8)),
                () -> assertEquals("", errorOutput.toString()));
    }

    private boolean hasBillLine(String output, String label, String value) {
        return output.lines()
                .anyMatch(line -> line.startsWith(label) && line.endsWith(value));
    }
}
