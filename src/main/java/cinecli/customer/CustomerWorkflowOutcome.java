package cinecli.customer;

/** Typed handoff from the customer workflow to the application router. */
public enum CustomerWorkflowOutcome {
    ADMIN,
    CUSTOMER,
    EXIT,
    TERMINATED
}
