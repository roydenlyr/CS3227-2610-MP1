package cinecli.storage.transaction;

import java.util.Arrays;

/** Carries validated logical state together with defensively copied exact file bytes. */
public final class TransactionFileSnapshot<T> {
    private final boolean isPresent;
    private final T value;
    private final byte[] bytes;

    private TransactionFileSnapshot(boolean isPresent, T value, byte[] bytes) {
        this.isPresent = isPresent;
        this.value = value;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Returns a present snapshot. */
    public static <T> TransactionFileSnapshot<T> present(T value, byte[] bytes) {
        return new TransactionFileSnapshot<>(true, value, bytes);
    }

    /** Returns a missing snapshot. */
    public static <T> TransactionFileSnapshot<T> missing(T value) {
        return new TransactionFileSnapshot<>(false, value, new byte[0]);
    }

    /** Returns whether the target exists. */
    public boolean isPresent() {
        return isPresent;
    }

    /** Returns the validated logical value. */
    public T value() {
        return value;
    }

    /** Returns a defensive copy of the exact bytes. */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
