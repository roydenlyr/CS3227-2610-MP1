package cinecli.admin;

import java.util.UUID;

/** Supplies UUID values for newly added movies. */
@FunctionalInterface
interface MovieIdGenerator {
    UUID generate();
}
