package cinecli.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a screening at a specific local date and time.
 */
public record Screening(String id, LocalDateTime startsAt) {

    /**
     * Creates an immutable screening.
     *
     * @param id Stable screening identifier.
     * @param startsAt Screening date and time.
     */
    public Screening(String id, LocalDateTime startsAt) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.startsAt = Objects.requireNonNull(startsAt);
    }
}
