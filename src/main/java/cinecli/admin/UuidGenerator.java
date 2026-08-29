package cinecli.admin;

import java.util.UUID;

/** Supplies UUID values for newly created administrator-managed entities. */
@FunctionalInterface
interface UuidGenerator {
    UUID generate();
}
