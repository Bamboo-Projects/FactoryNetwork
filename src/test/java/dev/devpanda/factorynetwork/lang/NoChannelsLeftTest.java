package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kein sichtbarer Text spricht mehr von Kanälen.
 *
 * <p><b>Der Lauf, der gefehlt hat.</b> Die Kanäle fielen am 29.08. aus dem
 * Code, aber neun Sprachtexte redeten weiter von ihnen — am Kabel stand
 * „16 Kanäle", während die Rechnung längst Byte zählte. Kein Prüflauf sah
 * das: Sie prüfen Verhalten, und ein falscher Name ist kein Verhalten.
 *
 * <p>Aufgefallen ist es dem User im Spiel. Deshalb steht der Wächter hier.
 */
class NoChannelsLeftTest {

    private static final Path LANG = Path.of(
            "src/main/resources/assets/factorynetwork/lang");

    /** Wörter, die es im Spiel nicht mehr geben darf. */
    private static final List<String> VERBOTEN = List.of(
            "Kanal", "Kanäle", "Kanälen", "channel", "Channel");

    @Test
    @DisplayName("Keine Sprachdatei nennt Kanäle")
    void noLanguageFileMentionsChannels() throws IOException {
        List<String> gefunden = new ArrayList<>();
        for (Path datei : List.of(LANG.resolve("de_de.json"), LANG.resolve("en_us.json"))) {
            String inhalt = Files.readString(datei, StandardCharsets.UTF_8);
            for (String wort : VERBOTEN) {
                if (inhalt.contains(wort)) {
                    // Die Zeile mitgeben: Bei vierhundert Einträgen ist
                    // „irgendwo steht Kanal" keine brauchbare Auskunft.
                    inhalt.lines()
                            .filter(zeile -> zeile.contains(wort))
                            .forEach(zeile -> gefunden.add(
                                    datei.getFileName() + ": " + zeile.trim()));
                }
            }
        }
        assertTrue(gefunden.isEmpty(),
                "Diese Texte sprechen noch von Kanälen:\n" + String.join("\n", gefunden));
    }
}
