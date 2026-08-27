package cinecli.storage.seat;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Creates storage fault doubles for workflow tests outside this package.
 */
public final class SeatStorageTestSupport {
    private SeatStorageTestSupport() {
        // Prevent instantiation.
    }

    /**
     * Creates storage that fails during final atomic replacement.
     *
     * @param runtimeSeats Runtime occupancy path.
     * @return Fault-injected storage.
     */
    public static SeatStorage failingReplacement(Path runtimeSeats) {
        return new SeatStorage(
                runtimeSeats,
                (operation, path) -> {
                    if (operation.equals("replace")) {
                        throw new IOException("simulated replacement failure");
                    }
                });
    }
}
