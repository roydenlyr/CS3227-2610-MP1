package cinecli.admin;

import cinecli.admin.ui.AdminTerminal;
import cinecli.storage.exception.PricingStorageException;
import cinecli.storage.pricing.PricingStorage;
import java.util.Objects;

/** Coordinates administrator ticket, snack/combo, and promotion management workflows. */
public final class PricingManagementApplication {
    private final PricingStorage pricingStorage;
    private final AdminTerminal terminal;
    private final AdminWorkflowInteraction interaction;
    private final AdminInputRules inputRules = new AdminInputRules();
    private final PricingManagementText text = new PricingManagementText();

    /**
     * Creates the administrator pricing-management coordinator.
     *
     * @param pricingStorage Runtime pricing storage.
     * @param terminal Administrator terminal interaction.
     */
    public PricingManagementApplication(PricingStorage pricingStorage, AdminTerminal terminal) {
        this.pricingStorage = Objects.requireNonNull(pricingStorage);
        this.terminal = Objects.requireNonNull(terminal);
        this.interaction = new AdminWorkflowInteraction(terminal);
    }

    /** Runs pricing management until navigation away from the administrator workflow. */
    public AdminWorkflowOutcome run() {
        while (true) {
            AdminWorkflowOutcome listingOutcome = showManagement();
            if (listingOutcome != null) {
                return listingOutcome;
            }

            AdminWorkflowOutcome actionOutcome = runAction();
            if (actionOutcome != null) {
                return actionOutcome;
            }
        }
    }

    private AdminWorkflowOutcome showManagement() {
        try {
            return interaction.write(text.management(pricingStorage.load()))
                    ? null : AdminWorkflowOutcome.TERMINATED;
        } catch (PricingStorageException exception) {
            return reportStorageFailure(PricingManagementText.ACCESS_FAILURE_PREFIX, exception);
        }
    }

    private AdminWorkflowOutcome runAction() {
        while (true) {
            AdminWorkflowInput input = interaction.read();
            if (input.outcome() != null) {
                return input.outcome();
            }
            Integer choice = inputRules.parseNumber(input.line(), 0, 3);
            if (choice == null) {
                if (!interaction.write(PricingManagementText.ACTION_ERROR_WITH_PROMPT)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return AdminWorkflowOutcome.BACK;
            }
            if (choice == 1) {
                return childOutcome(new TicketPriceManagementApplication(pricingStorage, terminal).run());
            }
            if (choice == 2) {
                return childOutcome(new SnackComboPriceManagementApplication(pricingStorage, terminal).run());
            }
            return childOutcome(new PromotionManagementApplication(pricingStorage, terminal).run());
        }
    }

    private AdminWorkflowOutcome childOutcome(AdminWorkflowOutcome outcome) {
        return outcome == AdminWorkflowOutcome.BACK ? null : outcome;
    }

    private AdminWorkflowOutcome reportStorageFailure(String prefix, PricingStorageException exception) {
        return interaction.write(text.storageFailure(prefix, exception))
                ? AdminWorkflowOutcome.BACK : AdminWorkflowOutcome.TERMINATED;
    }
}
