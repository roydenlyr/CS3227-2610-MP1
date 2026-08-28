package cinecli.storage.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import cinecli.storage.catalog.CatalogTransactionAdapter;
import cinecli.storage.exception.MovieDeletionCommitException;
import cinecli.storage.exception.MovieDeletionCommitException.Status;
import cinecli.storage.exception.MovieDeletionPreparationException;
import cinecli.storage.exception.StorageException;
import cinecli.storage.exception.TransactionStorageException;
import cinecli.storage.seat.SeatTransactionAdapter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Coordinates atomic Movie deletion across catalogue and occupancy files. */
public final class MovieDeletionTransaction {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final CatalogTransactionAdapter catalogAdapter;
    private final SeatTransactionAdapter seatAdapter;
    private final Path journalPath;
    private final TransactionOperationHook operationHook;

    /** Creates a transaction facade over storage-internal adapters. */
    public MovieDeletionTransaction(
            CatalogTransactionAdapter catalogAdapter,
            SeatTransactionAdapter seatAdapter,
            Path journalPath) {
        this(catalogAdapter, seatAdapter, journalPath, TransactionOperationHook.NONE);
    }

    MovieDeletionTransaction(
            CatalogTransactionAdapter catalogAdapter,
            SeatTransactionAdapter seatAdapter,
            Path journalPath,
            TransactionOperationHook operationHook) {
        this.catalogAdapter = catalogAdapter;
        this.seatAdapter = seatAdapter;
        this.journalPath = journalPath.toAbsolutePath().normalize();
        this.operationHook = operationHook;
    }

    /** Recovers a valid durable deletion journal, if present. */
    public RecoveryResult recover() throws TransactionStorageException {
        if (!Files.exists(journalPath)) {
            return RecoveryResult.NO_JOURNAL;
        }
        try {
            operationHook.before(TransactionOperation.READ_JOURNAL, journalPath);
            Journal journal = parseJournal(Files.readAllBytes(journalPath));
            byte[] currentCatalog = catalogAdapter.readBytes();
            TransactionFileSnapshot<Void> currentSeats = seatAdapter.readRaw();
            boolean isCatalogOriginal = Arrays.equals(currentCatalog, journal.originalCatalogBytes);
            boolean isCatalogIntended = Arrays.equals(currentCatalog, journal.intendedCatalogBytes);
            boolean isSeatsOriginal = matches(journal.originalSeats, currentSeats);
            boolean isSeatsIntended = matches(journal.intendedSeats, currentSeats);
            if ((!isCatalogOriginal && !isCatalogIntended)
                    || (!isSeatsOriginal && !isSeatsIntended)) {
                throw new TransactionStorageException(
                        "Current data diverges from the pending deletion journal.");
            }
            if (isCatalogOriginal) {
                catalogAdapter.replace(journal.intendedCatalogBytes);
            }
            if (isSeatsOriginal && !isSeatsIntended) {
                seatAdapter.replace(journal.intendedSeats, screeningIds(journal.intendedCatalog));
            }
            verifyIntended(journal);
            operationHook.before(TransactionOperation.DELETE_JOURNAL, journalPath);
            Files.delete(journalPath);
            return RecoveryResult.RECOVERED;
        } catch (TransactionStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TransactionStorageException(
                    "Unable to recover pending movie deletion '" + journalPath + "'.", exception);
        }
    }

