package cinecli.storage.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Movie;
import cinecli.storage.exception.CatalogStorageException;
import cinecli.storage.transaction.TransactionFileSnapshot;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Provides storage-internal catalogue snapshots for recovery transactions. */
public final class CatalogTransactionAdapter {
    private final CatalogStorage catalogStorage;
    private final Path catalogPath;
    private final CatalogParser parser = new CatalogParser();

    /** Creates an adapter over the normal catalogue storage facade. */
    public CatalogTransactionAdapter(CatalogStorage catalogStorage, Path catalogPath) {
        this.catalogStorage = Objects.requireNonNull(catalogStorage);
        this.catalogPath = Objects.requireNonNull(catalogPath).toAbsolutePath().normalize();
    }

    /** Loads the catalogue and captures its exact bytes. */
    public TransactionFileSnapshot<List<Movie>> loadSnapshot() throws CatalogStorageException {
        List<Movie> movies = catalogStorage.load();
        try {
            return TransactionFileSnapshot.present(movies, Files.readAllBytes(catalogPath));
        } catch (IOException exception) {
            throw new CatalogStorageException("Unable to snapshot runtime catalog '" + catalogPath + "'.", exception);
        }
    }

    /** Reads exact target bytes without initialization. */
    public byte[] readBytes() throws IOException {
        return Files.readAllBytes(catalogPath);
    }

    /** Parses exact catalogue bytes. */
    public List<Movie> parse(byte[] bytes, String description) throws CatalogStorageException {
        try {
            String text = UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return parser.parse(new StringReader(text), description);
        } catch (CharacterCodingException exception) {
            throw new CatalogStorageException(
                    "Malformed catalog '" + description + "': invalid UTF-8.", exception);
        }
    }

    /** Serializes a complete catalogue canonically. */
    public byte[] serialize(List<Movie> movies) throws CatalogStorageException {
        return CatalogStorage.serialize(movies).getBytes(UTF_8);
    }

    /** Atomically replaces the catalogue with a validated snapshot. */
    public void replace(byte[] bytes) throws CatalogStorageException {
        catalogStorage.save(parse(bytes, "transaction catalogue snapshot"));
    }
}
