package cinecli.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromoCodeTest {
    @Test
    void constructor_validCodeAndPercentage_canonicalizesCode() {
        PromoCode promotion = new PromoCode(" cs2103-early_bird ", 20);

        assertAll(
                () -> assertEquals("CS2103-EARLY_BIRD", promotion.code()),
                () -> assertEquals(20, promotion.discountPercentage()));
    }

    @Test
    void constructor_codeBoundaryValues_created() {
        assertAll(
                () -> assertEquals("A", new PromoCode("a", 1).code()),
                () -> assertEquals("A1234567890123456789012345678901",
                        new PromoCode("a1234567890123456789012345678901", 100).code()));
    }

    @Test
    void constructor_invalidCodeOrPercentage_exceptionThrown() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("", 20)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("-SAVE20", 20)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("SAVE 20", 20)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("\u03a9MEGA", 20)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("A12345678901234567890123456789012", 20)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("CS2103", 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromoCode("CS2103", 101)),
                () -> assertThrows(NullPointerException.class,
                        () -> new PromoCode(null, 20)));
    }
}