    /** Prepares a deletion and its complete impact preview without writing. */
    public PreparedMovieDeletion prepare(String movieId)
            throws MovieDeletionPreparationException {
        if (Files.exists(journalPath)) {
            throw new MovieDeletionPreparationException(
                    "A catalogue transaction journal already exists and must be recovered.");
        }
        try {
            TransactionFileSnapshot<List<Movie>> catalog = catalogAdapter.loadSnapshot();
            int movieIndex = findMovieIndex(catalog.value(), movieId);
            Movie movie = catalog.value().get(movieIndex);
            List<Movie> intendedCatalog = new ArrayList<>(catalog.value());
            intendedCatalog.remove(movieIndex);
            byte[] intendedCatalogBytes = catalogAdapter.serialize(intendedCatalog);

            if (movie.screenings().isEmpty()) {
                return new PreparedMovieDeletion(
                        movie,
                        movieIndex + 1,
                        List.of(),
                        intendedCatalog,
                        catalog.bytes(),
                        intendedCatalogBytes,
                        TransactionFileSnapshot.missing(Map.of()),
                        TransactionFileSnapshot.missing(Map.of()));
            }

            Set<String> originalScreeningIds = screeningIds(catalog.value());
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats =
                    seatAdapter.loadSnapshot(originalScreeningIds);
            Set<String> removedScreeningIds = new HashSet<>();
            List<ScreeningDeletionImpact> impacts = new ArrayList<>();
            for (Screening screening : movie.screenings()) {
                removedScreeningIds.add(screening.id());
                int count = originalSeats.value().getOrDefault(screening.id(), Set.of()).size();
                impacts.add(new ScreeningDeletionImpact(screening.id(), screening.startsAt(), count));
            }
            Map<String, Set<SeatCoordinate>> reducedSeats = mutableSeatCopy(originalSeats.value());
            boolean hasAffectedSeats = removedScreeningIds.stream().anyMatch(reducedSeats::containsKey);
            removedScreeningIds.forEach(reducedSeats::remove);
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats;
            if (!originalSeats.isPresent()) {
                intendedSeats = TransactionFileSnapshot.missing(Map.of());
            } else if (!hasAffectedSeats) {
                intendedSeats = TransactionFileSnapshot.present(reducedSeats, originalSeats.bytes());
            } else {
                intendedSeats = TransactionFileSnapshot.present(
                        reducedSeats, seatAdapter.serialize(reducedSeats));
            }
            return new PreparedMovieDeletion(
                    movie,
                    movieIndex + 1,
                    impacts,
                    intendedCatalog,
                    catalog.bytes(),
                    intendedCatalogBytes,
                    originalSeats,
                    intendedSeats);
        } catch (StorageException exception) {
            throw new MovieDeletionPreparationException(exception.getMessage(), exception);
        }
    }

    /** Commits one prepared deletion. */
    public MovieDeletionResult commit(PreparedMovieDeletion deletion)
            throws MovieDeletionCommitException {
        if (deletion == null) {
            throw new IllegalArgumentException("prepared deletion must not be null");
        }
        synchronized (deletion) {
            if (deletion.isConsumed) {
                throw new IllegalStateException("prepared deletion has already been committed");
            }
            deletion.isConsumed = true;
        }
        if (!deletion.isCascading()) {
            return commitChildless(deletion);
        }
        return commitCascade(deletion);
    }

    private MovieDeletionResult commitChildless(PreparedMovieDeletion deletion)
            throws MovieDeletionCommitException {
        try {
            requireCatalogOriginal(deletion.originalCatalogBytes);
            catalogAdapter.replace(deletion.intendedCatalogBytes);
            return resultOf(deletion);
        } catch (Exception exception) {
            throw commitFailure(Status.NOT_APPLIED, exception);
        }
    }

