package dev.devpanda.factorynetwork.lang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the game knows about a project, for an editor alongside it.
 *
 * <p><b>The return path of the bridge.</b> The way there has worked for a long
 * time: whoever saves in VS Code has their program adopted by the controller
 * every second. Nothing came back the other way so far — an error in the
 * program stood in the terminal, and anyone not in the game saw a file that
 * silently did not run.
 *
 * <p>This file closes the loop. It sits next to the program files and carries
 * two things an editor cannot know on its own:
 *
 * <ul>
 *   <li><b>The errors</b>, the way the compiler sees them — with file, line,
 *       and column. No second compiler in JavaScript, no second version of the
 *       same rules: the one that does it anyway does the computing.</li>
 *   <li><b>The names from the world</b> — connectors and displays. They stand
 *       in no file; they come from the label gun.</li>
 *   <li><b>The prefixes of the resource kinds.</b> Since Aug 26 foreign mods
 *       may register their own, and what applies in a pack only the running
 *       game knows. Without this line an editor would suggest exactly the four
 *       it knows itself — and keep quiet about the rest.</li>
 * </ul>
 *
 * <p><b>No port, no new permission.</b> This is the single-player cut of
 * point 4.1: it uses the channel that exists and does not open a new one. The
 * interface for servers — with the two stages from {@code entscheidungen.md} —
 * stays untouched by this and open.
 *
 * <p><b>Written by hand, without a JSON library.</b> The mod has none in the
 * compilation path, and the content is flat: two lists and a map. A writer of
 * forty lines is cheaper than a dependency — and what has to read it is
 * JavaScript anyway.
 *
 * @param diagnostics errors and warnings, by file
 * @param connectors  the named devices in the network
 * @param displays    the displays in the world
 * @param prefixes    the resource kinds this pack knows
 */
