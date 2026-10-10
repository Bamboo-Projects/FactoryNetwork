package dev.devpanda.factorynetwork.client;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod loads without BambooCEF.
 *
 * <p><b>What that takes.</b> BambooCEF is optional. In a game without it, the
 * first class that names one of its types cannot be loaded, and the client
 * stops. So the classes that do name them — the web classes — must never be
 * reached unless the mod list says BambooCEF is there.
 *
 * <p><b>What is checked.</b> Three rules that together keep that true:
 *
 * <ol>
 *   <li>Only the web classes name BambooCEF.</li>
 *   <li>Only {@code WebBridge} is named from outside the web classes — it is
 *       the one door.</li>
 *   <li>Whoever names {@code WebBridge} also asks {@code WebSupport.installed()}.</li>
 * </ol>
 *
 * <p><b>What is not.</b> That every call really stands behind the question,
 * and that the game starts without the mod. The first needs a reader, the
 * second a start of the client with BambooCEF taken out.
 */
class WebBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/dev/devpanda/factorynetwork");

    /** The classes that may name BambooCEF, by path below the root. */
    private static final Set<String> WEB_CLASSES = Set.of(
            "client/EditorApp.java",
            "client/EditorSession.java",
            "client/IdeLauncher.java",
            "client/OverlayDemo.java",
            "client/OverlayProof.java",
            "client/WebBridge.java",
            "client/WebCommands.java",
            "client/WorldPointer.java",
            "client/WorldSurfaceDemo.java",
            "client/screen/WebEditorLoadingScreen.java");

    private static final Pattern BAMBOO = Pattern.compile("dev\\.devpanda\\.bamboocef\\.");

    private static final Pattern WEB_CLASS_NAME = Pattern.compile(
            "\\b(EditorApp|EditorSession|IdeLauncher|OverlayDemo|OverlayProof|WebCommands"
                    + "|WorldPointer|WorldSurfaceDemo|WebEditorLoadingScreen)\\b");

    private static boolean isWeb(String relative) {
        // The benchmarks are development tooling built on the runtime; they
        // are reached only through IdeLauncher.
        return WEB_CLASSES.contains(relative) || relative.startsWith("client/bench/");
    }

    private static boolean isCode(String line) {
        String code = line.strip();
        return !(code.startsWith("*") || code.startsWith("/*") || code.startsWith("//"));
    }

    private interface Visitor {
        void visit(String relative, List<String> lines);
    }

    private static int walk(Visitor visitor) throws IOException {
        int seen = 0;
        try (Stream<Path> sources = Files.walk(ROOT)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = ROOT.relativize(source).toString().replace('\\', '/');
                visitor.visit(relative, Files.readAllLines(source, StandardCharsets.UTF_8));
                seen++;
            }
        }
        return seen;
    }

    @Test
    @DisplayName("Only the web classes name BambooCEF")
    void onlyTheWebClassesNameTheRuntime() throws IOException {
        List<String> breaches = new ArrayList<>();
        int seen = walk((relative, lines) -> {
            if (isWeb(relative)) {
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (isCode(lines.get(i)) && BAMBOO.matcher(lines.get(i)).find()) {
                    breaches.add(relative + ":" + (i + 1));
                }
            }
        });
        assertTrue(seen > 100, "the walk found barely any sources; the path is probably wrong");
        assertTrue(breaches.isEmpty(),
                "These lines name BambooCEF outside the web classes. In a game without it "
                        + "their class cannot be loaded:\n  " + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("From outside, the web classes are reached through WebBridge only")
    void theBridgeIsTheOnlyDoor() throws IOException {
        List<String> breaches = new ArrayList<>();
        walk((relative, lines) -> {
            if (isWeb(relative)) {
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                Matcher found = WEB_CLASS_NAME.matcher(lines.get(i));
                if (isCode(lines.get(i)) && found.find()) {
                    breaches.add(relative + ":" + (i + 1) + " — " + found.group());
                }
            }
        });
        assertTrue(breaches.isEmpty(),
                "These lines reach a web class directly; they have to go through WebBridge:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("Whoever goes through WebBridge asks WebSupport first")
    void theBridgeIsOnlyTakenAfterAsking() throws IOException {
        List<String> breaches = new ArrayList<>();
        List<String> users = new ArrayList<>();
        walk((relative, lines) -> {
            if (isWeb(relative)) {
                return;
            }
            String code = String.join("\n", lines.stream().filter(WebBoundaryTest::isCode).toList());
            if (!code.contains("WebBridge")) {
                return;
            }
            users.add(relative);
            if (!code.contains("WebSupport.installed()")) {
                breaches.add(relative);
            }
        });
        assertFalse(users.isEmpty(),
                "nobody uses WebBridge — then this check looks at nothing and the names "
                        + "in it are probably stale");
        assertTrue(breaches.isEmpty(),
                "These files use WebBridge without asking WebSupport.installed():\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("Outside the game there is no web runtime")
    void withoutAmodListNothingIsInstalled() {
        assertFalse(WebSupport.installed());
    }
}