    private MovieDeletionResult commitCascade(PreparedMovieDeletion deletion)
            throws MovieDeletionCommitException {
        Path temporaryJournal = null;
        boolean isJournalPublished = false;
        try {
            if (Files.exists(journalPath)) {
                throw new IOException("transaction journal already exists");
            }
            requireCatalogOriginal(deletion.originalCatalogBytes);
            requireSeatsOriginal(deletion.originalSeats);
            byte[] journalBytes = serializeJournal(deletion);
            Files.createDirectories(journalPath.getParent());
            operationHook.before(TransactionOperation.CREATE_JOURNAL_TEMPORARY, journalPath.getParent());
            temporaryJournal = Files.createTempFile(
                    journalPath.getParent(), "cinecli-catalog-transaction-", ".tmp");
            operationHook.before(TransactionOperation.WRITE_JOURNAL_TEMPORARY, temporaryJournal);
            try (FileChannel channel = FileChannel.open(
                    temporaryJournal, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer journalBuffer = ByteBuffer.wrap(journalBytes);
                while (journalBuffer.hasRemaining()) {
                    channel.write(journalBuffer);
                }
                operationHook.before(TransactionOperation.FORCE_JOURNAL_TEMPORARY, temporaryJournal);
                channel.force(true);
            }
            operationHook.before(TransactionOperation.PUBLISH_JOURNAL, journalPath);
            Files.move(temporaryJournal, journalPath, StandardCopyOption.ATOMIC_MOVE);
            isJournalPublished = true;
            catalogAdapter.replace(deletion.intendedCatalogBytes);
            if (!sameSnapshot(deletion.originalSeats, deletion.intendedSeats)) {
                seatAdapter.replace(deletion.intendedSeats, screeningIds(deletion.intendedCatalog));
            }
            verifyIntended(new Journal(
                    deletion.movieId(),
                    deletion.originalCatalogBytes,
                    deletion.intendedCatalogBytes,
                    deletion.intendedCatalog,
                    deletion.originalSeats,
                    deletion.intendedSeats));
            operationHook.before(TransactionOperation.DELETE_JOURNAL, journalPath);
            Files.delete(journalPath);
            return resultOf(deletion);
        } catch (Exception exception) {
            Status status = isJournalPublished ? Status.RECOVERY_PENDING : Status.NOT_APPLIED;
            throw commitFailure(status, exception);
        } finally {
            if (!isJournalPublished && temporaryJournal != null) {
                try {
                    operationHook.before(
                            TransactionOperation.DELETE_JOURNAL_TEMPORARY, temporaryJournal);
                    Files.deleteIfExists(temporaryJournal);
                } catch (IOException exception) {
                    // An unpublished temporary file is not durable intent.
                }
            }
        }
    }

    private Journal parseJournal(byte[] journalBytes) throws Exception {
        String text;
        try {
            text = UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(journalBytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw malformedJournal();
        }
        if (text.startsWith("\uFEFF") || text.contains("\r") || !text.endsWith("\n")) {
            throw malformedJournal();
        }
        String[] lines = text.split("\n", -1);
        if (lines.length != 8
                || !"CINECLI-CATALOG-TRANSACTION\t1".equals(lines[0])
                || !"OPERATION\tDELETE_MOVIE".equals(lines[1])) {
            throw malformedJournal();
        }
        String subjectId = requireTwoFields(lines[2], "SUBJECT_ID");
        if (!ID_PATTERN.matcher(subjectId).matches()) {
            throw malformedJournal();
        }
        byte[] originalCatalogBytes = parseBytesLine(lines[3], "CATALOG_ORIGINAL");
        byte[] intendedCatalogBytes = parseBytesLine(lines[4], "CATALOG_INTENDED");
        try {
            List<Movie> originalCatalog = catalogAdapter.parse(
                    originalCatalogBytes, "journal original catalogue");
            List<Movie> intendedCatalog = catalogAdapter.parse(
                    intendedCatalogBytes, "journal intended catalogue");
            Movie subject = originalCatalog.stream()
                    .filter(movie -> movie.id().equals(subjectId))
                    .findFirst()
                    .orElseThrow(this::malformedJournal);
            List<Movie> expectedCatalog = originalCatalog.stream()
                    .filter(movie -> !movie.id().equals(subjectId))
                    .toList();
            if (!expectedCatalog.equals(intendedCatalog)) {
                throw malformedJournal();
            }
            if (!Arrays.equals(catalogAdapter.serialize(intendedCatalog), intendedCatalogBytes)) {
                throw malformedJournal();
            }
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats =
                    parseSeatsLine(lines[5], "SEATS_ORIGINAL", screeningIds(originalCatalog));
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats =
                    parseSeatsLine(lines[6], "SEATS_INTENDED", screeningIds(intendedCatalog));
            if (originalSeats.isPresent() != intendedSeats.isPresent()) {
                throw malformedJournal();
            }
            Map<String, Set<SeatCoordinate>> expectedSeats = mutableSeatCopy(originalSeats.value());
            boolean hasAffected = subject.screenings().stream()
                    .map(Screening::id)
                    .anyMatch(expectedSeats::containsKey);
            subject.screenings().stream().map(Screening::id).forEach(expectedSeats::remove);
            if (!expectedSeats.equals(intendedSeats.value())) {
                throw malformedJournal();
            }
            if (!hasAffected && !Arrays.equals(originalSeats.bytes(), intendedSeats.bytes())) {
                throw malformedJournal();
            }
            if (hasAffected && !Arrays.equals(seatAdapter.serialize(expectedSeats), intendedSeats.bytes())) {
                throw malformedJournal();
            }
            return new Journal(
                    subjectId,
                    originalCatalogBytes,
                    intendedCatalogBytes,
                    intendedCatalog,
                    originalSeats,
                    intendedSeats);
        } catch (StorageException exception) {
            throw new TransactionStorageException("Malformed catalogue transaction journal.", exception);
        }
    }

    private TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> parseSeatsLine(
            String line, String expectedName, Set<String> knownScreeningIds)
            throws Exception {
        String[] fields = line.split("\t", -1);
        if (fields.length == 2 && expectedName.equals(fields[0]) && "MISSING".equals(fields[1])) {
            return TransactionFileSnapshot.missing(Map.of());
        }
        if (fields.length != 4 || !expectedName.equals(fields[0]) || !"PRESENT".equals(fields[1])) {
            throw malformedJournal();
        }
        byte[] bytes = decodeAndVerify(fields[2], fields[3]);
        try {
            return TransactionFileSnapshot.present(
                    seatAdapter.parse(bytes, knownScreeningIds, expectedName), bytes);
        } catch (StorageException exception) {
            throw new TransactionStorageException("Malformed catalogue transaction journal.", exception);
        }
    }

    private byte[] parseBytesLine(String line, String expectedName) throws Exception {
        String[] fields = line.split("\t", -1);
        if (fields.length != 3 || !expectedName.equals(fields[0])) {
            throw malformedJournal();
        }
        return decodeAndVerify(fields[1], fields[2]);
    }

    private byte[] decodeAndVerify(String digest, String encoded) throws Exception {
        if (!DIGEST_PATTERN.matcher(digest).matches()) {
            throw malformedJournal();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (!Base64.getEncoder().encodeToString(bytes).equals(encoded)) {
                throw malformedJournal();
            }
            if (!digest.equals(digest(bytes))) {
                throw malformedJournal();
            }
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw malformedJournal();
        }
    }

    private String requireTwoFields(String line, String expectedName)
            throws TransactionStorageException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 2 || !expectedName.equals(fields[0])) {
            throw malformedJournal();
        }
        return fields[1];
    }

