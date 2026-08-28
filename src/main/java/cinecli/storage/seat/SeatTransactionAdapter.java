package cinecli.storage.seat;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.SeatCoordinate;
import cinecli.storage.exception.SeatStorageException;
import cinecli.storage.transaction.TransactionFileSnapshot;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Provides storage-internal occupancy snapshots for recovery transactions. */
public final class SeatTransactionAdapter {
    private final SeatStorage seatStorage;
    private final Path seatsPath;
    private final SeatParser parser = new SeatParser();

    /** Creates an adapter over the normal occupancy storage facade. */
    public SeatTransactionAdapter(SeatStorage seatStorage, Path seatsPath) {
        this.seatStorage = Objects.requireNonNull(seatStorage);
        this.seatsPath = Objects.requireNonNull(seatsPath).toAbsolutePath().normalize();
    }

    /** Loads a presence-aware snapshot with exact bytes. */
    public TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> loadSnapshot(
            Set<String> knownScreeningIds) throws SeatStorageException {
        SeatOccupancySnapshot snapshot = seatStorage.loadSnapshot(knownScreeningIds);
        if (!snapshot.isPresent()) {
            return TransactionFileSnapshot.missing(snapshot.occupiedSeatsByScreening());
        }
        try {
            return TransactionFileSnapshot.present(
                    snapshot.occupiedSeatsByScreening(), Files.readAllBytes(seatsPath));
        } catch (IOException exception) {
            throw new SeatStorageException("Unable to snapshot seat occupancy '" + seatsPath + "'.", exception);
        }
    }

    /** Reads the current raw presence and bytes without parsing. */
    public TransactionFileSnapshot<Void> readRaw() throws IOException {
        if (!Files.exists(seatsPath)) {
            return TransactionFileSnapshot.missing(null);
        }
        return TransactionFileSnapshot.present(null, Files.readAllBytes(seatsPath));
    }

    /** Parses exact occupancy bytes against a catalogue. */
    public Map<String, Set<SeatCoordinate>> parse(
            byte[] bytes, Set<String> knownScreeningIds, String description)
            throws SeatStorageException {
        try {
            String text = UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return parser.parse(new StringReader(text), description, knownScreeningIds);
        } catch (CharacterCodingException exception) {
            throw new SeatStorageException(
                    "Malformed seat occupancy '" + description + "': invalid UTF-8.", exception);
        }
    }

    /** Serializes occupancy canonically. */
    public byte[] serialize(Map<String, Set<SeatCoordinate>> state) {
        return SeatStorage.serialize(state).getBytes(UTF_8);
    }

    /** Atomically replaces a present snapshot or preserves absence. */
    public void replace(
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> snapshot,
            Set<String> knownScreeningIds) throws SeatStorageException {
        if (!snapshot.isPresent()) {
            seatStorage.replaceTransactionSnapshot(SeatOccupancySnapshot.missing(), knownScreeningIds);
        } else {
            parse(snapshot.bytes(), knownScreeningIds, "transaction occupancy snapshot");
            seatStorage.replaceTransactionSnapshot(
                    SeatOccupancySnapshot.present(snapshot.value()), knownScreeningIds);
        }
    }
}
