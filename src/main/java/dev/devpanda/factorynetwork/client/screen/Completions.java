package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.lang.Signatures;
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
 *
 * <p><b>Was wo stehen darf, steht nicht hier, sondern in
 * {@link Signatures}.</b> Diese Klasse entscheidet nur, welche Stelle gerade
 * dran ist, und holt sich von dort, was dazu passt. Die Trennung ist der
 * Grund, warum dieselbe Auskunft auch die Hinweiszeile im Editor speist —
 * und einmal ein Sprachserver für VS Code.
 */
public final class Completions {

    /** Mehr als das wird nicht angezeigt; alles andere ist Scrollen ohne Nutzen. */
    private static final int MAX = 8;

    /**
     * Ein Vorschlag.
     *
     * @param text   was in der Liste steht und womit verglichen wird
     * @param insert was beim Übernehmen eingesetzt wird
     * @param kind   die Farbe
     * @param detail was dahinter gehört — {@code string expr} hinter
     *               {@code row}. <b>Das ist die Antwort auf „was muss ich wo
     *               angeben".</b> Ohne sie ist ein Vorschlag ein Wort ohne
     *               Form, und man legt die Doku daneben.
     */
    public record Entry(String text, String insert, Kind kind, String detail) {

        public Entry(String text, String insert, Kind kind) {
            this(text, insert, kind, "");
        }

        public enum Kind { KEYWORD, CONNECTOR, ITEM, TAG, BUILTIN }
    }

    private static final List<String> DECLARATIONS = List.of(
            "worker", "group", "multiblock", "event", "display", "fn", "on", "global");

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

