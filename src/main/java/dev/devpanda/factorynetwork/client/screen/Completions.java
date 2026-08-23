package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorschläge für den Editor.
 *
 * <p>Der Punkt, an dem sich entscheidet, ob die Sprache benutzbar ist. In
 * einem Pack mit zwanzigtausend Gegenstandsarten ist eine alphabetische Liste
 * wertlos — es muss ausgewählt werden, nicht aufgezählt.
 *
 * <p>Deshalb wird nach der Stelle im Code unterschieden: Hinter {@code from}
 * und {@code to} stehen Geräte, hinter {@code filter} Gegenstände, am
 * Zeilenanfang in einem Worker seine Angaben. Ein Vorschlag, der überall
 * dasselbe zeigt, hilft nirgends.
 */
public final class Completions {

    /** Mehr als das wird nicht angezeigt; alles andere ist Scrollen ohne Nutzen. */
    private static final int MAX = 8;

    public record Entry(String text, String insert, Kind kind) {
        public enum Kind { KEYWORD, CONNECTOR, ITEM, TAG, BUILTIN }
    }

    /** Angaben, die nur in einem Worker vorkommen. */
    private static final List<String> WORKER_ENTRIES = List.of(
            "from", "to", "filter", "maintain", "rate", "when", "priority",
            "strategy", "overflow");

    /**
     * Angaben, die nur in einer Anzeige vorkommen.
     *
     * <p>Und <b>nur</b> diese: Ein {@code displayEntry} enthält Ausdrücke,
     * aber keine Anweisungen — kein {@code if}, keine Schleife, kein
     * {@code await}. Vorher landete der Cursor in einer Anzeige im Zweig für
     * Funktionen und bekam genau das vorgeschlagen, was dort verboten ist.
     */
    private static final List<String> DISPLAY_ENTRIES = List.of(
            "title", "row", "text", "progress", "indicator", "list", "button");

    /** Angaben, die nur in einer Gruppe vorkommen. */
    private static final List<String> GROUP_ENTRIES = List.of("members", "strategy");

    private static final List<String> DECLARATIONS = List.of(
            "worker", "group", "multiblock", "event", "display", "fn", "on");

    private static final List<String> STRATEGIES = List.of(
            "round_robin", "first_available", "least_filled", "random", "priority");

    private static final List<String> BUILTINS = List.of(
            "storage", "crafting", "world", "network", "workers", "multiblocks");

    /**
     * Was an dieser Stelle passt.
     *
     * @param lines      alle Zeilen des Programms
     * @param lineIndex  Zeile, in der der Cursor steht
     * @param column     Spalte des Cursors
     */
    public static List<Entry> at(List<String> lines, int lineIndex, int column) {
        String line = lines.get(lineIndex);
        String upToCursor = line.substring(0, Math.min(column, line.length()));
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length()).trim();

        List<Entry> entries = new ArrayList<>();

        // „display halle " — der Name steht, als Nächstes kommt die Klammer.
        if (afterDeclarationName(upToCursor, prefix)) {
            return List.of();
        }

        // Nach from, to und overflow to: Geräte.
        if (before.endsWith("from") || before.endsWith("to")) {
            addConnectors(entries, prefix);
            addAll(entries, List.of("storage", "crafting"), prefix, Entry.Kind.BUILTIN);
            return limit(entries);
        }

        // Nach display: die Wände, die in der Welt stehen.
        //
        // "display NAME { … }" verlangt den Namen, den die Tafel trägt. Wer
        // ihn falsch schreibt, bekommt kein Programm, das nicht übersetzt,
        // sondern eine Wand, die schwarz bleibt — der Fehler, den man am
        // längsten sucht.
        if (before.endsWith("display")) {
            addDisplays(entries, prefix);
            return limit(entries);
        }

        // Nach filter: Gegenstände und Tags.
        if (before.endsWith("filter")) {
            addItems(entries, prefix);
            return limit(entries);
        }

        // Nach strategy: die Verteilungen.
        if (before.endsWith("strategy")) {
            addAll(entries, STRATEGIES, prefix, Entry.Kind.KEYWORD);
            return limit(entries);
        }

