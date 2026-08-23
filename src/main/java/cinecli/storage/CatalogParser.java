package cinecli.storage;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.Screening;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses and validates version 1 CineCLI catalog files.
 */
final class CatalogParser {
    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final String HEADER_NAME = "CINECLI-CATALOG";
    private static final String FORMAT_VERSION = "1";
    private static final String MOVIE_RECORD = "MOVIE";
    private static final String SCREENING_RECORD = "SCREENING";
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Parses all records before returning a complete catalog.
     *
     * @param source Catalog text source.
     * @param sourceDescription Description used in validation errors.
     * @return Movies in persisted order, with screenings in persisted order.
     * @throws CatalogStorageException If any record is malformed.
     */
    List<Movie> parse(Reader source, String sourceDescription) throws CatalogStorageException {
        BufferedReader reader = source instanceof BufferedReader bufferedReader
                ? bufferedReader
                : new BufferedReader(source);
        Map<String, ParsedMovie> movies = new LinkedHashMap<>();
        Map<String, ParsedScreening> screenings = new LinkedHashMap<>();

        try {
            parseHeader(reader.readLine(), sourceDescription);
            parseRecords(reader, sourceDescription, movies, screenings);
        } catch (IOException exception) {
            throw new CatalogStorageException(
                    "Unable to read catalog '" + sourceDescription + "'.", exception);
        }

        return assembleCatalog(sourceDescription, movies, screenings);
    }

    private void parseHeader(String header, String sourceDescription) throws CatalogStorageException {
        if (header == null) {
            throw formatError(sourceDescription, 1, "missing catalog header");
        }
        String normalizedHeader = header.startsWith(BYTE_ORDER_MARK)
                ? header.substring(BYTE_ORDER_MARK.length())
                : header;

        String[] fields = normalizedHeader.split("\t", -1);
        if (fields.length != 2 || !HEADER_NAME.equals(fields[0])) {
            throw formatError(sourceDescription, 1,
                    "expected header '" + HEADER_NAME + "<TAB>" + FORMAT_VERSION + "'");
        }
        if (!FORMAT_VERSION.equals(fields[1])) {
            throw formatError(sourceDescription, 1,
                    "unsupported catalog format version '" + fields[1] + "'");
        }
    }

