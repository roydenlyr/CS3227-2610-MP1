package cinecli.storage.transaction;

import cinecli.model.Movie;
import cinecli.model.SeatCoordinate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared opaque persistence state for one prepared catalogue deletion. */
abstract class PreparedCatalogDeletion {
    final List<Movie> intendedCatalog;
    final byte[] originalCatalogBytes;
    final byte[] intendedCatalogBytes;
    final TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats;
    final TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats;
    boolean isConsumed;

    PreparedCatalogDeletion(
            List<Movie> intendedCatalog,
            byte[] originalCatalogBytes,
            byte[] intendedCatalogBytes,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats) {
        this.intendedCatalog = List.copyOf(intendedCatalog);
        this.originalCatalogBytes = Arrays.copyOf(originalCatalogBytes, originalCatalogBytes.length);
        this.intendedCatalogBytes = Arrays.copyOf(intendedCatalogBytes, intendedCatalogBytes.length);
        this.originalSeats = originalSeats;
        this.intendedSeats = intendedSeats;
    }
}
