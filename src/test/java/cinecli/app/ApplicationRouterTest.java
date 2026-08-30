package cinecli.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationRouterTest {
    private static final String DEFAULT_CATALOG_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_routesBetweenCustomerAndAdministratorModes() {
        StringWriter output = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        run("/admin\n0\n/customer\n/exit\n", output, errorOutput);

        assertAll(
                () -> assertTrue(output.toString().contains("Welcome to CineCLI")),
                () -> assertTrue(output.toString().contains("Administrator Home")),
                () -> assertEquals("", errorOutput.toString()));
    }

    @Test
    void run_adminCommandFromAdministratorMode_restartsHomepage() {
        StringWriter output = new StringWriter();

        run("/admin\n/admin\n/exit\n", output, new StringWriter());

        assertEquals(2, output.toString().split("Administrator Home", -1).length - 1);
    }

    @Test
    void run_endOfInputInEachRole_terminates() {
        StringWriter customerOutput = new StringWriter();
        StringWriter administratorOutput = new StringWriter();

        run("", customerOutput, new StringWriter());
        run("/admin\n", administratorOutput, new StringWriter());

        assertAll(
                () -> assertTrue(customerOutput.toString().contains("Welcome to CineCLI")),
                () -> assertTrue(administratorOutput.toString().contains("Administrator Home")));
    }

    @Test
    void run_unrecoverableJournal_reportsErrorWithoutDispatchingRole() throws Exception {
        Path catalog = tempDirectory.resolve("catalog.tsv");
        Files.writeString(
                catalog.resolveSibling("catalog-transaction.journal"), "malformed\n", UTF_8);
        StringWriter output = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        ApplicationRouter router = new ApplicationRouter(
                new Utf8Terminal(new StringReader(""), output, errorOutput),
                catalog,
                tempDirectory.resolve("seats.tsv"),
                tempDirectory.resolve("pricing.tsv"),
                DEFAULT_CATALOG_RESOURCE);
        router.run();

        assertAll(
                () -> assertEquals("", output.toString()),
                () -> assertTrue(errorOutput.toString()
                        .contains("Unable to recover pending catalogue changes:")));
    }

    private void run(String input, StringWriter output, StringWriter errorOutput) {
        Path catalog = tempDirectory.resolve("catalog-" + System.nanoTime() + ".tsv");
        ApplicationRouter router = new ApplicationRouter(
                new Utf8Terminal(new StringReader(input), output, errorOutput),
                catalog,
                catalog.resolveSibling("seats.tsv"),
                catalog.resolveSibling("pricing.tsv"),
                DEFAULT_CATALOG_RESOURCE);
        router.run();
    }
}
