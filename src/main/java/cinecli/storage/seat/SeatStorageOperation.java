package cinecli.storage.seat;

/** Typed seat-occupancy file-system operation seam. */
enum SeatStorageOperation {
    CREATE_DIRECTORIES,
    READ_TARGET,
    INITIALIZE_TARGET,
    CREATE_TEMPORARY,
    WRITE_TEMPORARY,
    FORCE_TEMPORARY,
    ATOMIC_REPLACE,
    DELETE_TEMPORARY
}
