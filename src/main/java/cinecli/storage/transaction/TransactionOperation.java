package cinecli.storage.transaction;

/** Typed deterministic transaction file-system operation seam. */
enum TransactionOperation {
    READ_JOURNAL,
    CREATE_JOURNAL_TEMPORARY,
    WRITE_JOURNAL_TEMPORARY,
    FORCE_JOURNAL_TEMPORARY,
    PUBLISH_JOURNAL,
    VERIFY_CATALOG,
    VERIFY_SEATS,
    DELETE_JOURNAL,
    DELETE_JOURNAL_TEMPORARY
}