        // Ein angefangener Auswahlausdruck.
        if (prefix.contains(":")) {
            addItems(entries, prefix);
            return limit(entries);
        }
        return limit(structural(entries, lines, lineIndex, prefix));
    }

    /** Vorschläge, die von der Stelle im Aufbau abhängen. */
    private static List<Entry> structural(List<Entry> entries, List<String> lines,
                                          int lineIndex, String prefix) {
        // Auf oberster Ebene stehen Deklarationen.
        if (indentOf(lines.get(lineIndex)) == 0) {
            addAll(entries, DECLARATIONS, prefix, Entry.Kind.KEYWORD);
            return entries;
        }
        String block = enclosingBlock(lines, lineIndex);
        if (block != null) {
            switch (block) {
                case "worker" -> {
                    addAll(entries, WORKER_ENTRIES, prefix, Entry.Kind.KEYWORD);
                    return entries;
                }
                case "display" -> {
                    addAll(entries, DISPLAY_ENTRIES, prefix, Entry.Kind.KEYWORD);
                    return entries;
                }
                case "group" -> {
                    addAll(entries, GROUP_ENTRIES, prefix, Entry.Kind.KEYWORD);
                    return entries;
                }
                default -> { }
            }
        }
        // In einer Funktion: Connectoren, Eingebautes und Anweisungen.
        addConnectors(entries, prefix);
        addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
        addAll(entries, List.of("let", "if", "else", "for", "while", "move", "emit",
                "sleep", "await", "return"), prefix, Entry.Kind.KEYWORD);
        return entries;
    }

    /**
     * Welche Deklaration den Cursor umgibt, oder {@code null}.
     *
     * <p>Rückwärts bis zur ersten Zeile, die auf Spalte null mit einem
     * Deklarationswort anfängt — das reicht, weil Deklarationen nicht
     * ineinander stehen.
     *
     * <p>Vorher fragte diese Stelle nur „ist das ein Worker", und alles
     * andere fiel in denselben Topf: In einer Anzeige bekam man die
     * Anweisungen einer Funktion vorgeschlagen. Jede Blockart hat aber ihre
     * eigenen Angaben, und außerhalb davon sind sie falsch.
     */
    private static String enclosingBlock(List<String> lines, int lineIndex) {
        for (int i = lineIndex; i >= 0; i--) {
            String line = lines.get(i);
            if (indentOf(line) != 0) {
                continue;
            }
            String trimmed = line.trim();
            for (String declaration : DECLARATIONS) {
                if (trimmed.startsWith(declaration + " ")) {
                    return declaration;
                }
            }
        }
        return null;
    }

    /**
     * Steht in dieser Zeile schon eine Deklaration mit Namen?
     *
     * <p>Dann kommt als Nächstes die geschweifte Klammer, und dafür gibt es
     * nichts vorzuschlagen. Solange der Name noch getippt wird, gilt das
     * nicht — daran, dass hinter dem Schlüsselwort genau das angefangene Wort
     * steht, ist er zu erkennen.
     */
    private static boolean afterDeclarationName(String upToCursor, String prefix) {
        String trimmed = upToCursor.trim();
        for (String declaration : DECLARATIONS) {
            if (trimmed.startsWith(declaration + " ")) {
                return !trimmed.substring(declaration.length()).trim().equals(prefix);
            }
        }
        return false;
    }

    private static void addConnectors(List<Entry> entries, String prefix) {
        for (String connector : ClientNetworkState.connectors()) {
            if (matches(connector, prefix)) {
                entries.add(new Entry(connector, connector, Entry.Kind.CONNECTOR));
            }
        }
    }

    private static void addDisplays(List<Entry> entries, String prefix) {
        for (String display : ClientNetworkState.displays()) {
            if (matches(display, prefix)) {
                entries.add(new Entry(display, display, Entry.Kind.CONNECTOR));
            }
        }
    }

    /**
     * Gegenstände aus der Registry.
     *
     * <p>Erst ab drei Zeichen, und nur die ersten Treffer: Über zwanzigtausend
     * Einträge zu laufen, während jemand tippt, ist genau die Stelle, an der
     * ein Editor anfängt zu haken.
     */
    private static void addItems(List<Entry> entries, String prefix) {
        String search = prefix.startsWith("item:") ? prefix.substring(5)
                : prefix.startsWith("tag:") ? prefix.substring(4)
                : prefix;
        boolean asTag = prefix.startsWith("tag:");
        if (search.length() < 2 && !prefix.contains(":")) {
            entries.add(new Entry("item:", "item:", Entry.Kind.KEYWORD));
            entries.add(new Entry("tag:", "tag:", Entry.Kind.KEYWORD));
            entries.add(new Entry("fluid:", "fluid:", Entry.Kind.KEYWORD));
            return;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        int found = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (found >= MAX) {
                break;
            }
            String path = id.getPath();
            if (!path.contains(needle)) {
                continue;
            }
            String written = "minecraft".equals(id.getNamespace())
                    ? "item:" + path
                    : "item:" + id.getNamespace() + "/" + path;
            entries.add(new Entry(written, written,
                    asTag ? Entry.Kind.TAG : Entry.Kind.ITEM));
            found++;
        }
    }

    private static void addAll(List<Entry> entries, List<String> candidates, String prefix,
                               Entry.Kind kind) {
        for (String candidate : candidates) {
            if (matches(candidate, prefix)) {
                entries.add(new Entry(candidate, candidate, kind));
            }
        }
    }

    /**
     * Passt dieser Vorschlag zum angefangenen Wort?
     *
     * <p><b>Was schon vollständig dasteht, ist kein Vorschlag.</b> Wer
     * {@code halle} zu Ende getippt hat, bekam bis eben {@code halle}
     * angeboten — eine Liste mit einem Eintrag, der nichts ändert, und sie
     * verdeckt die Zeile darunter.
     */
    private static boolean matches(String candidate, String prefix) {
        if (candidate.equalsIgnoreCase(prefix)) {
            return false;
        }
        return prefix.isEmpty()
                || candidate.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static List<Entry> limit(List<Entry> entries) {
        return entries.size() <= MAX ? entries : entries.subList(0, MAX);
    }

    /** Das angefangene Wort links vom Cursor. */
    public static String currentWord(String upToCursor) {
        int start = upToCursor.length();
        while (start > 0) {
            char c = upToCursor.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/' || c == '*') {
                start--;
            } else {
                break;
            }
        }
        return upToCursor.substring(start);
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private Completions() {
    }
}
