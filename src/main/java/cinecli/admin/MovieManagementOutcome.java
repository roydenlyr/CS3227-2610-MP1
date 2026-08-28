package cinecli.admin;

/** Typed handoff from Movie management to the later role-routing workflow. */
public enum MovieManagementOutcome {
    BACK,
    ADMIN,
    CUSTOMER,
    EXIT,
    TERMINATED
}
