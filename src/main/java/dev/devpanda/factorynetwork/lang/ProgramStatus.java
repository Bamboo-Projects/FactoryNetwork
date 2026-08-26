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
 * Was das Spiel über ein Projekt weiß, für einen Editor daneben.
 *
 * <p><b>Der Rückweg der Brücke.</b> Hin funktioniert sie längst: Wer in
 * VS Code speichert, dessen Programm übernimmt der Controller im
 * Sekundentakt. Zurück kam bisher nichts — ein Fehler im Programm stand im
 * Terminal, und wer nicht im Spiel war, sah eine Datei, die stumm nicht lief.
 *
 * <p>Diese Datei schließt den Kreis. Sie liegt neben den Programmdateien und
 * trägt zwei Dinge, die ein Editor allein nicht wissen kann:
 *
 * <ul>
 *   <li><b>Die Fehler</b>, so wie der Übersetzer sie sieht — mit Datei, Zeile
 *       und Spalte. Kein zweiter Übersetzer in JavaScript, keine zweite
 *       Fassung derselben Regeln: Es rechnet der, der es ohnehin tut.</li>
 *   <li><b>Die Namen aus der Welt</b> — Connectoren und Anzeigen. Sie stehen
 *       in keiner Datei; sie kommen aus der Beschriftungspistole.</li>
 * </ul>
 *
 * <p><b>Kein Port, keine neue Erlaubnis.</b> Das ist der Einzelspieler-Schnitt
 * von Punkt 4.1: Er benutzt den Kanal, den es gibt, und macht keinen neuen
 * auf. Die Schnittstelle für Server — mit den zwei Stufen aus
 * {@code entscheidungen.md} — bleibt davon unberührt und offen.
 *
 * <p><b>Von Hand geschrieben, ohne JSON-Bibliothek.</b> Die Mod hat keine im
 * Übersetzungspfad, und der Inhalt ist flach: zwei Listen und eine Karte. Ein
 * Schreiber von vierzig Zeilen ist billiger als eine Abhängigkeit — und was
 * ihn lesen muss, ist ohnehin JavaScript.
 *
 * @param diagnostics Fehler und Warnungen, nach Datei
 * @param connectors  die benannten Geräte im Netz
 * @param displays    die Anzeigen in der Welt
 */
public record ProgramStatus(Map<String, List<Problem>> diagnostics,
                            List<String> connectors, List<String> displays) {

    /** So heißt die Datei im Ordner des Controllers. */
    public static final String FILE = ".fn-status.json";

    /**
     * Ein Fehler, wie ein Editor ihn braucht.
     *
     * <p>Zeile und Spalte zählen ab eins, wie im Übersetzer. Wer sie in
     * VS Code einträgt, zieht selbst eine ab — dort zählt alles ab null, und
     * die Umrechnung gehört dorthin, wo sie gebraucht wird.
     */
    public record Problem(int line, int column, int length, String severity,
                          String message, String hint) {
    }

    /** Der leere Stand — für einen Ordner, in dem noch nichts steht. */
    public static ProgramStatus empty() {
        return new ProgramStatus(Map.of(), List.of(), List.of());
    }

    /**
     * Schreibt den Stand neben die Programmdateien.
     *
     * <p><b>Nur bei Änderung.</b> Ein Editor mit Dateiwächter würde sonst
     * jede Sekunde geweckt, obwohl sich nichts geändert hat — und läse
     * denselben Inhalt noch einmal.
     */
    public static void write(Path folder, List<Diagnostic> problems,
                             List<String> connectors, List<String> displays) {
        String json = toJson(problems, connectors, displays);
        Path file = folder.resolve(FILE);
        try {
            if (Files.exists(file)
                    && json.equals(Files.readString(file, StandardCharsets.UTF_8))) {
                return;
            }
            Files.createDirectories(folder);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Ein schreibgeschützter Weltordner ist kein Fehler des Netzes —
            // dieselbe Haltung wie beim Schreiben der Programmdateien.
        }
    }

    /**
     * Liest den Stand, oder gibt einen leeren.
     *
     * <p>Gebraucht wird das Lesen nur zum Prüfen: Im Betrieb liest die
     * Erweiterung, und die ist in JavaScript. Ein Format, das nur eine Seite
     * schreiben kann, ließe sich aber nicht nachmessen.
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

    // ---- Schreiben ---------------------------------------------------------

    private static String toJson(List<Diagnostic> problems, List<String> connectors,
                                 List<String> displays) {
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
        return out.append("\n}\n").toString();
    }

    private static String problemJson(Diagnostic problem) {
        Span span = problem.span();
        return "{\"line\": " + span.line()
                + ", \"column\": " + span.column()
                + ", \"length\": " + Math.max(1, span.end() - span.start())
                + ", \"severity\": " + quote(problem.isError() ? "error" : "warning")
                + ", \"message\": " + quote(problem.message())
                + ", \"hint\": " + quote(problem.hint()) + "}";
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
                        // Umlaute bleiben Umlaute: Die Datei ist UTF-8, und
                        // ein ü in einer Fehlermeldung liest niemand.
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ---- Lesen -------------------------------------------------------------

    /**
     * Ein Leser für genau dieses Format.
     *
     * <p>Kein allgemeiner JSON-Leser: Er kennt die drei Schlüssel, die
     * {@link #toJson} schreibt, und sonst nichts. Was er nicht versteht,
     * überspringt er — eine Statusdatei aus einer künftigen Fassung soll
     * einen alten Prüflauf nicht anhalten.
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
                stringList(json, "connectors"), stringList(json, "displays"));
    }

    private static List<Problem> problems(String body) {
        List<Problem> found = new ArrayList<>();
        int at = 0;
        while ((at = body.indexOf('{', at)) >= 0) {
            int end = body.indexOf('}', at);
            String one = body.substring(at + 1, end);
            found.add(new Problem(number(one, "line"), number(one, "column"),
                    number(one, "length"), text(one, "severity"),
                    text(one, "message"), text(one, "hint")));
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

    /** Die schließende Klammer zur öffnenden an dieser Stelle. */
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
