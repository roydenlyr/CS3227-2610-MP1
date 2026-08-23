package cinecli.ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cinecli.model.ContentRating;
import cinecli.model.Movie;
import cinecli.model.Screening;
import cinecli.model.SeatCoordinate;
import cinecli.model.SnackMenuItem;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerUiTest {
    @Test
    void showCatalog_multipleScreenings_labelsTimingsWithLetters() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Movie movie = new Movie(
                "MOV-001",
                "Orbit of Echoes",
                ContentRating.PG13,
                List.of(
                        new Screening("SCR-001", LocalDateTime.of(2026, 8, 29, 13, 30)),
                        new Screening("SCR-002", LocalDateTime.of(2026, 8, 29, 18, 0))));

        customerUi.showCatalog(List.of(movie));

        assertAll(
                () -> assertTrue(output.toString().contains("A. 29 Aug 2026, 13:30")),
                () -> assertTrue(output.toString().contains("B. 29 Aug 2026, 18:00")));
    }

    @Test
    void showSeatMap_fixedLayout_placesScreenAboveGAndNumberAxisBelowA() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);
        Movie movie = new Movie(
                "MOV-001",
                "Orbit of Echoes",
                ContentRating.PG13,
                List.of());
        Screening screening = new Screening(
                "SCR-001", LocalDateTime.of(2026, 8, 29, 13, 30));

        customerUi.showSeatMap(
                movie, screening, Set.of(new SeatCoordinate('G', 4), new SeatCoordinate('A', 20)));

        List<String> lines = output.toString().lines().toList();
        int screenIndex = indexOfTrimmedLine(lines, "SCREEN");
        int rowGIndex = indexOfStartingLine(lines, "G   ");
        int rowAIndex = indexOfStartingLine(lines, "A   ");
        int numberAxisIndex = indexOfStartingLine(lines, "     1  2");
        String[] rowGFields = lines.get(rowGIndex).strip().split("\\s+");
        String[] rowAFields = lines.get(rowAIndex).strip().split("\\s+");
        String[] axisFields = lines.get(numberAxisIndex).strip().split("\\s+");

        assertAll(
                () -> assertTrue(screenIndex < rowGIndex),
                () -> assertTrue(rowGIndex < rowAIndex),
                () -> assertEquals(6, rowAIndex - rowGIndex),
                () -> assertTrue(rowAIndex < numberAxisIndex),
                () -> assertEquals(21, rowGFields.length),
                () -> assertEquals(21, rowAFields.length),
                () -> assertEquals("X", rowGFields[4]),
                () -> assertEquals("X", rowAFields[20]),
                () -> assertEquals("1", axisFields[0]),
                () -> assertEquals("20", axisFields[19]),
                () -> assertTrue(output.toString().contains(
                        "X = Taken or tentatively selected")));
    }

    @Test
    void showSnackMenu_fixedItems_groupsItemsWithExactPrices() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showSnackMenu(List.of(SnackMenuItem.values()));

        List<String> nonblankLines = output.toString().lines()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(
                List.of(
                        "Snack and Combo Menu",
                        "Snacks:",
                        "1. Popcorn - S$5.00",
                        "2. Nachos - S$6.00",
                        "3. Soft Drink - S$3.00",
                        "Combos:",
                        "4. Popcorn Combo (Popcorn + Soft Drink) - S$7.00",
                        "5. Nachos Combo (Nachos + Soft Drink) - S$8.00",
                        "0. Skip snacks and combos"),
                nonblankLines);
    }

    @Test
    void showSnackSelected_combo_displaysNameAndExactPrice() {
        StringWriter output = new StringWriter();
        CustomerUi customerUi = createUi(output);

        customerUi.showSnackSelected(SnackMenuItem.NACHOS_COMBO);

        assertEquals(
                "Snack/combo selected: Nachos Combo (Nachos + Soft Drink) - S$8.00",
                output.toString().strip());
    }

    private CustomerUi createUi(StringWriter output) {
        return new CustomerUi(new StringReader(""), output, new StringWriter());
    }

    private int indexOfTrimmedLine(List<String> lines, String expectedLine) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(expectedLine)) {
                return i;
            }
        }
        throw new AssertionError("Line not found: " + expectedLine);
    }

    private int indexOfStartingLine(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("Line not found with prefix: " + prefix);
    }
}
