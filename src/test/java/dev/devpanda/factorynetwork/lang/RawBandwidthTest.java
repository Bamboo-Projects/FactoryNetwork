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
 * Keine rohe Bandbreitenzahl erreicht den Bildschirm.
 *
 * <p><b>Zweimal ist genau das passiert.</b> Am Kabel stand „Pink: 500" statt
 * „10 KB/s", und im Router-Tooltip dieselbe Zahl gleich zweimal. Beide Male
 * war die Rechnung richtig und nur die Anzeige roh — und beide Male fiel es
 * erst im Spiel auf.
 *
 * <p>Der Wächter sucht nach {@code String.valueOf(…Bandwidth…)} und nach
 * einer Bandbreitenzahl, die ohne {@code perSecond} an eine Zeichenkette
 * gehängt wird. Er kennt keine Absicht — wer eine rohe Zahl wirklich
 * braucht, rechnet sie vorher in eine Variable.
 */
class RawBandwidthTest {

    private static final Path SOURCE = Path.of("src/main/java");

    /** {@code String.valueOf(irgendwas mit Bandwidth)} — die erste Falle. */
    private static final Pattern VALUE_OF = Pattern.compile(
            "String[.]valueOf[(]\\s*[^)]*Bandwidth[.][A-Za-z]+");

    /** Eine Bandbreitenzahl direkt an eine Zeichenkette gehängt. */
    private static final Pattern CONCAT = Pattern.compile(
            "[+]\\s*[\\w.]*Bandwidth[.](THIN|DENSE|UNLIMITED|at)\\b");

    @Test
    @DisplayName("Keine Bandbreite geht ungerechnet in einen Text")
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