    private byte[] serializeJournal(PreparedMovieDeletion deletion) throws NoSuchAlgorithmException {
        StringBuilder journal = new StringBuilder();
        journal.append("CINECLI-CATALOG-TRANSACTION\t1\n")
                .append("OPERATION\tDELETE_MOVIE\n")
                .append("SUBJECT_ID\t").append(deletion.movieId()).append('\n')
                .append(snapshotLine("CATALOG_ORIGINAL", deletion.originalCatalogBytes))
                .append(snapshotLine("CATALOG_INTENDED", deletion.intendedCatalogBytes))
                .append(seatLine("SEATS_ORIGINAL", deletion.originalSeats))
                .append(seatLine("SEATS_INTENDED", deletion.intendedSeats));
        return journal.toString().getBytes(UTF_8);
    }

    private String snapshotLine(String name, byte[] bytes) throws NoSuchAlgorithmException {
        return name + "\t" + digest(bytes) + "\t"
                + Base64.getEncoder().encodeToString(bytes) + "\n";
    }

    private String seatLine(
            String name, TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> snapshot)
            throws NoSuchAlgorithmException {
        if (!snapshot.isPresent()) {
            return name + "\tMISSING\n";
        }
        return name + "\tPRESENT\t" + digest(snapshot.bytes()) + "\t"
                + Base64.getEncoder().encodeToString(snapshot.bytes()) + "\n";
    }