public record ProgramStatus(Map<String, List<Problem>> diagnostics,
                            List<String> connectors, List<String> displays,
                            List<String> prefixes) {

    /** The name of the file in the controller's folder. */
    public static final String FILE = ".fn-status.json";

    /**
     * An error the way an editor needs it.
     *
     * <p>Line and column count from one, as in the compiler. Whoever enters
     * them into VS Code subtracts one themselves — there everything counts from
     * zero, and the conversion belongs where it is needed.
     */
    public record Problem(int line, int column, int length, String severity,
                          String message, String hint,
                          String fixText, int fixLine, int fixColumn, int fixLength) {

        /** Without an applicable suggestion. */
        public Problem(int line, int column, int length, String severity,
                       String message, String hint) {
            this(line, column, length, severity, message, hint, "", 0, 0, 0);
        }

        /** Does this message carry a suggestion that an editor can apply? */
        public boolean hasFix() {
            return fixText != null && !fixText.isEmpty();
        }
    }

    /** The empty status — for a folder in which nothing stands yet. */
    public static ProgramStatus empty() {
        return new ProgramStatus(Map.of(), List.of(), List.of(), List.of());
    }

    /**
     * Writes the status next to the program files.
     *
     * <p><b>Only on change.</b> An editor with a file watcher would otherwise
     * be woken every second even though nothing has changed — and would read
     * the same content once more.
     */
    public static void write(Path folder, List<Diagnostic> problems,
                             List<String> connectors, List<String> displays,
                             List<String> prefixes) {
        String json = toJson(problems, connectors, displays, prefixes);
        Path file = folder.resolve(FILE);
        try {
            if (Files.exists(file)
                    && json.equals(Files.readString(file, StandardCharsets.UTF_8))) {
                return;
            }
            Files.createDirectories(folder);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // A read-only world folder is not an error of the network — the
            // same stance as when writing the program files.
        }
    }

    /**
     * Reads the status, or returns an empty one.
     *
     * <p>Reading is needed only for testing: in operation the extension reads,
     * and that is in JavaScript. But a format only one side can write could not
     * be verified.
     */
    public static ProgramStatus read(Path folder) {
        try {
            Path file = folder.resolve(FILE);
            return Files.exists(file)
                    ? fromJson(Files.readString(file, StandardCharsets.UTF_8))
                    : empty();
        } catch (IOException | RuntimeException unreadable) {
            return empty();
        }
    }

    // ---- Writing -----------------------------------------------------------

    private static String toJson(List<Diagnostic> problems, List<String> connectors,
                                 List<String> displays, List<String> prefixes) {
        Map<String, List<Diagnostic>> byFile = new LinkedHashMap<>();
        for (Diagnostic problem : problems) {
            byFile.computeIfAbsent(problem.file(), name -> new ArrayList<>()).add(problem);
        }
        StringBuilder out = new StringBuilder("{\n  \"diagnostics\": {");
        boolean firstFile = true;
        for (Map.Entry<String, List<Diagnostic>> entry : byFile.entrySet()) {
            out.append(firstFile ? "\n" : ",\n");
            firstFile = false;
            out.append("    ").append(quote(entry.getKey())).append(": [");
            boolean firstProblem = true;
            for (Diagnostic problem : entry.getValue()) {
                out.append(firstProblem ? "\n" : ",\n");
                firstProblem = false;
                out.append("      ").append(problemJson(problem));
            }
            out.append(firstProblem ? "]" : "\n    ]");
        }
        out.append(firstFile ? "}" : "\n  }");
        out.append(",\n  \"connectors\": ").append(array(connectors));
        out.append(",\n  \"displays\": ").append(array(displays));
        out.append(",\n  \"prefixes\": ").append(array(prefixes));
        return out.append("\n}\n").toString();
    }

    private static String problemJson(Diagnostic problem) {
        Span span = problem.span();
        return "{\"line\": " + span.line()
                + ", \"column\": " + span.column()
                + ", \"length\": " + Math.max(1, span.end() - span.start())
                + ", \"severity\": " + quote(problem.isError() ? "error" : "warning")
                + ", \"message\": " + quote(problem.message())
                + ", \"hint\": " + quote(problem.hint())
                + fixJson(problem.fix()) + "}";
    }

    /**
     * The applicable suggestion, written flat alongside.
     *
     * <p>Flat and not as its own object: the reader above here is written by
     * hand and knows numbers and strings — a nested object would be a second
     * reader.
     *
     * <p>Without a suggestion nothing stands there at all. An empty field would
     * be the same thing at greater length.
     */
    private static String fixJson(Diagnostic.Fix fix) {
        if (fix == null) {
            return "";
        }
        return ", \"fixText\": " + quote(fix.text())
                + ", \"fixLine\": " + fix.span().line()
                + ", \"fixColumn\": " + fix.span().column()
                + ", \"fixLength\": "
                + Math.max(1, fix.span().end() - fix.span().start());
    }

    private static String array(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(quote(values.get(i)));
        }
        return out.append(']').toString();
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        // Umlauts stay umlauts: the file is UTF-8, and no one
                        // reads an escaped umlaut in an error message.
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ---- Reading -----------------------------------------------------------

    /**
     * A reader for exactly this format.
     *
     * <p>Not a general JSON reader: it knows the three keys that
     * {@link #toJson} writes, and nothing else. What it does not understand it
     * skips — a status file from a future version should not halt an old test
     * run.
     */
    private static ProgramStatus fromJson(String json) {
        Map<String, List<Problem>> diagnostics = new LinkedHashMap<>();
        int at = json.indexOf("\"diagnostics\"");
        if (at >= 0) {
            int open = json.indexOf('{', at);
            int close = matching(json, open);
            String body = json.substring(open + 1, Math.max(open + 1, close));
            for (int i = 0; i < body.length(); i++) {
                if (body.charAt(i) != '"') {
                    continue;
                }
                int end = body.indexOf('"', i + 1);
                String file = body.substring(i + 1, end);
                int listStart = body.indexOf('[', end);
                int listEnd = body.indexOf(']', listStart);
                diagnostics.put(file, problems(body.substring(listStart + 1, listEnd)));
                i = listEnd;
            }
        }
        return new ProgramStatus(Map.copyOf(diagnostics),
                stringList(json, "connectors"), stringList(json, "displays"),
                stringList(json, "prefixes"));
    }

    private static List<Problem> problems(String body) {
        List<Problem> found = new ArrayList<>();
        int at = 0;
        while ((at = body.indexOf('{', at)) >= 0) {
            int end = body.indexOf('}', at);
            String one = body.substring(at + 1, end);
            found.add(new Problem(number(one, "line"), number(one, "column"),
                    number(one, "length"), text(one, "severity"),
                    text(one, "message"), text(one, "hint"),
                    text(one, "fixText"), number(one, "fixLine"),
                    number(one, "fixColumn"), number(one, "fixLength")));
            at = end + 1;
        }
        return List.copyOf(found);
    }

    private static int number(String body, String key) {
        int at = body.indexOf('"' + key + '"');
        if (at < 0) {
            return 0;
        }
        int start = body.indexOf(':', at) + 1;
        StringBuilder digits = new StringBuilder();
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isDigit(c) || (c == '-' && digits.isEmpty())) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }

    private static String text(String body, String key) {
        int at = body.indexOf('"' + key + '"');
        if (at < 0) {
            return "";
        }
        int open = body.indexOf('"', body.indexOf(':', at) + 1);
        StringBuilder out = new StringBuilder();
        for (int i = open + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<String> stringList(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return List.of();
        }
        int open = json.indexOf('[', at);
        int close = json.indexOf(']', open);
        List<String> found = new ArrayList<>();
        String body = json.substring(open + 1, Math.max(open + 1, close));
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) != '"') {
                continue;
            }
            int end = body.indexOf('"', i + 1);
            found.add(body.substring(i + 1, end));
            i = end;
        }
        return List.copyOf(found);
    }

    /** The closing brace matching the opening one at this position. */
    private static int matching(String json, int open) {
        int depth = 0;
        boolean inText = false;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inText) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inText = false;
                }
                continue;
            }
            if (c == '"') {
                inText = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return json.length();
    }
}
