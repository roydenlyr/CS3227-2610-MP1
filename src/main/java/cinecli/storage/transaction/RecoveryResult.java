package cinecli.storage.transaction;

/** Result of checking for and completing a durable catalogue transaction. */
public enum RecoveryResult {
    NO_JOURNAL,
    RECOVERED
}
