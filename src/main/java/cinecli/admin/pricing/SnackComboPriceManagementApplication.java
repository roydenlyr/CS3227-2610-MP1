package cinecli.admin.pricing;

import cinecli.admin.AdminInputRules;
import cinecli.admin.AdminInputRules.Confirmation;
import cinecli.admin.AdminWorkflowInput;
import cinecli.admin.AdminWorkflowInteraction;
import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.ui.AdminTerminal;
import cinecli.model.Pricing;
import cinecli.model.SnackMenuItem;
import cinecli.storage.exception.PricingStorageException;
import cinecli.storage.pricing.PricingStorage;
import java.util.Objects;

/** Coordinates administrator workflows for fixed snack and combo price changes. */
public final class SnackComboPriceManagementApplication {
    private final PricingStorage pricingStorage;
    private final AdminWorkflowInteraction interaction;
    private final AdminInputRules inputRules = new AdminInputRules();
    private final PricingInputRules pricingInputRules = new PricingInputRules();
    private final SnackComboPriceManagementText text = new SnackComboPriceManagementText();

    /** Creates the snack and combo price workflow. */
    public SnackComboPriceManagementApplication(PricingStorage pricingStorage, AdminTerminal terminal) {
        this.pricingStorage = Objects.requireNonNull(pricingStorage);
        this.interaction = new AdminWorkflowInteraction(Objects.requireNonNull(terminal));
    }

    /** Runs snack and combo price management until a navigation or termination outcome occurs. */
    public AdminWorkflowOutcome run() {
        while (true) {
            Pricing pricing;
            try {
                pricing = pricingStorage.load();
            } catch (PricingStorageException exception) {
                return reportStorageFailure(SnackComboPriceManagementText.ACCESS_FAILURE_PREFIX, exception);
            }
            if (!write(text.management(pricing))) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            AdminWorkflowOutcome outcome = chooseAction(pricing);
            if (outcome != null) {
                return outcome;
            }
        }
    }

    private AdminWorkflowOutcome chooseAction(Pricing pricing) {
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return input.outcome;
            }
            Integer choice = inputRules.parseNumber(input.line, 0, 1);
            if (choice == null) {
                if (!write(SnackComboPriceManagementText.ACTION_ERROR_WITH_PROMPT)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            return choice == 0 ? AdminWorkflowOutcome.BACK : editSnackPrice(pricing);
        }
    }

