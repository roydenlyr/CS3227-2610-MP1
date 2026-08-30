package cinecli.admin;

import cinecli.admin.ui.AdminTerminal;
import cinecli.admin.movies.MovieManagementApplication;
import cinecli.admin.pricing.PricingManagementApplication;
import cinecli.admin.screenings.ScreeningManagementApplication;
import java.util.Objects;

/** Coordinates the administrator homepage and its existing management workflows. */
public final class AdministratorApplication {
    private static final String HOMEPAGE = """
            
            Administrator Home
            1. Movie Management
            2. Screening Management
            3. Pricing and Promotions Management
            0. Return to customer mode
            Enter an option. Type /customer to return to the kiosk:\s""";
    private static final String ACTION_ERROR = "Enter 0, 1, 2, or 3.\n";

    private final MovieManagementApplication movieManagementApplication;
    private final ScreeningManagementApplication screeningManagementApplication;
    private final PricingManagementApplication pricingManagementApplication;
    private final AdminWorkflowInteraction interaction;
    private final AdminInputRules inputRules = new AdminInputRules();

    /**
     * Creates the administrator homepage with its existing management workflows.
     *
     * @param movieManagementApplication Movie-management workflow.
     * @param screeningManagementApplication Screening-management workflow.
     * @param pricingManagementApplication Pricing and promotions-management workflow.
     * @param terminal Shared terminal interaction.
     */
    public AdministratorApplication(
            MovieManagementApplication movieManagementApplication,
            ScreeningManagementApplication screeningManagementApplication,
            PricingManagementApplication pricingManagementApplication,
            AdminTerminal terminal) {
        this.movieManagementApplication = Objects.requireNonNull(movieManagementApplication);
        this.screeningManagementApplication = Objects.requireNonNull(screeningManagementApplication);
        this.pricingManagementApplication = Objects.requireNonNull(pricingManagementApplication);
        this.interaction = new AdminWorkflowInteraction(Objects.requireNonNull(terminal));
    }

    /** Runs the homepage until a global role transition or terminal termination occurs. */
    public AdminWorkflowOutcome run() {
        while (true) {
            if (!interaction.write(HOMEPAGE)) {
                return AdminWorkflowOutcome.TERMINATED;
            }
            AdminWorkflowInput input = interaction.read();
            if (input.outcome() != null) {
                return input.outcome();
            }
            Integer choice = inputRules.parseNumber(input.line(), 0, 3);
            if (choice == null) {
                if (!interaction.write(ACTION_ERROR)) {
                    return AdminWorkflowOutcome.TERMINATED;
                }
                continue;
            }
            if (choice == 0) {
                return AdminWorkflowOutcome.CUSTOMER;
            }
            AdminWorkflowOutcome outcome = runManagement(choice);
            if (outcome != AdminWorkflowOutcome.BACK) {
                return outcome;
            }
        }
    }

    private AdminWorkflowOutcome runManagement(int choice) {
        if (choice == 1) {
            return movieManagementApplication.run();
        }
        if (choice == 2) {
            return screeningManagementApplication.run();
        }
        return pricingManagementApplication.run();
    }
}
