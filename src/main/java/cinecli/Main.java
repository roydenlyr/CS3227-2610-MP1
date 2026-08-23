package cinecli;

import static java.nio.charset.StandardCharsets.UTF_8;

import cinecli.storage.CatalogStorage;
import cinecli.storage.SeatStorage;
import cinecli.ui.CustomerUi;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

/**
 * Provides the application entry point.
 */
public final class Main {
    private static final Path RUNTIME_CATALOG_PATH = Path.of("data", "runtime", "catalog.tsv");
    private static final Path RUNTIME_SEATS_PATH = Path.of("data", "runtime", "seats.tsv");
    private static final String DEFAULT_CATALOG_RESOURCE = "/cinecli/default-catalog.tsv";

    private Main() {
        // Prevent instantiation.
    }

    /**
     * Starts the application.
     *
     * @param args Command-line arguments; currently unused.
     */
    public static void main(String[] args) {
        Reader input = new InputStreamReader(System.in, UTF_8);
        Writer output = new OutputStreamWriter(System.out, UTF_8);
        Writer errorOutput = new OutputStreamWriter(System.err, UTF_8);
        run(input, output, errorOutput, RUNTIME_CATALOG_PATH, RUNTIME_SEATS_PATH);
    }

    /**
     * Runs the customer workflow using a catalog path and a sibling seat occupancy path.
     *
     * @param input User input source.
     * @param output Normal user output destination.
     * @param errorOutput Error output destination.
     * @param runtimeCatalogPath Runtime catalog path.
     */
    static void run(Reader input, Writer output, Writer errorOutput, Path runtimeCatalogPath) {
        run(input, output, errorOutput, runtimeCatalogPath, runtimeCatalogPath.resolveSibling("seats.tsv"));
    }

    /**
     * Runs the customer workflow using the supplied inputs, outputs, and runtime data paths.
     *
     * @param input User input source.
     * @param output Normal user output destination.
     * @param errorOutput Error output destination.
     * @param runtimeCatalogPath Runtime catalog path.
     * @param runtimeSeatsPath Temporary runtime seat occupancy path.
     */
    static void run(
            Reader input,
            Writer output,
            Writer errorOutput,
            Path runtimeCatalogPath,
            Path runtimeSeatsPath) {
        CustomerUi customerUi = new CustomerUi(input, output, errorOutput);
        CatalogStorage catalogStorage = new CatalogStorage(
                runtimeCatalogPath, DEFAULT_CATALOG_RESOURCE);
        SeatStorage seatStorage = new SeatStorage(runtimeSeatsPath);
        CustomerApplication customerApplication = new CustomerApplication(
                customerUi, catalogStorage, seatStorage);
        customerApplication.run();
    }
}
