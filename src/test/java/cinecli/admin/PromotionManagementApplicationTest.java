package cinecli.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.ui.EndOfInput;
import cinecli.admin.ui.GlobalCommand;
import cinecli.admin.ui.InputFailure;
import cinecli.admin.ui.SubmittedLine;
import cinecli.admin.ui.TerminalInput;
import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.storage.pricing.PricingStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromotionManagementApplicationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void run_listsPromotionsInPersistedOrder() throws Exception {
        Fixture fixture = fixtureWithPricingText("""
                CINECLI-PRICING\t1
                TICKET_PRICE\tADULT\t11.00
                SNACK_PRICE\tPOPCORN\t5.00
                PROMOTION\tSECOND\t20
                TICKET_PRICE\tSTUDENT\t7.00
                SNACK_PRICE\tNACHOS\t6.00
                TICKET_PRICE\tSENIOR\t4.50
                SNACK_PRICE\tSOFT_DRINK\t3.00
                PROMOTION\tFIRST\t99
                SNACK_PRICE\tPOPCORN_COMBO\t7.00
                SNACK_PRICE\tNACHOS_COMBO\t8.00
                """, lines("0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        String output = fixture.terminal().output();
        assertAll(
                () -> assertTrue(output.contains("1. SECOND - 20%")),
                () -> assertTrue(output.contains("2. FIRST - 99%")),
                () -> assertTrue(output.indexOf("1. SECOND") < output.indexOf("2. FIRST")));
    }

    @Test
    void run_addNormalizesCodeAndAcceptsPercentageBoundariesAfterInvalidValues() throws Exception {
        Fixture fixture = fixture(List.of(), lines(
                "1", "bad code", "  student-2026  ", "0", "101", "100", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        Pricing pricing = fixture.storage().load();
        assertAll(
                () -> assertEquals(List.of(new PromoCode("STUDENT-2026", 100)), pricing.promotions()),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter a code of 1 to 32 ASCII letters")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Enter a whole discount percentage from 1 through 100.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Promotion added: STUDENT-2026 (100%).")));
    }

    @Test
    void run_addOnePercentThenCancelAtConfirmation_preservesBytes() throws Exception {
        Fixture fixture = fixture(List.of(), lines("1", "one", "1", "N", "0"));
        byte[] original = Files.readAllBytes(fixture.pricingPath());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.pricingPath())),
                () -> assertTrue(fixture.terminal().output().contains("Discount: 1%")),
                () -> assertTrue(fixture.terminal().output().contains("Promotion addition cancelled.")));
    }

    @Test
    void run_editRenamesAndChangesPercentageIndependently() throws Exception {
        Fixture fixture = fixture(List.of(new PromoCode("OLD", 20)), lines(
                "2", "1", "1", "new", "2", "100", "3", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertEquals(List.of(new PromoCode("NEW", 100)), fixture.storage().load().promotions()),
                () -> assertTrue(fixture.terminal().output().contains("OLD -> NEW")),
                () -> assertTrue(fixture.terminal().output().contains("20% -> 100%")));
    }

    @Test
    void run_editRejectsCaseInsensitiveRenameCollisionAndCancellationPreservesBytes()
            throws Exception {
        Fixture fixture = fixture(List.of(
                new PromoCode("FIRST", 20), new PromoCode("SECOND", 30)),
                lines("2", "1", "1", "second", "/cancel", "0"));
        byte[] original = Files.readAllBytes(fixture.pricingPath());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "A promotion with that code already exists.")),
                () -> assertTrue(fixture.terminal().output().contains("Promotion edit cancelled.")),
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.pricingPath())));
    }

    @Test
    void run_deleteShowsDestructivePreviewAndPersistsConfirmedRemoval() throws Exception {
        Fixture fixture = fixture(List.of(new PromoCode("REMOVE", 25)), lines("3", "1", "Y", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.storage().load().promotions().isEmpty()),
                () -> assertTrue(fixture.terminal().output().contains(
                        "WARNING: This promotion will be removed.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "Promotion deleted: REMOVE (25%).")));
    }

    @Test
    void run_outputFailureAfterConfirmedMutationTerminates() throws Exception {
        Fixture added = fixture(List.of(), lines("1", "NEW", "20", "Y"));
        added.terminal().failWriteNumber(5);

        Fixture edited = fixture(List.of(new PromoCode("EDIT", 20)), lines(
                "2", "1", "2", "25", "3", "Y"));
        edited.terminal().failWriteNumber(7);

        Fixture deleted = fixture(List.of(new PromoCode("DELETE", 20)), lines("3", "1", "Y"));
        deleted.terminal().failWriteNumber(4);

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, added.application().run()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, edited.application().run()),
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, deleted.application().run()));
    }

    @Test
    void run_emptyActionOutputFailureTerminates() throws Exception {
        Fixture fixture = fixture(List.of(), lines("2"));
        fixture.terminal().failWriteNumber(2);

        assertEquals(AdminWorkflowOutcome.TERMINATED, fixture.application().run());
    }

    @Test
    void run_emptyActionsAndInvalidAction_keepThePricingFileUnchanged() throws Exception {
        Fixture fixture = fixture(List.of(), lines("", "4", "2", "3", "0"));
        byte[] original = Files.readAllBytes(fixture.pricingPath());

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertEquals(2, occurrences(
                        fixture.terminal().output(), "Enter 0, 1, 2, or 3.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "There are no promotions to edit.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "There are no promotions to delete.")),
                () -> assertArrayEquals(original, Files.readAllBytes(fixture.pricingPath())));
    }

    @Test
    void run_editSingleFieldsThenNoOp_showsAllPreviewAndUnchangedAlternatives() throws Exception {
        Fixture fixture = fixture(List.of(new PromoCode("OLD", 20)), lines(
                "2", "1", "1", "new", "3", "Y",
                "2", "1", "2", "21", "3", "Y",
                "2", "1", "1", "new", "2", "21", "3", "0", "0"));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertEquals(List.of(new PromoCode("NEW", 21)), fixture.storage().load().promotions()),
                () -> assertTrue(fixture.terminal().output().contains("Discount: 20% (unchanged)")),
                () -> assertTrue(fixture.terminal().output().contains("Code: NEW (unchanged)")),
                () -> assertTrue(fixture.terminal().output().contains("The promotion code is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains(
                        "The promotion percentage is unchanged.")),
                () -> assertTrue(fixture.terminal().output().contains("No promotion changes have been made.")),
                () -> assertTrue(fixture.terminal().output().contains("Promotion edit cancelled.")));
    }

    @Test
    void run_cancellationAtEveryPromotionStep_preservesOriginalBytes() throws Exception {
        List<List<TerminalInput>> inputs = List.of(
                lines("1", "/cancel", "0"),
                lines("1", "new", "/cancel", "0"),
                lines("1", "new", "1", "/cancel", "0"),
                lines("2", "/cancel", "0"),
                lines("2", "0", "0"),
                lines("2", "1", "/cancel", "0"),
                lines("2", "1", "1", "/cancel", "0"),
                lines("2", "1", "2", "/cancel", "0"),
                lines("2", "1", "1", "new", "3", "/cancel", "0"),
                lines("3", "/cancel", "0"),
                lines("3", "0", "0"),
                lines("3", "1", "/cancel", "0"));

        for (List<TerminalInput> input : inputs) {
            Fixture fixture = fixture(List.of(new PromoCode("OLD", 20)), input);
            byte[] original = Files.readAllBytes(fixture.pricingPath());

            assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.pricingPath()));
        }
    }

    @Test
    void run_propagatesTypedGlobalOutcomesFromEveryNestedPromptWithoutSaving() throws Exception {
        List<List<TerminalInput>> prefixes = List.of(
                List.of(),
                lines("1"),
                lines("1", "new"),
                lines("1", "new", "1"),
                lines("2"),
                lines("2", "1"),
                lines("2", "1", "1"),
                lines("2", "1", "2"),
                lines("2", "1", "1", "new", "3"),
                lines("3"),
                lines("3", "1"));

        for (List<TerminalInput> prefix : prefixes) {
            List<TerminalInput> input = new ArrayList<>(prefix);
            input.add(new GlobalCommand(GlobalCommand.Type.CUSTOMER));
            Fixture fixture = fixture(List.of(new PromoCode("OLD", 20)), input);
            byte[] original = Files.readAllBytes(fixture.pricingPath());

            assertEquals(AdminWorkflowOutcome.CUSTOMER, fixture.application().run());
            assertArrayEquals(original, Files.readAllBytes(fixture.pricingPath()));
        }
    }

    @Test
    void run_inputAndLoadFailuresAreFailClosed() throws Exception {
        Fixture failedInput = fixture(List.of(), List.of(new InputFailure()));
        assertEquals(AdminWorkflowOutcome.TERMINATED, failedInput.application().run());
        assertEquals("Unable to read input. CineCLI will exit.\n", failedInput.terminal().errors());

        Fixture malformed = fixture(List.of(), lines("0"));
        byte[] original = "malformed\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(malformed.pricingPath(), original);
        assertEquals(AdminWorkflowOutcome.BACK, malformed.application().run());
        assertAll(
                () -> assertTrue(malformed.terminal().output().startsWith(
                        "Unable to access promotion management: ")),
                () -> assertArrayEquals(original, Files.readAllBytes(malformed.pricingPath())));
    }

    @Test
    void run_saveFailureReportsNoFalseSuccess() throws Exception {
        Fixture fixture = fixture(List.of(), lines("1", "new", "1", "Y"));
        fixture.terminal().runBeforeWriteNumber(4, () -> makePricingParentAFile(fixture.pricingPath()));

        assertEquals(AdminWorkflowOutcome.BACK, fixture.application().run());

        assertAll(
                () -> assertTrue(fixture.terminal().output().contains(
                        "Promotion change was not saved: ")),
                () -> assertFalse(fixture.terminal().output().contains("Promotion added: NEW")));
    }

    @Test
    void run_editAndDeleteSaveFailuresReportNoFalseSuccess() throws Exception {
        Fixture edit = fixture(List.of(new PromoCode("OLD", 20)), lines(
                "2", "1", "1", "new", "3", "Y"));
        edit.terminal().runBeforeWriteNumber(5, () -> makePricingParentAFile(edit.pricingPath()));

        Fixture delete = fixture(List.of(new PromoCode("OLD", 20)), lines("3", "1", "Y"));
        delete.terminal().runBeforeWriteNumber(3, () -> makePricingParentAFile(delete.pricingPath()));

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.BACK, edit.application().run()),
                () -> assertTrue(edit.terminal().output().contains(
                        "Promotion change was not saved: ")),
                () -> assertFalse(edit.terminal().output().contains("Promotion updated: NEW")),
                () -> assertEquals(AdminWorkflowOutcome.BACK, delete.application().run()),
                () -> assertTrue(delete.terminal().output().contains(
                        "Promotion change was not saved: ")),
                () -> assertFalse(delete.terminal().output().contains("Promotion deleted: OLD")));
    }

    @Test
    void run_storageFailureOutputFailure_terminates() throws Exception {
        Fixture fixture = fixture(List.of(), lines("1", "new", "1", "Y"));
        fixture.terminal().runBeforeWriteNumber(4, () -> makePricingParentAFile(fixture.pricingPath()));
        fixture.terminal().failWriteNumber(5);

        assertAll(
                () -> assertEquals(AdminWorkflowOutcome.TERMINATED, fixture.application().run()),
                () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                        fixture.terminal().errors()));
    }

    @Test
    void run_outputFailureAtEveryRenderedBlock_terminates() throws Exception {
        assertEveryOutputFailureTerminates(List.of(),
                "bad", "1", "bad code", "new", "0", "1", "maybe", "N", "0");
        assertEveryOutputFailureTerminates(List.of(new PromoCode("OLD", 20)),
                "2", "bad", "1", "bad", "1", "bad code", "new", "2", "0", "20",
                "3", "maybe", "N", "0");
        assertEveryOutputFailureTerminates(List.of(
                new PromoCode("OLD", 20), new PromoCode("OTHER", 30)),
                "2", "1", "1", "old", "2", "20", "3", "1", "other", "new", "2", "20",
                "3", "N", "0");
        assertEveryOutputFailureTerminates(List.of(new PromoCode("OLD", 20)),
                "3", "bad", "1", "maybe", "N", "0");
    }

    private void assertEveryOutputFailureTerminates(List<PromoCode> promotions, String... input)
            throws Exception {
        Fixture baseline = fixture(promotions, lines(input));
        assertEquals(AdminWorkflowOutcome.BACK, baseline.application().run());
        for (int failedWrite = 1; failedWrite <= baseline.terminal().writeCount(); failedWrite++) {
            Fixture fixture = fixture(promotions, lines(input));
            fixture.terminal().failWriteNumber(failedWrite);

            assertAll(
                    () -> assertEquals(AdminWorkflowOutcome.TERMINATED, fixture.application().run()),
                    () -> assertEquals("Unable to write output. CineCLI will exit.\n",
                            fixture.terminal().errors()));
        }
    }

    private int occurrences(String output, String fragment) {
        return output.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private void makePricingParentAFile(Path pricingPath) {
        try {
            Files.delete(pricingPath);
            Files.delete(pricingPath.getParent());
            Files.writeString(pricingPath.getParent(), "not a directory\n");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private Fixture fixture(List<PromoCode> promotions, List<TerminalInput> inputs) throws Exception {
        Path pricingPath = tempDirectory.resolve(UUID.randomUUID().toString()).resolve("pricing.tsv");
        Files.createDirectories(pricingPath.getParent());
        Pricing initial = PricingReplacements.withPromotions(Pricing.defaults(), promotions);
        new PricingStorage(pricingPath).save(initial);
        PricingStorage storage = new PricingStorage(pricingPath);
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        return new Fixture(
                new PromotionManagementApplication(storage, terminal), storage, terminal, pricingPath);
    }

    private Fixture fixtureWithPricingText(String pricingText, List<TerminalInput> inputs)
            throws Exception {
        Path pricingPath = tempDirectory.resolve(UUID.randomUUID().toString()).resolve("pricing.tsv");
        Files.createDirectories(pricingPath.getParent());
        Files.writeString(pricingPath, pricingText, UTF_8);
        PricingStorage storage = new PricingStorage(pricingPath);
        ScriptedTerminal terminal = new ScriptedTerminal(inputs);
        return new Fixture(
                new PromotionManagementApplication(storage, terminal), storage, terminal, pricingPath);
    }

    private List<TerminalInput> lines(String... values) {
        return Arrays.stream(values).map(SubmittedLine::new).map(TerminalInput.class::cast).toList();
    }

    private static final class ScriptedTerminal implements AdminTerminal {
        private final Deque<TerminalInput> inputs;
        private final List<String> outputs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int writeCount;
        private int failedWriteNumber = -1;
        private int beforeWriteNumber = -1;
        private Runnable beforeWrite;

        private ScriptedTerminal(List<TerminalInput> inputs) {
            this.inputs = new ArrayDeque<>(inputs);
        }

        @Override
        public TerminalInput readLine() {
            return inputs.isEmpty() ? new EndOfInput() : inputs.removeFirst();
        }

        @Override
        public boolean write(String output) {
            writeCount++;
            if (writeCount == beforeWriteNumber) {
                beforeWrite.run();
            }
            if (writeCount == failedWriteNumber) {
                return false;
            }
            outputs.add(output);
            return true;
        }

        @Override
        public boolean writeError(String output) {
            errors.add(output);
            return true;
        }

        private String output() {
            return String.join("", outputs);
        }

        private String errors() {
            return String.join("", errors);
        }

        private int writeCount() {
            return writeCount;
        }

        private void failWriteNumber(int writeNumber) {
            failedWriteNumber = writeNumber;
        }

        private void runBeforeWriteNumber(int writeNumber, Runnable action) {
            beforeWriteNumber = writeNumber;
            beforeWrite = action;
        }
    }

    private record Fixture(
            PromotionManagementApplication application,
            PricingStorage storage,
            ScriptedTerminal terminal,
            Path pricingPath) {
    }
}
