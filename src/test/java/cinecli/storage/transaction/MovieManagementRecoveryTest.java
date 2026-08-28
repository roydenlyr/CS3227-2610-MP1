package cinecli.storage.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.MovieManagementApplication;
import cinecli.admin.MovieManagementOutcome;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.MovieManagementTerminal;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovieManagementRecoveryTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void run_validPendingRecovery_reportsBeforeListingOrTerminatesOnOutputFailure() throws Exception {
        Paths successfulPaths = writeState();
        createPendingJournal(successfulPaths);
        Terminal successfulTerminal = new Terminal("0");
        MovieManagementApplication successful = application(
                successfulPaths, transaction(successfulPaths), successfulTerminal);

        assertEquals(MovieManagementOutcome.BACK, successful.run());
        assertTrue(successfulTerminal.output().startsWith(
                "Pending movie deletion recovery completed.\nMovie Management"));
        assertFalse(Files.exists(successfulPaths.journal()));

        Paths failedOutputPaths = writeState();
        createPendingJournal(failedOutputPaths);
        Terminal failedTerminal = new Terminal("0");
        failedTerminal.failedWriteNumber = 1;
        MovieManagementApplication failed = application(
                failedOutputPaths, transaction(failedOutputPaths), failedTerminal);

        assertEquals(MovieManagementOutcome.TERMINATED, failed.run());
        assertEquals("Unable to write output. CineCLI will exit.\n", failedTerminal.errors());
    }

    @Test
    void run_deleteCommitStatuses_useDistinctMessages() throws Exception {
        Paths notAppliedPaths = writeState();
        Terminal notAppliedTerminal = new Terminal("3", "1", "Y");
        notAppliedTerminal.beforeRead = () -> {
            if (notAppliedTerminal.readCount == 3) {
                try {
                    Files.writeString(notAppliedPaths.catalog(), catalog() + "\n", UTF_8);
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
        };
        MovieManagementApplication notApplied = application(
                notAppliedPaths, transaction(notAppliedPaths), notAppliedTerminal);
        assertEquals(MovieManagementOutcome.BACK, notApplied.run());
        assertTrue(notAppliedTerminal.output().contains("Movie deletion was not applied: "));
        assertFalse(Files.exists(notAppliedPaths.journal()));

        Paths pendingPaths = writeState();
        MovieDeletionTransaction pendingTransaction = transaction(
                pendingPaths,
                (operation, path) -> {
                    if (operation == TransactionOperation.VERIFY_CATALOG) {
                        throw new IOException("simulated verification failure");
                    }
                });
        Terminal pendingTerminal = new Terminal("3", "1", "Y");
        MovieManagementApplication pending = application(
                pendingPaths, pendingTransaction, pendingTerminal);
        assertEquals(MovieManagementOutcome.BACK, pending.run());
        assertTrue(pendingTerminal.output().contains(
                "Movie deletion is confirmed but recovery is pending: "));
        assertTrue(Files.exists(pendingPaths.journal()));
    }

    private void createPendingJournal(Paths paths) throws Exception {
        MovieDeletionTransaction transaction = transaction(
                paths,
                (operation, path) -> {
                    if (operation == TransactionOperation.VERIFY_CATALOG) {
                        throw new IOException("interrupt after publication");
                    }
                });
        try {
            transaction.commit(transaction.prepare("MOV-1"));
        } catch (MovieDeletionCommitException exception) {
            assertEquals(MovieDeletionCommitException.Status.RECOVERY_PENDING, exception.status());
        }
    }

    private MovieManagementApplication application(
            Paths paths, MovieDeletionTransaction transaction, Terminal terminal) {
        return new MovieManagementApplication(
                new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE), transaction, terminal);
    }

    private MovieDeletionTransaction transaction(Paths paths) {
        return transaction(paths, TransactionOperationHook.NONE);
    }

    private MovieDeletionTransaction transaction(Paths paths, TransactionOperationHook hook) {
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(paths.seats());
        return new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, paths.catalog()),
                new SeatTransactionAdapter(seatStorage, paths.seats()),
                paths.journal(),
                hook);
    }

    private Paths writeState() throws Exception {
        Path runtime = tempDirectory.resolve(java.util.UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Paths paths = new Paths(
                runtime.resolve("catalog.tsv"),
                runtime.resolve("seats.tsv"),
                runtime.resolve("catalog-transaction.journal"));
        Files.writeString(paths.catalog(), catalog(), UTF_8);
        Files.writeString(paths.seats(), "CINECLI-SEATS\t1\n", UTF_8);
        return paths;
    }

    private String catalog() {
        return """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                """;
    }

    private static final class Terminal implements MovieManagementTerminal {
        private final Deque<TerminalInput> inputs;
        private final StringBuilder output = new StringBuilder();
        private final StringBuilder errors = new StringBuilder();
        private int readCount;
        private int writeCount;
        private int failedWriteNumber = -1;
        private Runnable beforeRead = () -> { };

        private Terminal(String... lines) {
            inputs = new ArrayDeque<>(Arrays.stream(lines).map(SubmittedLine::new).toList());
        }

        @Override
        public TerminalInput readLine() {
            readCount++;
            beforeRead.run();
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
            errors.append(text);
            return true;
        }

        private String output() {
            return output.toString();
        }

        private String errors() {
            return errors.toString();
        }
    }

    private record Paths(Path catalog, Path seats, Path journal) {
    }
}
