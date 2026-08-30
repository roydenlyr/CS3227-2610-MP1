package cinecli.app;

import cinecli.admin.AdminWorkflowOutcome;
import cinecli.admin.AdministratorApplication;
import cinecli.admin.movies.MovieManagementApplication;
import cinecli.admin.pricing.PricingManagementApplication;
import cinecli.admin.screenings.ScreeningManagementApplication;
import cinecli.admin.ui.AdminTerminal;
import cinecli.customer.CustomerApplication;
import cinecli.customer.CustomerWorkflowOutcome;
import cinecli.customer.ui.CustomerUi;
import cinecli.storage.catalog.CatalogStorage;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.pricing.PricingStorage;
import cinecli.storage.seat.SeatStorage;
import cinecli.storage.seat.SeatTransactionAdapter;
import cinecli.storage.transaction.CatalogRecoveryGate;
import cinecli.storage.transaction.MovieDeletionTransaction;
import java.nio.file.Path;
import java.util.Objects;

/** Coordinates application role transitions and wires dependencies shared by both roles. */
public final class ApplicationRouter {
    private static final String RECOVERY_FAILURE_PREFIX =
            "Unable to recover pending catalogue changes: ";

    private final AdminTerminal terminal;
    private final CustomerApplication customerApplication;
    private final AdministratorApplication administratorApplication;
    private final CatalogRecoveryGate recoveryGate;

    /**
     * Creates the application router and its shared runtime dependencies.
     *
     * @param terminal Shared terminal adapter.
     * @param runtimeCatalogPath Runtime catalogue path.
     * @param runtimeSeatsPath Runtime seat occupancy path.
     * @param runtimePricingPath Runtime pricing path.
     * @param defaultCatalogResource Classpath resource used to seed a missing catalogue.
     */
    public ApplicationRouter(
            AdminTerminal terminal,
            Path runtimeCatalogPath,
            Path runtimeSeatsPath,
            Path runtimePricingPath,
            String defaultCatalogResource) {
        this.terminal = Objects.requireNonNull(terminal);
        CatalogStorage catalogStorage = new CatalogStorage(
                Objects.requireNonNull(runtimeCatalogPath),
                Objects.requireNonNull(defaultCatalogResource));
        SeatStorage seatStorage = new SeatStorage(Objects.requireNonNull(runtimeSeatsPath));
        PricingStorage pricingStorage = new PricingStorage(Objects.requireNonNull(runtimePricingPath));
        MovieDeletionTransaction deletionTransaction = new MovieDeletionTransaction(
                new CatalogTransactionAdapter(catalogStorage, runtimeCatalogPath),
                new SeatTransactionAdapter(seatStorage, runtimeSeatsPath),
                runtimeCatalogPath.resolveSibling("catalog-transaction.journal"));
        this.customerApplication = new CustomerApplication(
                new CustomerUi(terminal), catalogStorage, seatStorage, pricingStorage);
        this.administratorApplication = new AdministratorApplication(
                new MovieManagementApplication(catalogStorage, deletionTransaction, terminal),
                new ScreeningManagementApplication(catalogStorage, deletionTransaction, terminal),
                new PricingManagementApplication(pricingStorage, terminal),
                terminal);
        this.recoveryGate = new CatalogRecoveryGate(deletionTransaction);
    }

    /** Runs the application from customer mode until it is explicitly exited or terminated. */
    public void run() {
        Role role = Role.CUSTOMER;
        while (true) {
            if (!recoverBeforeAccess()) {
                return;
            }
            if (role == Role.CUSTOMER) {
                role = roleFor(customerApplication.run());
            } else {
                role = roleFor(administratorApplication.run());
            }
            if (role == null) {
                return;
            }
        }
    }

    private boolean recoverBeforeAccess() {
        try {
            recoveryGate.recoverBeforeAccess();
            return true;
        } catch (TransactionStorageException exception) {
            terminal.writeError(RECOVERY_FAILURE_PREFIX + exception.getMessage() + "\n");
            return false;
        }
    }

    private Role roleFor(CustomerWorkflowOutcome outcome) {
        if (outcome == CustomerWorkflowOutcome.ADMIN) {
            return Role.ADMINISTRATOR;
        }
        if (outcome == CustomerWorkflowOutcome.EXIT
                || outcome == CustomerWorkflowOutcome.TERMINATED) {
            return null;
        }
        return Role.CUSTOMER;
    }

    private Role roleFor(AdminWorkflowOutcome outcome) {
        if (outcome == AdminWorkflowOutcome.CUSTOMER) {
            return Role.CUSTOMER;
        }
        if (outcome == AdminWorkflowOutcome.EXIT
                || outcome == AdminWorkflowOutcome.TERMINATED) {
            return null;
        }
        return Role.ADMINISTRATOR;
    }

    private enum Role {
        CUSTOMER,
        ADMINISTRATOR
    }
}
