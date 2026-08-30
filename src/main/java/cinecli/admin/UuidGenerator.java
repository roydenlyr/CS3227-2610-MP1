package cinecli.admin;

import java.util.UUID;

/** Supplies UUID values for newly created administrator-managed entities. */
@FunctionalInterface
public interface UuidGenerator {
    /** Generates one UUID for a newly created administrator-managed entity. */
    UUID generate();
}
