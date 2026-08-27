package dev.devpanda.factorynetwork.lang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Hervorhebung in VS Code kennt jedes Wort der Sprache.
 *
 * <p><b>Gefunden beim ersten Öffnen einer echten Programmdatei</b> (27.08.):
 * Im Editor stand {@code store kiste_1 { }} — und {@code store} war farblos
 * wie ein Name. Fünf Wörter fehlten in der Grammatik: {@code store},
 * {@code recipe}, {@code scale}, {@code at} und {@code out}. Alle fünf sind
 * später zur Sprache dazugekommen, und niemand hat die zweite Liste
 * nachgezogen.
 *
 * <p><b>Das ist die Falle, die in dieser Sitzung viermal zugeschlagen hat:</b>
 * Dieselbe Auskunft steht an zwei Stellen, und eine davon altert. Hier ist die
 * eine {@link TokenType} — die Wahrheit, gegen die der Lexer arbeitet —, die
 * andere eine TextMate-Grammatik in JSON, die kein Compiler liest.
 *
 * <p>Ein farbloses Schlüsselwort ist kein Fehler, der etwas kaputtmacht. Es
 * fällt niemandem auf, der die Sprache kennt, und verwirrt jeden, der sie
 * lernt — genau die Sorte Mangel, die sich ohne Prüflauf ewig hält.
 */
class GrammarKeywordTest {

    private static final Path GRAMMAR =
            Path.of("editor/vscode/syntaxes/manifold.tmLanguage.json");

    @Test
    @DisplayName("Jedes Schlüsselwort steht auch in der Grammatik für VS Code")
    void everyKeywordIsAlsoInTheGrammar() throws IOException {
        assertTrue(Files.exists(GRAMMAR), () -> GRAMMAR + " fehlt");
        String grammar = Files.readString(GRAMMAR, StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();
        for (String keyword : TokenType.keywords()) {
            // Als ganzes Wort: „in" steckt in „indicator", und ein Treffer
            // darin bewiese nichts.
            Pattern whole = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(keyword)
                    + "(?![A-Za-z])");
            if (!whole.matcher(grammar).find()) {
                missing.add(keyword);
            }
        }

        assertTrue(missing.isEmpty(),
                () -> "Diese Wörter gehören der Sprache, aber der Editor färbt sie "
                        + "nicht: " + missing + ". Sie gehören in "
                        + "manifold.tmLanguage.json — ein Schlüsselwort, das "
                        + "aussieht wie ein Name, verwirrt jeden, der die Sprache "
                        + "lernt.");
    }

    @Test
    @DisplayName("Die Grammatik erfindet keine Wörter, die es nicht gibt")
    void thegrammarInventsNoWordsThatDoNotExist() throws IOException {
        String grammar = Files.readString(GRAMMAR, StandardCharsets.UTF_8);

        // Die andere Richtung, und sie ist die schwächere: Die Grammatik führt
        // auch Wörter, die keine Schlüsselwörter sind — Ressourcenpräfixe wie
        // „item" etwa, und die stehen in einer Registry. Geprüft werden
        // deshalb nur die Muster, die ausdrücklich Schlüsselwörter meinen.
        List<String> unknown = new ArrayList<>();
        for (String group : new String[] {"declaration", "control", "worker", "display"}) {
            List<String> words = wordsOf(grammar, group);
            // Ohne diese Zeile bestünde der Test hohl: Findet der Ausdruck
            // nichts, ist die Liste leer und alles scheinbar in Ordnung.
            assertTrue(words.size() >= 5,
                    () -> "Aus " + group + " kamen nur " + words.size()
                            + " Wörter — der Ausdruck greift nicht mehr");
            for (String word : words) {
                if (TokenType.keyword(word) == null) {
                    unknown.add(group + ": " + word);
                }
            }
        }

        assertTrue(unknown.isEmpty(),
                () -> "Der Editor färbt Wörter, die die Sprache nicht kennt: " + unknown);
    }

    /** Die Wörter aus einem Muster der Form {@code \\b(a|b|c)\\b}. */
    private static List<String> wordsOf(String grammar, String group) {
        var entry = Pattern.compile("\"" + group + "\"\\s*:\\s*\\{[^}]*?\"match\"\\s*:\\s*"
                + "\"\\\\\\\\b\\(([^)]*)\\)").matcher(grammar);
        List<String> found = new ArrayList<>();
        if (entry.find()) {
            for (String word : entry.group(1).split("\\|")) {
                if (!word.isBlank()) {
                    found.add(word);
                }
            }
        }
        return found;
    }
}
