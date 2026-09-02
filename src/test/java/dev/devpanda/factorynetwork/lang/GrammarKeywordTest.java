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
 * The highlighting in VS Code knows every word of the language.
 *
 * <p><b>Found the first time a real program file was opened</b> (27.08.):
 * the editor showed {@code store kiste_1 { }} — and {@code store} was colourless
 * like a name. Five words were missing from the grammar: {@code store},
 * {@code recipe}, {@code scale}, {@code at} and {@code out}. All five were
 * added to the language later, and nobody kept the second list
 * in step.
 *
 * <p><b>This is the trap that sprang four times in this session:</b>
 * the same information sits in two places, and one of them ages. Here one
 * is {@link TokenType} — the truth the lexer works against —, the
 * other a TextMate grammar in JSON that no compiler reads.
 *
 * <p>A colourless keyword is not a mistake that breaks anything. It
 * goes unnoticed by anyone who knows the language, and confuses everyone who
 * is learning it — exactly the kind of flaw that lingers forever without a check.
 */
class GrammarKeywordTest {

    private static final Path GRAMMAR =
            Path.of("editor/vscode/syntaxes/manifold.tmLanguage.json");

    @Test
    @DisplayName("Every keyword is also in the grammar for VS Code")
    void everyKeywordIsAlsoInTheGrammar() throws IOException {
        assertTrue(Files.exists(GRAMMAR), () -> GRAMMAR + " fehlt");
        String grammar = Files.readString(GRAMMAR, StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();
        for (String keyword : TokenType.keywords()) {
            // As a whole word: "in" is inside "indicator", and a match
            // there would prove nothing.
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
    @DisplayName("The grammar invents no words that do not exist")
    void thegrammarInventsNoWordsThatDoNotExist() throws IOException {
        String grammar = Files.readString(GRAMMAR, StandardCharsets.UTF_8);

        // The other direction, and it is the weaker one: the grammar also
        // carries words that are not keywords — resource prefixes like
        // "item", say, and those live in a registry. So only the patterns
        // that explicitly mean keywords are checked.
        List<String> unknown = new ArrayList<>();
        for (String group : new String[] {"declaration", "control", "worker", "display"}) {
            List<String> words = wordsOf(grammar, group);
            // Without this line the test would be hollow: if the expression
            // finds nothing, the list is empty and everything looks fine.
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

    /** The words from a pattern of the form {@code \\b(a|b|c)\\b}. */
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
