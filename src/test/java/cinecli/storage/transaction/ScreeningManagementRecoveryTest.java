package cinecli.storage.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.screenings.ScreeningManagementApplication;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.app.ApplicationRouter;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.ScreeningDeletionCommitException;
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

class ScreeningManagementRecoveryTest {
    private static final String DEFAULT_RESOURCE = "/cinecli/default-catalog.tsv";

    @TempDir
    Path tempDirectory;

    @Test
    void router_recoversPendingScreeningDeletionBeforeCustomerAndAdministratorAccess() throws Exception {
        Paths paths = paths();
        MovieDeletionTransaction interrupted = transaction(paths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_CATALOG) {
                throw new IOException("simulated interruption");
            }
        });
        org.junit.jupiter.api.Assertions.assertThrows(ScreeningDeletionCommitException.class,
                () -> interrupted.commitScreening(interrupted.prepareScreening("SCR-1")));
        RouterTerminal terminal = new RouterTerminal(
                new GlobalCommand(GlobalCommand.Type.ADMIN),
                new SubmittedLine("2"),
                new GlobalCommand(GlobalCommand.Type.EXIT));

        router(paths, terminal).run();

        assertTrue(terminal.output().contains("Welcome to CineCLI"));
        assertTrue(terminal.output().contains("Administrator Home"));
        assertTrue(terminal.output().contains("Screening Management"));
        assertFalse(Files.exists(paths.journal()));
        assertFalse(Files.readString(paths.catalog(), UTF_8).contains("SCREENING\tSCR-1\t"));
    }

    @Test
    void router_recoveryFailureDoesNotDispatchWhenErrorOutputFails() throws Exception {
        Paths paths = paths();
        Files.writeString(paths.journal(), "malformed\n", UTF_8);
        RouterTerminal terminal = new RouterTerminal();
        terminal.failErrorWrites = true;

        router(paths, terminal).run();

        assertTrue(terminal.errors().contains("Unable to recover pending catalogue changes:"));
        assertEquals("", terminal.output());
    }

    @Test
    void run_commitFailuresReportBothDurableStatuses() throws Exception {
        Paths pendingPaths = paths();
        Terminal pendingTerminal = new Terminal("3", "1", "Y");
        MovieDeletionTransaction pendingTransaction = transaction(pendingPaths, (operation, path) -> {
            if (operation == TransactionOperation.VERIFY_CATALOG) {
                throw new IOException("simulated interruption");
            }
        });
        assertEquals(AdminWorkflowOutcome.BACK,
                application(pendingPaths, pendingTransaction, pendingTerminal).run());
        assertTrue(pendingTerminal.output.toString().contains(
                "Screening deletion is confirmed but recovery is pending: "));

        Paths stalePaths = paths();
        Terminal staleTerminal = new Terminal(() -> appendWhitespace(stalePaths.catalog()), "3", "1", "Y");
        assertEquals(AdminWorkflowOutcome.BACK,
                application(stalePaths, transaction(stalePaths), staleTerminal).run());
        assertTrue(staleTerminal.output.toString().contains("Screening deletion was not applied: "));
    }

    private ScreeningManagementApplication application(
            Paths paths, MovieDeletionTransaction transaction, Terminal terminal) {
        return new ScreeningManagementApplication(
                new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE), transaction, terminal);
    }

    private ApplicationRouter router(Paths paths, RouterTerminal terminal) {
        return new ApplicationRouter(
                terminal,
                paths.catalog(),
                paths.seats(),
                paths.catalog().resolveSibling("pricing.tsv"),
                DEFAULT_RESOURCE);
    }

    private MovieDeletionTransaction transaction(Paths paths) {
        return transaction(paths, TransactionOperationHook.NONE);
    }

    private MovieDeletionTransaction transaction(Paths paths, TransactionOperationHook hook) {
        CatalogStorage catalogStorage = new CatalogStorage(paths.catalog(), DEFAULT_RESOURCE);
        return new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, paths.catalog()),
                new SeatTransactionAdapter(new SeatStorage(paths.seats()), paths.seats()), paths.journal(), hook);
    }

    private Paths paths() throws Exception {
        Path runtime = tempDirectory.resolve(java.util.UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Path catalog = runtime.resolve("catalog.tsv");
        Files.writeString(catalog, """
                CINECLI-CATALOG\t1
                MOVIE\tMOV-1\tFirst\tPG13
                SCREENING\tSCR-1\tMOV-1\t2027-01-02\t21:30
                """, UTF_8);
        Files.writeString(runtime.resolve("seats.tsv"), "CINECLI-SEATS\t1\n", UTF_8);
        return new Paths(catalog, runtime.resolve("seats.tsv"),
                runtime.resolve("catalog-transaction.journal"));
    }

    private void appendWhitespace(Path catalogPath) {
        try {
            Files.writeString(catalogPath, Files.readString(catalogPath, UTF_8) + "\n", UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class Terminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final Runnable beforeConfirmation;
        private final StringBuilder output = new StringBuilder();
        private int writeCount;
        private int failedWriteNumber = -1;

        private Terminal(String... lines) {
            this(null, lines);
        }

        private Terminal(Runnable beforeConfirmation, String... lines) {
            this.beforeConfirmation = beforeConfirmation;
            inputs = new ArrayDeque<>(Arrays.stream(lines).map(SubmittedLine::new).toList());
        }

        @Override
        public TerminalInput readLine() {
            TerminalInput input = inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
            if (input instanceof SubmittedLine submitted && submitted.value().equals("Y")
                    && beforeConfirmation != null) {
                beforeConfirmation.run();
            }
            return input;
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

    private static final class RouterTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final StringBuilder output = new StringBuilder();
        private final StringBuilder errors = new StringBuilder();
        private boolean failErrorWrites;

        private RouterTerminal(TerminalInput... inputs) {
            this.inputs = new ArrayDeque<>(Arrays.asList(inputs));
        }

        @Override
        public TerminalInput readLine() {
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String text) {
            output.append(text);
            return true;
        }

        @Override
        public boolean writeError(String text) {
            errors.append(text);
            return !failErrorWrites;
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
