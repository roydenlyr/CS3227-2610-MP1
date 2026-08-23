package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromoCodeTest {
    @Test
    void fixedCodes_haveExpectedDiscountPercentages() {
        assertAll(
                () -> assertEquals("CS2103", PromoCode.CS2103.getCode()),
                () -> assertEquals(20, PromoCode.CS2103.getDiscountPercentage()),
                () -> assertEquals("CS3227", PromoCode.CS3227.getCode()),
                () -> assertEquals(99, PromoCode.CS3227.getDiscountPercentage()));
    }

    @Test
    void parse_supportedCodes_ignoresCaseAndSurroundingWhitespace() {
        assertAll(
                () -> assertEquals(PromoCode.CS2103, PromoCode.parse(" cs2103 ")),
                () -> assertEquals(PromoCode.CS3227, PromoCode.parse("Cs3227")));
    }

    @Test
    void parse_blankOrUnsupportedCode_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromoCode.parse("")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromoCode.parse("SAVE20")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PromoCode.parse("CS21030")));
    }
}
