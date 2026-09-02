package dev.devpanda.factorynetwork.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Gegenstück zu {@link PackageBoundaryTest}: FactoryNetwork ist Nutzer der
 * CEF-API, kein Sonderfall.
 *
 * <p><b>Warum als Prüflauf und nicht als Vorsatz.</b> „Die Mod fasst nur die
 * API an" ist leicht gesagt und leicht gebrochen — ein bequemer Import in die
 * Runtime, und die Grenze, um die es geht, ist weg. Hier fällt das beim Bauen
 * auf.
 *
 * <p>Erlaubt ist aus dem Web-Paket nur die zugesagte Schnittstelle
 * ({@code web.api}) und die beiden Helfer, die eine Seite ablegen
 * ({@code WebPage}, {@code WebAssets}), dazu die Sichtbarkeitsstufe.
 *
 * <p><b>Fünf Klassen sind ausgenommen, mit Namen.</b> Sie sind kein
 * ausgeliefertes Verhalten, sondern das Dev-Werkzeug der Runtime und die
 * Verdrahtung ihres Lebenslaufs — der Nachweis-Ablauf, die DevTools, die
 * Mess- und Prüfbefehle, die Ticks im Client und die Anmeldung in der
 * Hauptklasse. Sie gehören auf die Runtime-Seite und ziehen mit der
 * physischen Trennung der Mods dorthin. Bis dahin stehen sie hier, damit die
 * Grenze für alles <i>andere</i> — den Editor, die Overlays, die
 * Weltflächen — schon heute gilt.
 */
class ModBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/dev/devpanda/factorynetwork");
    private static final Path RUNTIME = ROOT.resolve("web");

    private static final String WEB = "dev.devpanda.factorynetwork.web.";
    private static final List<String> ALLOWED = List.of(
            "dev.devpanda.factorynetwork.web.api.",
            "dev.devpanda.factorynetwork.web.WebPage",
            "dev.devpanda.factorynetwork.web.WebAssets",
            "dev.devpanda.factorynetwork.web.BrowserVisibility");

    /** Dev-Werkzeug und Lebenslauf-Verdrahtung — noch offen, siehe Klassendoku. */
    private static final Set<String> EXEMPT = Set.of(
            "FactoryNetwork.java",
            "FnClient.java",
            "WebCommands.java",
            "WebDevTools.java",
            "WebProofChain.java");

    private static List<Path> modSources() throws IOException {
        try (Stream<Path> found = Files.walk(ROOT)) {
            return found
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(RUNTIME))
                    .filter(p -> !EXEMPT.contains(p.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean allowed(String imported) {
        for (String prefix : ALLOWED) {
            if (imported.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Die Mod-Seite gibt es, und die Runtime-Seite auch")
    void bothSidesExist() {
        assertTrue(Files.isDirectory(ROOT) && Files.isDirectory(RUNTIME),
                "ohne beide Seiten prüft dieser Lauf nichts");
    }

    @Test
    @DisplayName("Die Mod fasst aus dem Web-Paket nur die API an")
    void theModTouchesOnlyTheApi() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : modSources()) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (!line.startsWith("import ")) {
                    continue;
                }
                String imported = line.substring("import ".length())
                        .replace("static ", "").replace(";", "").strip();
                if (imported.startsWith(WEB) && !allowed(imported)) {
                    breaches.add("%s:%d — %s".formatted(
                            ROOT.relativize(source), i + 1, imported));
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "FactoryNetwork darf aus dem Web-Paket nur web.api (plus WebPage, "
                        + "WebAssets, BrowserVisibility) kennen:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("Und auch nicht über den vollen Namen, am Import vorbei")
    void norByFullyQualifiedName() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : modSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            int from = 0;
            while (true) {
                int at = text.indexOf(WEB, from);
                if (at < 0) {
                    break;
                }
                from = at + WEB.length();
                String rest = text.substring(at);
                if (allowed(rest) || rest.startsWith("dev.devpanda.factorynetwork.web;")) {
                    continue;
                }
                // Der eigene Paketkopf und Importzeilen zählen nicht.
                int lineStart = text.lastIndexOf('\n', at) + 1;
                String line = text.substring(lineStart, Math.min(text.length(),
                        text.indexOf('\n', at) < 0 ? text.length() : text.indexOf('\n', at))).strip();
                if (line.startsWith("package ") || line.startsWith("import ")) {
                    continue;
                }
                breaches.add("%s — %s…".formatted(
                        ROOT.relativize(source),
                        text.substring(at, Math.min(text.length(), at + 60))));
            }
        }
        assertTrue(breaches.isEmpty(),
                "Kein Griff mitten im Code an der API vorbei:\n  "
                        + String.join("\n  ", breaches));
    }
}
