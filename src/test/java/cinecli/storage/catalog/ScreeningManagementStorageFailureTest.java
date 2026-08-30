package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.screenings.ScreeningManagementApplication;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import cinecli.storage.transaction.MovieDeletionTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreeningManagementStorageFailureTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_addOrEditSaveFailureReportsNotSavedAndPreservesCatalog() throws Exception {
        for (String[] inputs : new String[][] {
                {"1", "1", "2027-01-01", "10:00", "Y"},
                {"2", "1", "1", "2027-02-02", "3", "Y"}
        }) {
            Path catalogPath = tempDirectory.resolve(java.util.UUID.randomUUID() + ".tsv");
            String catalog = """
                    CINECLI-CATALOG\t1
                    MOVIE\tMOV-1\tFirst\tPG13
                    SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                    """;
            Files.writeString(catalogPath, catalog, UTF_8);
            byte[] original = Files.readAllBytes(catalogPath);
            Terminal terminal = new Terminal(inputs);

            assertEquals(AdminWorkflowOutcome.BACK,
                    application(failingSaveStorage(catalogPath), catalogPath, terminal).run());
            assertTrue(terminal.output.toString().contains("Screening change was not saved: "));
            assertArrayEquals(original, Files.readAllBytes(catalogPath));
        }
    }

    @Test
    void run_storageFailureOutputFailureTerminates() throws Exception {
        Path catalogPath = tempDirectory.resolve("output/catalog.tsv");
        Files.createDirectories(catalogPath.getParent());
        Files.writeString(catalogPath, "CINECLI-CATALOG\t1\nMOVIE\tMOV-1\tFirst\tPG13\n", UTF_8);
        Terminal terminal = new Terminal("1", "1", "2027-01-01", "10:00", "Y");
        terminal.failedWriteNumber = 6;

        assertEquals(AdminWorkflowOutcome.TERMINATED,
                application(failingSaveStorage(catalogPath), catalogPath, terminal).run());
    }

    private CatalogStorage failingSaveStorage(Path catalogPath) {
        return new CatalogStorage(catalogPath, DEFAULT_RESOURCE, (operation, path) -> {
            if (operation == CatalogStorageOperation.CREATE_TEMPORARY) {
                throw new IOException("simulated save failure");
            }
        });
    }

    private ScreeningManagementApplication application(
            CatalogStorage storage, Path catalogPath, Terminal terminal) {
        Path seatsPath = catalogPath.resolveSibling("seats.tsv");
        MovieDeletionTransaction transaction = new MovieDeletionTransaction(
                new CatalogTransactionAdapter(storage, catalogPath),
                new SeatTransactionAdapter(new SeatStorage(seatsPath), seatsPath),
                catalogPath.resolveSibling("catalog-transaction.journal"));
        return new ScreeningManagementApplication(storage, transaction, terminal);
    }

    private static final class Terminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final StringBuilder output = new StringBuilder();
        private int writeCount;
        private int failedWriteNumber = -1;

        private Terminal(String... lines) {
            inputs = new ArrayDeque<>(Arrays.stream(lines).map(SubmittedLine::new).toList());
        }

        @Override
        public TerminalInput readLine() {
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String text) {
            writeCount++;
            if (writeCount == failedWriteNumber) {
                return false;
            }
            output.append(text);
            return true;
        }

        @Override
        public boolean writeError(String text) {
            return true;
        }
    }
}
