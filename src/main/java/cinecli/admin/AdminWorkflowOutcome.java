package cinecli.admin;

/** Typed handoff from an administrator workflow to the later role-routing workflow. */
public enum AdminWorkflowOutcome {
    BACK,
    ADMIN,
    CUSTOMER,
    EXIT,
    TERMINATED
}
