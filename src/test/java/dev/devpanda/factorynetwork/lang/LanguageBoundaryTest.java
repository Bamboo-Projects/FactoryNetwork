package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The language stands on its own: lexer, parser and checks need no game.
 *
 * <p><b>Why as a check run.</b> This is the part that can be carried to
 * another Minecraft version without a single change — as long as nothing in
 * it reaches for the game or for the rest of the mod. One convenient import
 * ends that, and nothing would say so.
 *
 * <p><b>One file is exempt:</b> {@code ProgramFolder} reads and writes the
 * program next to the world and needs the game for where that is.
 */
class LanguageBoundaryTest {

    private static final Path LANG = Path.of("src/main/java/dev/devpanda/factorynetwork/lang");

    private static final Set<String> EXEMPT = Set.of("ProgramFolder.java");

    /** The game, its loader, and every package of this mod except the two the language may use. */
    private static final Pattern FOREIGN = Pattern.compile(
            "\\b(net\\.minecraft|net\\.neoforged|com\\.mojang"
                    + "|dev\\.devpanda\\.factorynetwork\\.(?!lang\\b|util\\b)[a-z]+)");

    @Test
    @DisplayName("Nothing in the language reaches for the game or the rest of the mod")
    void theLanguageKnowsOnlyItself() throws IOException {
        List<String> breaches = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> sources = Files.walk(LANG)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (EXEMPT.contains(source.getFileName().toString())) {
                    continue;
                }
                checked++;
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String code = line.strip();
                    // Documentation may name what it is set apart from.
                    if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")) {
                        continue;
                    }
                    Matcher found = FOREIGN.matcher(line);
                    if (found.find()) {
                        breaches.add("%s:%d — %s".formatted(
                                LANG.relativize(source), i + 1, found.group()));
                    }
                }
            }
        }
        assertTrue(checked > 20, "the walk found barely any sources; the path is probably wrong");
        assertTrue(breaches.isEmpty(),
                "The language may use only its own package and util:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("The five words of the language are known without anybody registering them")
    void theBuiltInWordsAreAlwaysThere() {
        assertTrue(SelectorWords.all().containsAll(
                Set.of("item", "fluid", "chemical", "tag", "fluidtag")));
        assertEquals(Expr.Selector.Kind.ITEM, SelectorWords.kindOf("item"));
        assertEquals(Expr.Selector.Kind.FLUIDTAG, SelectorWords.kindOf("fluidtag"));
        assertNull(SelectorWords.kindOf("nosuchword"));
    }

    @Test
    @DisplayName("Without a game, chemical counts as missing its mod and the others as usable")
    void onlyChemicalNeedsAmod() {
        assertNull(SelectorWords.unavailable("item"));
        assertNull(SelectorWords.unavailable("fluid"));
        // Either the default or the kind's own answer — both say the mod is
        // missing in a run without a mod list.
        SelectorWords.Unavailable chemical = SelectorWords.unavailable("chemical");
        assertNotNull(chemical);
        assertTrue(chemical.message().contains("Mekanism"));
    }
}
