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

        // Nach from, to und overflow to: Geräte.
        if (before.endsWith("from") || before.endsWith("to")) {
            addConnectors(entries, prefix);
            addAll(entries, List.of("storage", "crafting"), prefix, Entry.Kind.BUILTIN);
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
        if (insideWorker(lines, lineIndex)) {
            addAll(entries, WORKER_ENTRIES, prefix, Entry.Kind.KEYWORD);
            return entries;
        }
        // Auf oberster Ebene stehen Deklarationen.
        if (indentOf(lines.get(lineIndex)) == 0) {
            addAll(entries, DECLARATIONS, prefix, Entry.Kind.KEYWORD);
            return entries;
        }
        // In einer Funktion: Connectoren, Eingebautes und Anweisungen.
        addConnectors(entries, prefix);
        addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
        addAll(entries, List.of("let", "if", "else", "for", "while", "move", "emit",
                "sleep", "await", "return"), prefix, Entry.Kind.KEYWORD);
        return entries;
    }

    /**
     * Steht der Cursor in einem Worker-Block? Gesucht wird rückwärts nach der
     * öffnenden Zeile — das reicht, weil Deklarationen nicht verschachtelt
     * sind.
     */
    private static boolean insideWorker(List<String> lines, int lineIndex) {
        for (int i = lineIndex; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("worker ")) {
                return true;
            }
            if (line.startsWith("fn ") || line.startsWith("on ")
                    || line.startsWith("group ") || line.startsWith("display ")
                    || line.startsWith("multiblock ")) {
                return false;
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

    private static boolean matches(String candidate, String prefix) {
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
