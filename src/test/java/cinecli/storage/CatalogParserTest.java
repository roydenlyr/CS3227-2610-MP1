package cinecli.storage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogParserTest {
    private final CatalogParser catalogParser = new CatalogParser();

    @Test
    void parse_validCatalog_preservesIdsTextAssociationsAndOrder() throws CatalogStorageException {
        String catalog = """
                CINECLI-CATALOG\t1
                SCREENING\tSCR-002\tMOV-001\t2026-10-12\t18:45
                MOVIE\tMOV-001\tÉtoile Meridian\tPG13
                MOVIE\tMOV-002\tNight Glass\tR21
                SCREENING\tSCR-001\tMOV-001\t2026-10-11\t09:05
                """;

        List<Movie> movies = parse(catalog);

        assertAll(
                () -> assertEquals(2, movies.size()),
                () -> assertEquals("MOV-001", movies.get(0).id()),
                () -> assertEquals("Étoile Meridian", movies.get(0).title()),
                () -> assertEquals(ContentRating.PG13, movies.get(0).contentRating()),
                () -> assertEquals("SCR-002", movies.get(0).screenings().get(0).id()),
                () -> assertEquals(
                        LocalDateTime.of(2026, 10, 12, 18, 45),
                        movies.get(0).screenings().get(0).startsAt()),
                () -> assertEquals("SCR-001", movies.get(0).screenings().get(1).id()),
                () -> assertEquals("MOV-002", movies.get(1).id()),
                () -> assertEquals(ContentRating.R21, movies.get(1).contentRating()));
    }

    @Test
    void parse_headerOnlyCatalog_returnsEmptyCatalog() throws CatalogStorageException {
        assertTrue(parse("CINECLI-CATALOG\t1\n").isEmpty());
    }

    @Test
    void parse_missingMovieId_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\t\tUntitled Orbit\tPG13
                """, "movie ID");
    }

    @Test
    void parse_duplicateMovieId_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title\tPG13
                MOVIE\tMOV-001\tSecond Title\tM18
                """, "duplicate movie ID");
    }

    @Test
    void parse_duplicateScreeningId_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-12\t12:30
                SCREENING\tSCR-001\tMOV-001\t2026-10-13\t12:30
                """, "duplicate screening ID");
    }

    @Test
    void parse_unknownMovieReference_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                SCREENING\tSCR-001\tMOV-MISSING\t2026-10-12\t12:30
                """, "references unknown movie ID");
    }

    @Test
    void parse_unsupportedRating_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title\tNC16
                """, "rating must be one of");
    }

    @Test
    void parse_impossibleDate_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-02-30\t12:30
                """, "screening date");
    }

    @Test
    void parse_invalidTime_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title\tPG13
                SCREENING\tSCR-001\tMOV-001\t2026-10-12\t24:00
                """, "screening time");
    }

    @Test
    void parse_wrongMovieFieldCount_exceptionThrown() {
        assertMalformed("""
                CINECLI-CATALOG\t1
                MOVIE\tMOV-001\tFirst Title
                """, "requires 4 tab-separated fields");
    }

    @Test
    void parse_blankRecord_exceptionThrown() {
        assertMalformed("CINECLI-CATALOG\t1\n\n", "blank records are not allowed");
    }

    private List<Movie> parse(String catalog) throws CatalogStorageException {
        return catalogParser.parse(new StringReader(catalog), "test catalog");
    }

    private void assertMalformed(String catalog, String expectedMessagePart) {
        CatalogStorageException exception = assertThrows(
                CatalogStorageException.class, () -> parse(catalog));
        assertTrue(exception.getMessage().contains(expectedMessagePart));
    }
}