    private AdminWorkflowOutcome editSnackPrice(Pricing pricing) {
        TargetResult target = requestTarget();
        if (target.outcome != null) {
            return target.outcome;
        }
        if (target.isCancelled) {
            return cancel(SnackComboPriceManagementText.EDIT_CANCELLED);
        }
        SnackMenuItem menuItem = SnackMenuItem.values()[target.index];
        ValueResult<Integer> price = requestPrice(menuItem);
        if (price.outcome != null) {
            return price.outcome;
        }
        if (price.isCancelled) {
            return cancel(SnackComboPriceManagementText.EDIT_CANCELLED);
        }
        int originalPriceInCents = pricing.snackPriceInCents(menuItem);
        if (price.value == originalPriceInCents) {
            return write(SnackComboPriceManagementText.PRICE_UNCHANGED)
                    ? null : AdminWorkflowOutcome.TERMINATED;
        }
        Pricing intended = PricingReplacements.withSnackPrice(pricing, menuItem, price.value);
        ConfirmationResult confirmation = confirm(
                text.editPreview(menuItem, originalPriceInCents, price.value));
        if (confirmation.outcome != null) {
            return confirmation.outcome;
        }
        if (!confirmation.isConfirmed) {
            return cancel(SnackComboPriceManagementText.EDIT_CANCELLED);
        }
        try {
            pricingStorage.save(intended);
        } catch (PricingStorageException exception) {
            return reportStorageFailure(SnackComboPriceManagementText.CHANGE_NOT_SAVED_PREFIX, exception);
        }
        return write(text.updated(menuItem, price.value)) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private TargetResult requestTarget() {
        while (true) {
            if (!write(text.targetPrompt())) {
                return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return TargetResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return TargetResult.cancelled();
            }
            Integer choice = inputRules.parseNumber(input.line, 0, SnackMenuItem.values().length);
            if (choice == null) {
                if (!write(SnackComboPriceManagementText.TARGET_ERROR)) {
                    return TargetResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return choice == 0 ? TargetResult.cancelled() : TargetResult.index(choice - 1);
        }
    }

    private ValueResult<Integer> requestPrice(SnackMenuItem menuItem) {
        while (true) {
            if (!write(text.pricePrompt(menuItem))) {
                return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
            ReadResult input = read();
            if (input.outcome != null) {
                return ValueResult.outcome(input.outcome);
            }
            if (inputRules.isCancel(input.line)) {
                return ValueResult.cancelled();
            }
            Integer priceInCents = pricingInputRules.parsePriceInCents(input.line);
            if (priceInCents == null) {
                if (!write(SnackComboPriceManagementText.PRICE_ERROR)) {
                    return ValueResult.outcome(AdminWorkflowOutcome.TERMINATED);
                }
                continue;
            }
            return ValueResult.value(priceInCents);
        }
    }

    private ConfirmationResult confirm(String preview) {
        if (!write(preview)) {
            return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
        }
        while (true) {
            ReadResult input = read();
            if (input.outcome != null) {
                return ConfirmationResult.outcome(input.outcome);
            }
            Confirmation answer = inputRules.parseConfirmation(input.line);
            if (answer == Confirmation.CONFIRMED) {
                return ConfirmationResult.confirmed();
            }
            if (answer == Confirmation.CANCELLED) {
                return ConfirmationResult.cancelled();
            }
            if (!write(SnackComboPriceManagementText.CONFIRMATION_ERROR)) {
                return ConfirmationResult.outcome(AdminWorkflowOutcome.TERMINATED);
            }
        }
    }

    private ReadResult read() {
        AdminWorkflowInput input = interaction.read();
        return new ReadResult(input.line(), input.outcome());
    }

    private boolean write(String output) {
        return interaction.write(output);
    }

    private AdminWorkflowOutcome cancel(String message) {
        return write(message) ? null : AdminWorkflowOutcome.TERMINATED;
    }

    private AdminWorkflowOutcome reportStorageFailure(String prefix, PricingStorageException exception) {
        return write(text.storageFailure(prefix, exception))
                ? AdminWorkflowOutcome.BACK : AdminWorkflowOutcome.TERMINATED;
    }

    private record ReadResult(String line, AdminWorkflowOutcome outcome) {
    }

    private record TargetResult(int index, boolean isCancelled, AdminWorkflowOutcome outcome) {
        private static TargetResult index(int index) {
            return new TargetResult(index, false, null);
        }

        private static TargetResult cancelled() {
            return new TargetResult(-1, true, null);
        }

        private static TargetResult outcome(AdminWorkflowOutcome outcome) {
            return new TargetResult(-1, false, outcome);
        }
    }

    private static final class ValueResult<T> {
        private final T value;
        private final boolean isCancelled;
        private final AdminWorkflowOutcome outcome;

        private ValueResult(T value, boolean isCancelled, AdminWorkflowOutcome outcome) {
            this.value = value;
            this.isCancelled = isCancelled;
            this.outcome = outcome;
        }

        private static <T> ValueResult<T> value(T value) {
            return new ValueResult<>(value, false, null);
        }

        private static <T> ValueResult<T> cancelled() {
            return new ValueResult<>(null, true, null);
        }

        private static <T> ValueResult<T> outcome(AdminWorkflowOutcome outcome) {
            return new ValueResult<>(null, false, outcome);
        }
    }

    private record ConfirmationResult(boolean isConfirmed, AdminWorkflowOutcome outcome) {
        private static ConfirmationResult confirmed() {
            return new ConfirmationResult(true, null);
        }

        private static ConfirmationResult cancelled() {
            return new ConfirmationResult(false, null);
        }

        private static ConfirmationResult outcome(AdminWorkflowOutcome outcome) {
            return new ConfirmationResult(false, outcome);
        }
    }
}
