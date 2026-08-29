package cinecli.storage.pricing;

/** Typed pricing file-system operation seam. */
enum PricingStorageOperation {
    CREATE_DIRECTORIES,
    READ_TARGET,
    CREATE_TEMPORARY,
    WRITE_TEMPORARY,
    FORCE_TEMPORARY,
    ATOMIC_REPLACE,
    DELETE_TEMPORARY
}
