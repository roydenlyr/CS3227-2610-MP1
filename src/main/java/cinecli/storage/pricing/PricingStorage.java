package cinecli.storage.pricing;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Pricing;
import cinecli.model.PromoCode;
import cinecli.model.SnackMenuItem;
import cinecli.model.TicketType;
import cinecli.storage.exception.PricingStorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Initializes, validates, reads, and atomically saves complete runtime pricing data.
 */
public final class PricingStorage {
    private final Path runtimePricingPath;
    private final PricingStorageOperationHook operationHook;
    private final PricingParser pricingParser = new PricingParser();

    /**
     * Creates pricing storage for a mutable runtime path.
     *
     * @param runtimePricingPath Mutable runtime pricing path.
     */
    public PricingStorage(Path runtimePricingPath) {
        this(runtimePricingPath, PricingStorageOperationHook.NONE);
    }

    PricingStorage(Path runtimePricingPath, PricingStorageOperationHook operationHook) {
        this.runtimePricingPath = Objects.requireNonNull(runtimePricingPath).toAbsolutePath().normalize();
        this.operationHook = Objects.requireNonNull(operationHook);
    }

    /**
     * Initializes missing runtime pricing and loads the complete validated state.
     *
     * @return Complete pricing state.
     * @throws PricingStorageException If initialization, reading, or validation fails.
     */
    public Pricing load() throws PricingStorageException {
        if (Files.notExists(runtimePricingPath)) {
            save(Pricing.defaults());
        }
        try (BufferedReader reader = Files.newBufferedReader(runtimePricingPath, UTF_8)) {
            operationHook.before(PricingStorageOperation.READ_TARGET, runtimePricingPath);
            return pricingParser.parse(reader, runtimePricingPath.toString());
        } catch (IOException exception) {
            throw new PricingStorageException(
                    "Unable to read runtime pricing '" + runtimePricingPath + "'.", exception);
        }
    }

    /**
     * Validates and atomically saves a complete pricing state.
     *
     * @param pricing Complete intended pricing state.
     * @throws PricingStorageException If validation or atomic persistence fails.
     */
    public void save(Pricing pricing) throws PricingStorageException {
        String serializedPricing = serialize(pricing);
        pricingParser.parse(new StringReader(serializedPricing), "proposed pricing");

        Path temporaryPath = null;
        try {
            Path parentDirectory = runtimePricingPath.getParent();
            operationHook.before(PricingStorageOperation.CREATE_DIRECTORIES, parentDirectory);
            Files.createDirectories(parentDirectory);
            operationHook.before(PricingStorageOperation.CREATE_TEMPORARY, parentDirectory);
            temporaryPath = Files.createTempFile(parentDirectory, "cinecli-pricing-", ".tmp");
            operationHook.before(PricingStorageOperation.WRITE_TEMPORARY, temporaryPath);
            Files.writeString(
                    temporaryPath,
                    serializedPricing,
                    UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            operationHook.before(PricingStorageOperation.FORCE_TEMPORARY, temporaryPath);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            operationHook.before(PricingStorageOperation.ATOMIC_REPLACE, runtimePricingPath);
            Files.move(
                    temporaryPath,
                    runtimePricingPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new PricingStorageException(
                    "Unable to update runtime pricing '" + runtimePricingPath
                            + "' because the file system does not support atomic replacement."
                            + " The existing data was preserved.",
                    exception);
        } catch (IOException exception) {
            throw new PricingStorageException(
                    "Unable to update runtime pricing '" + runtimePricingPath
                            + "'. The existing data was preserved.",
                    exception);
        } finally {
            deleteTemporaryFileIfPresent(temporaryPath);
        }
    }

    static String serialize(Pricing pricing) throws PricingStorageException {
        if (pricing == null) {
            throw new PricingStorageException("Proposed pricing must not be null.");
        }
        StringBuilder serializedPricing = new StringBuilder("CINECLI-PRICING\t1\n");
        for (TicketType ticketType : TicketType.values()) {
            serializedPricing.append("TICKET_PRICE\t")
                    .append(ticketType.name()).append('\t')
                    .append(formatPrice(pricing.ticketPriceInCents(ticketType))).append('\n');
        }
        for (SnackMenuItem menuItem : SnackMenuItem.values()) {
            serializedPricing.append("SNACK_PRICE\t")
                    .append(menuItem.name()).append('\t')
                    .append(formatPrice(pricing.snackPriceInCents(menuItem))).append('\n');
        }
        List<PromoCode> promotions = new ArrayList<>(pricing.promotions());
        promotions.sort(Comparator.comparing(PromoCode::code));
        for (PromoCode promotion : promotions) {
            serializedPricing.append("PROMOTION\t")
                    .append(promotion.code()).append('\t')
                    .append(promotion.discountPercentage()).append('\n');
        }
        return serializedPricing.toString();
    }

    private static String formatPrice(int priceInCents) {
        return "%d.%02d".formatted(priceInCents / 100, priceInCents % 100);
    }

    private void deleteTemporaryFileIfPresent(Path temporaryPath) {
        if (temporaryPath == null) {
            return;
        }
        try {
            operationHook.before(PricingStorageOperation.DELETE_TEMPORARY, temporaryPath);
            Files.deleteIfExists(temporaryPath);
        } catch (IOException exception) {
            // Preserve the primary operation result when temporary cleanup fails.
        }
    }
}