    private void parseRecords(
            BufferedReader reader,
            String sourceDescription,
            Map<String, ParsedMovie> movies,
            Map<String, ParsedScreening> screenings) throws IOException, CatalogStorageException {
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                throw formatError(sourceDescription, lineNumber, "blank records are not allowed");
            }

            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
                case MOVIE_RECORD -> parseMovie(fields, sourceDescription, lineNumber, movies);
                case SCREENING_RECORD -> parseScreening(
                        fields, sourceDescription, lineNumber, screenings);
                default -> throw formatError(sourceDescription, lineNumber,
                        "unknown record type '" + fields[0] + "'");
            }
        }
    }

    private void parseMovie(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            Map<String, ParsedMovie> movies) throws CatalogStorageException {
        requireFieldCount(fields, 4, sourceDescription, lineNumber, MOVIE_RECORD);
        String id = parseId(fields[1], "movie ID", sourceDescription, lineNumber);
        String title = parseText(fields[2], "movie title", sourceDescription, lineNumber);
        ContentRating contentRating = parseContentRating(fields[3], sourceDescription, lineNumber);

        ParsedMovie previousMovie = movies.putIfAbsent(
                id, new ParsedMovie(id, title, contentRating));
        if (previousMovie != null) {
            throw formatError(sourceDescription, lineNumber, "duplicate movie ID '" + id + "'");
        }
    }

    private void parseScreening(
            String[] fields,
            String sourceDescription,
            int lineNumber,
            Map<String, ParsedScreening> screenings) throws CatalogStorageException {
        requireFieldCount(fields, 5, sourceDescription, lineNumber, SCREENING_RECORD);
        String id = parseId(fields[1], "screening ID", sourceDescription, lineNumber);
        String movieId = parseId(fields[2], "movie ID", sourceDescription, lineNumber);
        LocalDate date = parseDate(fields[3], sourceDescription, lineNumber);
        LocalTime time = parseTime(fields[4], sourceDescription, lineNumber);

        ParsedScreening previousScreening = screenings.putIfAbsent(
                id, new ParsedScreening(id, movieId, LocalDateTime.of(date, time), lineNumber));
        if (previousScreening != null) {
            throw formatError(sourceDescription, lineNumber,
                    "duplicate screening ID '" + id + "'");
        }
    }

    private List<Movie> assembleCatalog(
            String sourceDescription,
            Map<String, ParsedMovie> movies,
            Map<String, ParsedScreening> screenings) throws CatalogStorageException {
        Map<String, List<Screening>> screeningsByMovie = new LinkedHashMap<>();
        for (String movieId : movies.keySet()) {
            screeningsByMovie.put(movieId, new ArrayList<>());
        }

        for (ParsedScreening parsedScreening : screenings.values()) {
            List<Screening> movieScreenings = screeningsByMovie.get(parsedScreening.movieId());
            if (movieScreenings == null) {
                throw formatError(sourceDescription, parsedScreening.lineNumber(),
                        "screening '" + parsedScreening.id()
                                + "' references unknown movie ID '" + parsedScreening.movieId() + "'");
            }
            movieScreenings.add(new Screening(parsedScreening.id(), parsedScreening.startsAt()));
        }

        List<Movie> moviesInDisplayOrder = new ArrayList<>();
        for (ParsedMovie parsedMovie : movies.values()) {
            moviesInDisplayOrder.add(new Movie(
                    parsedMovie.id(),
                    parsedMovie.title(),
                    parsedMovie.contentRating(),
                    screeningsByMovie.get(parsedMovie.id())));
        }
        return List.copyOf(moviesInDisplayOrder);
    }

    private void requireFieldCount(
            String[] fields,
            int expectedCount,
            String sourceDescription,
            int lineNumber,
            String recordType) throws CatalogStorageException {
        if (fields.length != expectedCount) {
            throw formatError(sourceDescription, lineNumber,
                    recordType + " record requires " + expectedCount + " tab-separated fields");
        }
    }

    private String parseId(
            String value,
            String fieldName,
            String sourceDescription,
            int lineNumber) throws CatalogStorageException {
        if (!ID_PATTERN.matcher(value).matches()) {
            throw formatError(sourceDescription, lineNumber,
                    fieldName + " must match " + ID_PATTERN.pattern());
        }
        return value;
    }

    private String parseText(
            String value,
            String fieldName,
            String sourceDescription,
            int lineNumber) throws CatalogStorageException {
        if (value.isBlank()) {
            throw formatError(sourceDescription, lineNumber, fieldName + " must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw formatError(sourceDescription, lineNumber,
                    fieldName + " must not have leading or trailing whitespace");
        }
        return value;
    }

    private ContentRating parseContentRating(
            String value,
            String sourceDescription,
            int lineNumber) throws CatalogStorageException {
        try {
            return ContentRating.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw formatError(sourceDescription, lineNumber,
                    "rating must be one of PG13, M18, or R21");
        }
    }

    private LocalDate parseDate(
            String value,
            String sourceDescription,
            int lineNumber) throws CatalogStorageException {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException exception) {
            throw formatError(sourceDescription, lineNumber,
                    "screening date must use yyyy-MM-dd and be a real date");
        }
    }

    private LocalTime parseTime(
            String value,
            String sourceDescription,
            int lineNumber) throws CatalogStorageException {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeException exception) {
            throw formatError(sourceDescription, lineNumber,
                    "screening time must use 24-hour HH:mm and be a real time");
        }
    }

    private CatalogStorageException formatError(
            String sourceDescription, int lineNumber, String reason) {
        return new CatalogStorageException(
                "Malformed catalog '" + sourceDescription + "' at line " + lineNumber + ": " + reason + ".");
    }

    private record ParsedMovie(String id, String title, ContentRating contentRating) {
    }

    private record ParsedScreening(String id, String movieId, LocalDateTime startsAt, int lineNumber) {
    }
}