        // Nach einem Punkt hinter einem Gerätenamen: was ein Gerät hat.
        //
        // Vor der Prüfung auf die Stelle in der Angabe, weil „to crusher_1."
        // sonst als angefangener Zielname gelesen würde — und dann stünden
        // dort wieder die Connectoren.
        //
        // <b>Und der Punkt beendet die Liste in jedem Fall.</b> Steht davor
        // kein bekannter Connector, gibt es eben nichts: Vorher fiel dieser
        // Fall bis zur Ausdrucksstelle durch und bot „storage, crafting,
        // world …" an — hinter einem Punkt ergibt keines davon einen Satz.
        if (afterDot(upToCursor)) {
            if (memberPrefix(upToCursor) == null) {
                return List.of();
            }
            for (Signatures.Member candidate : Signatures.MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(new Entry(candidate.name(), candidate.name(),
                            Entry.Kind.BUILTIN, candidate.shape()));
                }
            }
            return limit(entries);
        }

        // Nach display: die Wände, die in der Welt stehen.
        //
        // "display NAME { … }" verlangt den Namen, den die Tafel trägt. Wer
        // ihn falsch schreibt, bekommt kein Programm, das nicht übersetzt,
        // sondern eine Wand, die schwarz bleibt — der Fehler, den man am
        // längsten sucht.
        if (before.endsWith("display") && indentOf(line) == 0) {
            addDisplays(entries, prefix);
            return limit(entries);
        }

        // <b>Der Kern.</b> Steht der Cursor hinter einem Schlüsselwort mit
        // Form, richtet sich der Vorschlag nach der Stelle, die gerade dran
        // ist — und nicht mehr nach dem Block. Vorher bot der Editor hinter
        // „row" wieder „title, row, text …" an, also genau das, was dort
        // nicht hingehört.
        Signatures.Where where = whereAt(lines, lineIndex, column);
        if (where != null) {
            return limit(forSlot(where, entries, prefix, lines));
        }

        // Ein angefangener Auswahlausdruck.
        if (prefix.contains(":")) {
            addItems(entries, prefix);
            return limit(entries);
        }
        return limit(structural(entries, lines, lineIndex, prefix));
    }

    /**
     * Wo der Cursor in einer Angabe steht, oder {@code null}.
     *
     * <p>Öffentlich, weil der Editor dasselbe wissen muss: Er zeichnet daraus
     * die Hinweiszeile mit der ganzen Form und der markierten Stelle.
     */
    public static Signatures.Where whereAt(List<String> lines, int lineIndex, int column) {
        String line = lines.get(lineIndex);
        String upToCursor = line.substring(0, Math.min(column, line.length()));
        return Signatures.at(enclosingBlock(lines, lineIndex), upToCursor);
    }

    /** Was an dieser Stelle einer Angabe stehen darf. */
    private static List<Entry> forSlot(Signatures.Where where, List<Entry> entries,
                                       String prefix, List<String> lines) {
        Signatures.Slot slot = where.slot();
        if (slot == null) {
            // Die Angabe ist voll. Nichts vorzuschlagen ist hier die Auskunft:
            // Die Zeile ist fertig.
            return entries;
        }
        switch (slot.kind()) {
            case TARGET -> {
                addConnectors(entries, prefix);
                addAll(entries, List.of("storage", "crafting"), prefix, Entry.Kind.BUILTIN);
            }
            case EXPR -> {
                // Ein angefangener Auswahlausdruck ist auch ein Ausdruck.
                // Ohne diesen Zweig fiele „let x = minecraft:" durch: Die
                // Prüfung auf den Doppelpunkt steht weiter unten und wird
                // hier gar nicht mehr erreicht.
                if (prefix.contains(":")) {
                    addItems(entries, prefix);
                    return entries;
                }
                addConnectors(entries, prefix);
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
            }
            case SELECTION -> addItems(entries, prefix);
            case STRATEGY -> addAll(entries, Signatures.STRATEGIES, prefix,
                    Entry.Kind.KEYWORD);
            case MEMBERS -> addConnectors(entries, prefix);
            case FUNCTION -> addFunctions(entries, prefix, lines);
            case EVENT -> addEvents(entries, prefix, lines);
            case LITERAL -> {
                addAll(entries, List.of(slot.label()), prefix, Entry.Kind.KEYWORD);
                // Darf diese Stelle wegfallen, ist auch das Wort dahinter
                // erlaubt: Nach „move 64 " kommt „from" oder gleich „to".
                if (slot.optional()) {
                    Signatures.Slot next =
                            where.signature().slotAt(where.slotIndex() + 2);
                    if (next != null && next.kind() == Signatures.Kind.LITERAL) {
                        addAll(entries, List.of(next.label()), prefix,
                                Entry.Kind.KEYWORD);
                    }
                }
            }
            // Ein Name, der hier erst entsteht, lässt sich nicht vorschlagen.
            case NEW_NAME -> { }
            // Für einen Text, eine Zahl oder eine Dauer gibt es nichts
            // vorzuschlagen. Die Hinweiszeile sagt trotzdem, was hier steht —
            // dafür ist sie da.
            default -> { }
        }
        return entries;
    }

    /** Die Ereignisse des Projekts — für {@code emit}. */
    private static void addEvents(List<Entry> entries, String prefix, List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("event ")) {
                continue;
            }
            int open = trimmed.indexOf('(');
            String name = (open < 0 ? trimmed.substring(6) : trimmed.substring(6, open)).trim();
            if (!name.isEmpty() && matches(name, prefix)) {
                entries.add(new Entry(name, name, Entry.Kind.KEYWORD, "event"));
            }
        }
    }

    /** Die Funktionen des Projekts — für den Knopf einer Anzeige. */
    private static void addFunctions(List<Entry> entries, String prefix, List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("fn ")) {
                continue;
            }
            int open = trimmed.indexOf('(');
            String name = (open < 0 ? trimmed.substring(3) : trimmed.substring(3, open)).trim();
            if (!name.isEmpty() && matches(name, prefix)) {
                entries.add(new Entry(name, name, Entry.Kind.KEYWORD, "fn"));
            }
        }
    }

    /** Vorschläge, die von der Stelle im Aufbau abhängen. */
    private static List<Entry> structural(List<Entry> entries, List<String> lines,
                                          int lineIndex, String prefix) {
        // Auf oberster Ebene stehen Deklarationen. Die meisten öffnen einen
        // Block und haben keine Form; global ist die Ausnahme und bringt
        // seine mit.
        if (indentOf(lines.get(lineIndex)) == 0) {
            for (String word : DECLARATIONS) {
                if (!matches(word, prefix)) {
                    continue;
                }
                Signatures.Signature shape = Signatures.find(null, word);
                String detail = shape == null ? ""
                        : shape.shape().substring(word.length()).trim();
                entries.add(new Entry(word, word, Entry.Kind.KEYWORD, detail));
            }
            return entries;
        }
        // In einem Block: seine Angaben oder seine Anweisungen, jede mit
        // ihrer Form.
        String block = enclosingBlock(lines, lineIndex);
        List<Signatures.Signature> shapes = Signatures.forBlock(block);
        if (!shapes.isEmpty()) {
            for (Signatures.Signature signature : shapes) {
                if (matches(signature.keyword(), prefix)) {
                    String detail = signature.shape()
                            .substring(signature.keyword().length()).trim();
                    entries.add(new Entry(signature.keyword(), signature.keyword(),
                            Entry.Kind.KEYWORD, detail));
                }
            }
            if (isCodeBlock(block)) {
                // In einer Funktion ist auch ein Ausdruck eine Anweisung —
                // also gehören die Bestände und die Connectoren dazu. Und die
                // drei Wörter ohne eigene Form.
                addAll(entries, List.of("else", "break", "continue"), prefix,
                        Entry.Kind.KEYWORD);
                addConnectors(entries, prefix);
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
            }
            return entries;
        }
        // Außerhalb jedes bekannten Blocks: Connectoren und Eingebautes.
        addConnectors(entries, prefix);
        addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
        return entries;
    }

    /** Enthält dieser Block Anweisungen statt fester Angaben? */
    private static boolean isCodeBlock(String declaration) {
        return "fn".equals(declaration) || "on".equals(declaration)
                || "multiblock".equals(declaration);
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
    static String enclosingBlock(List<String> lines, int lineIndex) {
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

    /**
     * Steht der Cursor hinter einem Punkt, der auf einen Namen folgt?
     *
     * <p>{@code 3.5} zählt nicht: Davor steht eine Zahl und kein Name, und
     * ein Punktzugriff ist es damit nicht.
     */
    private static boolean afterDot(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        if (!before.endsWith(".")) {
            return false;
        }
        String name = currentWord(before.substring(0, before.length() - 1));
        return !name.isEmpty() && !Character.isDigit(name.charAt(0));
    }

    /**
     * Der Gerätename vor dem Punkt, oder {@code null}.
     *
     * <p>Nur, wenn davor wirklich ein Connector steht: {@code storage.} ist
     * etwas anderes, und was niemand so genannt hat, hat auch keine
     * Mitglieder.
     */
    private static String memberPrefix(String upToCursor) {
        if (!afterDot(upToCursor)) {
            return null;
        }
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        String name = currentWord(before.substring(0, before.length() - 1));
        return ClientNetworkState.connectors().contains(name) ? name : null;
    }

    /**
     * Was ein Gerät kann, in einer Zeile.
     *
     * <p>Über alle Seiten zusammengefasst und nicht je Seite: In der
     * Vorschlagsliste ist Platz für ein paar Wörter, und die Frage dort
     * lautet „taugt das überhaupt". Welche Seite es genau ist, sagt das
     * Zeigen.
     */
    public static String abilities(DeviceProfile profile) {
        if (!profile.reachable()) {
            return "";
        }
        List<String> can = new ArrayList<>();
        for (Side side : Side.values()) {
            if (profile.hasItems(side) && !can.contains("Gegenstände")) {
                can.add("Gegenstände");
            }
            if (profile.hasFluids(side) && !can.contains("Flüssigkeiten")) {
                can.add("Flüssigkeiten");
            }
            if (profile.hasEnergy(side) && !can.contains("Strom")) {
                can.add("Strom");
            }
        }
        return can.isEmpty() ? "nichts anzuschließen" : String.join(", ", can);
    }

    /**
     * Die Connectoren, jeder mit der Maschine dahinter.
     *
     * <p>Das {@code detail} stand hier lange leer. Es ist die billigste
     * Stelle mit dem größten Nutzen: Sie steht in jeder Vorschlagsliste, ohne
     * dass jemand etwas dafür tun muss.
     */
    private static void addConnectors(List<Entry> entries, String prefix) {
        for (String connector : ClientNetworkState.connectors()) {
            if (!matches(connector, prefix)) {
                continue;
            }
            DeviceProfile profile = ClientNetworkState.profile(connector);
            String detail = profile.reachable()
                    ? net.minecraft.network.chat.Component
                            .translatable(profile.descriptionId()).getString()
                            + " · " + abilities(profile)
                    : "";
            entries.add(new Entry(connector, connector, Entry.Kind.CONNECTOR, detail));
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
