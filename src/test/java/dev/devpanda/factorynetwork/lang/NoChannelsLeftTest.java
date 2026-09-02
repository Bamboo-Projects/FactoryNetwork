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
 * No visible text speaks of channels any more.
 *
 * <p><b>The test that was missing.</b> The channels dropped out of the
 * code on 29.08., but nine language texts kept talking about them — the cable
 * said "16 channels" while the maths had long since counted bytes. No test saw
 * that: they check behaviour, and a wrong name is not behaviour.
 *
 * <p>It was the user who noticed it in game. That is why this guard is here.
 */
class NoChannelsLeftTest {

    private static final Path LANG = Path.of(
            "src/main/resources/assets/factorynetwork/lang");

    /** Words that must no longer appear in the game. */
    private static final List<String> VERBOTEN = List.of(
            "Kanal", "Kanäle", "Kanälen", "channel", "Channel");

    @Test
    @DisplayName("No language file names channels")
    void noLanguageFileMentionsChannels() throws IOException {
        List<String> gefunden = new ArrayList<>();
        for (Path datei : List.of(LANG.resolve("de_de.json"), LANG.resolve("en_us.json"))) {
            String inhalt = Files.readString(datei, StandardCharsets.UTF_8);
            for (String wort : VERBOTEN) {
                if (inhalt.contains(wort)) {
                    // Include the line: with four hundred entries,
                    // "Kanal appears somewhere" is not useful feedback.
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
