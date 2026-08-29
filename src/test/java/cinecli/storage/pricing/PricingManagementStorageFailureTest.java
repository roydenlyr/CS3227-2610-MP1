package cinecli.storage.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.TicketPriceManagementApplication;
import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.Pricing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PricingManagementStorageFailureTest {
    @TempDir
    Path tempDirectory;

    @Test
    void run_ticketPriceSaveFailure_reportsNotSavedAndPreservesOriginalBytes() throws Exception {
        Path pricingPath = tempDirectory.resolve("runtime/pricing.tsv");
        new PricingStorage(pricingPath).save(Pricing.defaults());
        byte[] original = Files.readAllBytes(pricingPath);
        PricingStorage storage = new PricingStorage(
                pricingPath,
                (operation, path) -> {
                    if (operation == PricingStorageOperation.CREATE_TEMPORARY) {
                        throw new IOException("simulated save failure");
                    }
                });
        Terminal terminal = new Terminal("1", "1", "9.99", "Y");

        AdminWorkflowOutcome outcome = new TicketPriceManagementApplication(storage, terminal).run();

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.BACK, outcome),
                () -> assertTrue(terminal.output().contains("Ticket price change was not saved: ")),
                () -> assertTrue(terminal.output().contains("The existing data was preserved.")),
                () -> assertArrayEquals(original, Files.readAllBytes(pricingPath)));
    }

    private static final class Terminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final StringBuilder output = new StringBuilder();

        private Terminal(String... lines) {
            inputs = new ArrayDeque<>(Arrays.stream(lines).map(SubmittedLine::new).toList());
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
            return true;
        }

        private String output() {
            return output.toString();
        }
    }
}
