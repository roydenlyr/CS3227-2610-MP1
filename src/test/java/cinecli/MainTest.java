package cinecli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void mainCompletesWithNoArguments() {
        assertDoesNotThrow(() -> Main.main(new String[0]));
    }
}
