package cinecli.storage.catalog;

/** Typed catalogue file-system operation seam. */
enum CatalogStorageOperation {
    CREATE_DIRECTORIES,
    READ_TARGET,
    COPY_DEFAULT,
    CREATE_TEMPORARY,
    WRITE_TEMPORARY,
    FORCE_TEMPORARY,
    ATOMIC_REPLACE,
    DELETE_TEMPORARY
}
