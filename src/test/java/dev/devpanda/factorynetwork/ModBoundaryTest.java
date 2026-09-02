package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FactoryNetwork is a consumer of the web runtime, not a special case.
 *
 * <p><b>Why as a check run and not as an intention.</b> "The mod only touches
 * the API" is easily said and easily broken — one convenient import into the
 * runtime, and the boundary this is all about is gone. Here it shows up at
 * build time.
 *
 * <p>The runtime itself is another mod now (BambooCEF). Allowed from it is the
 * promised interface ({@code web.api}) and the two helpers that put a page on
 * disk ({@code WebPage}, {@code WebAssets}), plus the visibility level.
 * Everything else — sessions, frames, screens, the input translation — is that
 * mod's business, and a reach into it would tie this build to its internals.
 *
 * <p><b>One exemption, and it is a directory.</b> The benchmarks under
 * {@code client/bench} are development tooling: they subclass the runtime's
 * own screens to measure them. They are not shipped behavior, and no player
 * ever reaches them. The exemption hangs on the path and not on file names, so
 * a new class does not slip through it by accident.
 */
class ModBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/dev/devpanda/factorynetwork");

    /** Development tooling, exempt by directory — see the class documentation. */
    private static final Path BENCH = ROOT.resolve("client").resolve("bench");

    private static final String LIB = "dev.devpanda.bamboocef.";
    private static final List<String> ALLOWED = List.of(
            "dev.devpanda.bamboocef.web.api.",
            "dev.devpanda.bamboocef.web.WebPage",
            "dev.devpanda.bamboocef.web.WebAssets",
            "dev.devpanda.bamboocef.web.BrowserVisibility");

    private static List<Path> modSources() throws IOException {
        try (Stream<Path> found = Files.walk(ROOT)) {
            return found
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(BENCH))
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
    @DisplayName("There are sources to check, and the exempt directory exists")
    void thereIsSomethingToCheck() throws IOException {
        assertTrue(Files.isDirectory(ROOT) && Files.isDirectory(BENCH),
                "without both paths this run checks nothing — it would be green and worthless");
        assertTrue(modSources().size() > 100,
                "the walk found barely any sources; the path is probably wrong");
    }

    @Test
    @DisplayName("The mod touches only the API of the web runtime")
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
                if (imported.startsWith(LIB) && !allowed(imported)) {
                    breaches.add("%s:%d — %s".formatted(
                            ROOT.relativize(source), i + 1, imported));
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "FactoryNetwork may know only web.api (plus WebPage, WebAssets, "
                        + "BrowserVisibility) of BambooCEF:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("And not via the full name either, bypassing the import")
    void norByFullyQualifiedName() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : modSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            int from = 0;
            while (true) {
                int at = text.indexOf(LIB, from);
                if (at < 0) {
                    break;
                }
                from = at + LIB.length();
                String rest = text.substring(at);
                if (allowed(rest)) {
                    continue;
                }
                // The file's own package header and import lines do not count.
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
                "No reach in the middle of the code that bypasses the API:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("Nothing in this mod compiles against Chromium")
    void nothingTouchesChromium() throws IOException {
        // <b>Not even the exempt benchmarks.</b> org.cef comes from the native
        // runtime and exists in exactly one place: BambooCEF. A single import
        // here would tie this build to a Chromium version — and it is precisely
        // that dependency the split was meant to end.
        List<String> breaches = new ArrayList<>();
        try (Stream<Path> found = Files.walk(ROOT)) {
            for (Path source : found.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("import org.cef")) {
                        breaches.add("%s:%d — %s".formatted(
                                ROOT.relativize(source), i + 1, line));
                    }
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "org.cef belongs to BambooCEF and nowhere else:\n  "
                        + String.join("\n  ", breaches));
    }
}
