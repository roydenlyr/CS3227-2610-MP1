package cinecli.storage.seat;

import cinecli.model.SeatCoordinate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Represents the complete validated occupancy state without conflating absence and emptiness.
 */
public final class SeatOccupancySnapshot {
    private static final SeatOccupancySnapshot MISSING = new SeatOccupancySnapshot(false, Map.of());

    private final boolean isPresent;
    private final Map<String, Set<SeatCoordinate>> occupiedSeatsByScreening;

    private SeatOccupancySnapshot(
            boolean isPresent, Map<String, Set<SeatCoordinate>> occupiedSeatsByScreening) {
        this.isPresent = isPresent;
        this.occupiedSeatsByScreening = immutableCopy(occupiedSeatsByScreening);
    }

    /** Returns a missing-file snapshot. */
    public static SeatOccupancySnapshot missing() {
        return MISSING;
    }

    /** Returns a present-file snapshot with the supplied complete state. */
    public static SeatOccupancySnapshot present(
            Map<String, Set<SeatCoordinate>> occupiedSeatsByScreening) {
        return new SeatOccupancySnapshot(true, occupiedSeatsByScreening);
    }

    /** Returns whether the occupancy file exists. */
    public boolean isPresent() {
        return isPresent;
    }

    /** Returns the complete immutable occupancy map. */
    public Map<String, Set<SeatCoordinate>> occupiedSeatsByScreening() {
        return occupiedSeatsByScreening;
    }

    private static Map<String, Set<SeatCoordinate>> immutableCopy(
            Map<String, Set<SeatCoordinate>> source) {
        Objects.requireNonNull(source);
        Map<String, Set<SeatCoordinate>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<SeatCoordinate>> entry : source.entrySet()) {
            Objects.requireNonNull(entry.getKey());
            Objects.requireNonNull(entry.getValue());
            TreeSet<SeatCoordinate> seats = new TreeSet<>();
            for (SeatCoordinate seat : entry.getValue()) {
                seats.add(Objects.requireNonNull(seat));
            }
            copy.put(entry.getKey(), Collections.unmodifiableSet(seats));
        }
        return Collections.unmodifiableMap(copy);
    }
}