    private String digest(byte[] bytes) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void verifyIntended(Journal journal) throws Exception {
        operationHook.before(TransactionOperation.VERIFY_CATALOG, journalPath);
        boolean isCatalogIntended = Arrays.equals(
                catalogAdapter.readBytes(), journal.intendedCatalogBytes);
        operationHook.before(TransactionOperation.VERIFY_SEATS, journalPath);
        boolean isSeatsIntended = matches(journal.intendedSeats, seatAdapter.readRaw());
        if (!isCatalogIntended || !isSeatsIntended) {
            throw new IOException("intended transaction snapshots could not be verified");
        }
    }

    private void requireCatalogOriginal(byte[] originalBytes) throws IOException {
        if (!Arrays.equals(catalogAdapter.readBytes(), originalBytes)) {
            throw new IOException("catalogue changed after deletion preparation");
        }
    }

    private void requireSeatsOriginal(
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> original) throws IOException {
        if (!matches(original, seatAdapter.readRaw())) {
            throw new IOException("seat occupancy changed after deletion preparation");
        }
    }

    private boolean matches(TransactionFileSnapshot<?> expected, TransactionFileSnapshot<?> actual) {
        return expected.isPresent() == actual.isPresent()
                && Arrays.equals(expected.bytes(), actual.bytes());
    }

    private boolean sameSnapshot(TransactionFileSnapshot<?> first, TransactionFileSnapshot<?> second) {
        return matches(first, second);
    }

    private int findMovieIndex(List<Movie> movies, String movieId)
            throws MovieDeletionPreparationException {
        for (int i = 0; i < movies.size(); i++) {
            if (movies.get(i).id().equals(movieId)) {
                return i;
            }
        }
        throw new MovieDeletionPreparationException("Unknown movie ID '" + movieId + "'.");
    }

    private Set<String> screeningIds(List<Movie> movies) {
        Set<String> ids = new HashSet<>();
        for (Movie movie : movies) {
            movie.screenings().stream().map(Screening::id).forEach(ids::add);
        }
        return Set.copyOf(ids);
    }

    private Map<String, Set<SeatCoordinate>> mutableSeatCopy(
            Map<String, Set<SeatCoordinate>> source) {
        Map<String, Set<SeatCoordinate>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<SeatCoordinate>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }

    private MovieDeletionResult resultOf(PreparedMovieDeletion deletion) {
        return new MovieDeletionResult(
                deletion.movieId(),
                deletion.title(),
                deletion.screeningCount(),
                deletion.occupiedSeatCount());
    }

    private MovieDeletionCommitException commitFailure(Status status, Exception cause) {
        return new MovieDeletionCommitException(status, cause.getMessage(), cause);
    }

    private TransactionStorageException malformedJournal() {
        return new TransactionStorageException("Malformed catalogue transaction journal.");
    }

    private record Journal(
            String subjectId,
            byte[] originalCatalogBytes,
            byte[] intendedCatalogBytes,
            List<Movie> intendedCatalog,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> originalSeats,
            TransactionFileSnapshot<Map<String, Set<SeatCoordinate>>> intendedSeats) {
    }
}
