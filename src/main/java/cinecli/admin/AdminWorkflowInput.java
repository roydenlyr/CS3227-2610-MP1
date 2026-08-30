package cinecli.admin;

/** Carries one submitted administrator line or a typed workflow outcome. */
public record AdminWorkflowInput(String line, AdminWorkflowOutcome outcome) {
}
