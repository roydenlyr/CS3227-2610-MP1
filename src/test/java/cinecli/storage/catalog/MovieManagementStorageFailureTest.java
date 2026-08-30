package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.movies.MovieManagementApplication;
import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.movies.MovieManagementTerminal;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.transaction.MovieDeletionTransaction;
import cinecli.storage.transaction.RecoveryResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovieManagementStorageFailureTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_addOrEditSaveFailure_reportsNotSavedAndPreservesCatalog() throws Exception {
        for (String[] inputs : new String[][] {
                {"1", "Added", "1", "Y"},
                {"2", "1", "1", "Changed", "3", "Y"}
        }) {
            Path catalogPath = tempDirectory.resolve(java.util.UUID.randomUUID() + ".tsv");
            String catalog = """
                    CINECLI-CATALOG\t1
                    MOVIE\tMOV-1\tFirst\tPG13
                    """;
            Files.writeString(catalogPath, catalog, UTF_8);
            byte[] original = Files.readAllBytes(catalogPath);
            CatalogStorage storage = failingSaveStorage(catalogPath);
            Terminal terminal = new Terminal(inputs);

            AdminWorkflowOutcome outcome = application(storage, catalogPath, terminal).run();

            assertEquals(AdminWorkflowOutcome.BACK, outcome);
            assertTrue(terminal.output.toString().contains("Movie change was not saved: "));
            assertArrayEquals(original, Files.readAllBytes(catalogPath));
        }
    }

    @Test
    void run_storageFailureMessageOutputFailure_terminates() throws Exception {
        Path catalogPath = tempDirectory.resolve("output/catalog.tsv");
        Files.createDirectories(catalogPath.getParent());
        Files.writeString(catalogPath, "CINECLI-CATALOG\t1\n", UTF_8);
        CatalogStorage storage = failingSaveStorage(catalogPath);
        Terminal terminal = new Terminal("1", "Added", "1", "Y");
        terminal.failedWriteNumber = 5;

        assertEquals(
                AdminWorkflowOutcome.TERMINATED,
                application(storage, catalogPath, terminal).run());
    }

    @Test
    void cascade_catalogAtomicStageFailure_isRecoveryPendingWithDurableJournal()
            throws Exception {
        for (CatalogStorageOperation failedOperation : new CatalogStorageOperation[] {
                CatalogStorageOperation.CREATE_TEMPORARY,
                CatalogStorageOperation.WRITE_TEMPORARY,
                CatalogStorageOperation.FORCE_TEMPORARY,
                CatalogStorageOperation.ATOMIC_REPLACE
        }) {
            Path runtime = tempDirectory.resolve("cascade-" + failedOperation);
            Files.createDirectories(runtime);
            Path catalogPath = runtime.resolve("catalog.tsv");
            Path seatsPath = runtime.resolve("seats.tsv");
            Path journalPath = runtime.resolve("catalog-transaction.journal");
            String catalog = """
                    CINECLI-CATALOG\t1
                    MOVIE\tMOV-1\tFirst\tPG13
                    SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                    """;
            Files.writeString(catalogPath, catalog, UTF_8);
            Files.writeString(seatsPath, "CINECLI-SEATS\t1\n", UTF_8);
            CatalogStorage storage = new CatalogStorage(
                    catalogPath,
                    DEFAULT_RESOURCE,
                    (operation, path) -> {
                        if (operation == failedOperation) {
                            throw new IOException("simulated " + failedOperation);
                        }
                    });
            MovieDeletionTransaction transaction = new MovieDeletionTransaction(
                    new CatalogTransactionAdapter(storage, catalogPath),
                    new SeatTransactionAdapter(new SeatStorage(seatsPath), seatsPath),
                    journalPath);

            MovieDeletionCommitException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    MovieDeletionCommitException.class,
                    () -> transaction.commit(transaction.prepare("MOV-1")));

            assertEquals(MovieDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
            assertTrue(Files.exists(journalPath));
            assertEquals(catalog, Files.readString(catalogPath, UTF_8));
            org.junit.jupiter.api.Assertions.assertThrows(
                    TransactionStorageException.class, transaction::recover);
            MovieDeletionTransaction retry = new MovieDeletionTransaction(
                    new CatalogTransactionAdapter(
                            new CatalogStorage(catalogPath, DEFAULT_RESOURCE), catalogPath),
                    new SeatTransactionAdapter(new SeatStorage(seatsPath), seatsPath),
                    journalPath);
            assertEquals(RecoveryResult.RECOVERED, retry.recover());
            assertFalse(Files.exists(journalPath));
        }
    }

    private CatalogStorage failingSaveStorage(Path catalogPath) {
        return new CatalogStorage(
                catalogPath,
                DEFAULT_RESOURCE,
                (operation, path) -> {
                    if (operation == CatalogStorageOperation.CREATE_TEMPORARY) {
                        throw new IOException("simulated save failure");
                    }
                });
    }

    private MovieManagementApplication application(
            CatalogStorage storage, Path catalogPath, Terminal terminal) {
        Path seatsPath = catalogPath.resolveSibling("seats.tsv");
        MovieDeletionTransaction transaction = new MovieDeletionTransaction(
                new CatalogTransactionAdapter(storage, catalogPath),
                new SeatTransactionAdapter(new SeatStorage(seatsPath), seatsPath),
                catalogPath.resolveSibling("catalog-transaction.journal"));
        return new MovieManagementApplication(storage, transaction, terminal);
    }

    private static final class Terminal implements MovieManagementTerminal {
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
