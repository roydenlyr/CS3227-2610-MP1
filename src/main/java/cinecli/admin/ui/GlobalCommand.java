package cinecli.admin.ui;

import java.util.Objects;

/** A global role-routing command already recognized by the shared terminal adapter. */
public record GlobalCommand(Type type) implements TerminalInput {
    /** Supported global transitions. */
    public enum Type {
        ADMIN,
        CUSTOMER,
        EXIT
    }

    /** Creates a typed global command. */
    public GlobalCommand {
        Objects.requireNonNull(type);
    }
}
