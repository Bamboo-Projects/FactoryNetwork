package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No raw bandwidth number reaches the screen.
 *
 * <p><b>Exactly that happened twice.</b> The cable said "Pink: 500" instead
 * of "10 KB/s", and the router tooltip showed the same number twice over.
 * Both times the arithmetic was right and only the display was raw — and
 * both times it was noticed only in game.
 *
 * <p>The guard looks for {@code String.valueOf(…Bandwidth…)} and for a
 * bandwidth number that is appended to a string without {@code perSecond}.
 * It knows no intent — whoever really needs a raw number computes it into a
 * variable beforehand.
 */
class RawBandwidthTest {

    private static final Path SOURCE = Path.of("src/main/java");

    /** {@code String.valueOf(irgendwas mit Bandwidth)} — the first trap. */
    private static final Pattern VALUE_OF = Pattern.compile(
            "String[.]valueOf[(]\\s*[^)]*Bandwidth[.][A-Za-z]+");

    /** A bandwidth number appended directly to a string. */
    private static final Pattern CONCAT = Pattern.compile(
            "[+]\\s*[\\w.]*Bandwidth[.](THIN|DENSE|UNLIMITED|at)\\b");

    @Test
    @DisplayName("No bandwidth goes into a text uncomputed")
    void noRawBandwidthReachesTheScreen() throws IOException {
        List<String> gefunden = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path datei : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String inhalt = Files.readString(datei, StandardCharsets.UTF_8);
                for (Pattern muster : List.of(VALUE_OF, CONCAT)) {
                    Matcher treffer = muster.matcher(inhalt);
                    while (treffer.find()) {
                        gefunden.add(SOURCE.relativize(datei) + ": " + treffer.group().trim());
                    }
                }
            }
        }
        assertTrue(gefunden.isEmpty(),
                "Diese Stellen zeigen eine rohe Bandbreitenzahl statt Bandwidth.perSecond:\n"
                        + String.join("\n", gefunden));
    }
}
