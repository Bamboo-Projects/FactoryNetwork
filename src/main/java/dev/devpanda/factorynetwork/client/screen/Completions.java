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
     * Eine vollständige Liste wird nicht gekürzt.
     *
     * <p>Das Limit gilt den Namen aus der Welt: zwanzigtausend Gegenstände,
     * dreihundert Geräte — dort ist Abschneiden richtig. Was die Sprache
     * selbst hergibt, ist abzählbar und muss ganz dastehen. Aufgefallen ist
     * es beim neunten Gerätemitglied: {@code click} fiel hinten heraus, und
     * der Editor verschwieg eine Fähigkeit, statt zu scrollen.
     */
    private static List<Entry> whole(List<Entry> entries) {
        return entries;
    }

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

    /** Dieselbe Liste, die auch VS Code bekommt — sie steht in Signatures. */
    private static final List<String> DECLARATIONS = Signatures.DECLARATIONS;

    /**
     * Die eingebauten Namen, die der Interpreter <b>wirklich</b> kennt.
     *
     * <p>Die Sprache parst auch {@code world}, {@code network},
     * {@code workers} und {@code multiblocks} — auswerten kann sie keines
     * davon. Sie anzubieten hieß: Wer {@code to network} schreibt, bekommt
     * „Als Ziel taugt nur ein Name", eine Meldung, die dem Vorschlag
     * widerspricht, der sie ausgelöst hat.
     *
     * <p>Sie kommen zurück, sobald sie etwas tun. {@code crafting} ist diesen
     * Weg gegangen und steht in {@link #SOURCES} — als Quelle, denn ein Ziel
     * ist es nicht.
     */
    private static final List<String> BUILTINS = List.of("storage");

    /** Was nur als Quelle taugt — {@code from crafting} bestellt, was fehlt. */
    private static final List<String> SOURCES = List.of("crafting");

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
        // Hinter „…items()." steht eine Liste und kein Gerät. Vor afterDot
        // geprüft, weil dort ein Name vor dem Punkt erwartet wird — hier
        // steht eine schließende Klammer.
        //
        // Der Editor kennt keine Typen, aber diesen einen Fall erkennt er am
        // Text, und er ist der einzige, der heute vorkommt.
        if (afterListCall(upToCursor)) {
            for (Signatures.Member candidate : Signatures.LIST_MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(new Entry(candidate.name(), candidate.name(),
                            Entry.Kind.BUILTIN, candidate.shape()));
                }
            }
            return whole(entries);
        }
        if (afterDot(upToCursor)) {
            // <b>it ist kein Gerät.</b> Es steht für einen Posten, und daran
            // stehen andere Dinge. Vorher fiel es durch memberPrefix, das
            // nur bekannte Connectoren durchlässt — nach „it." kam damit
            // gar nichts.
            String receiver = wordBeforeDot(upToCursor);
            // Das Netz ist kein Gerät: An ihm stehen power und capacity, und
            // sonst nichts. Vor der Prüfung auf einen Connector, denn
            // „network" ist keiner und fiele sonst auf die leere Liste.
            if ("network".equals(receiver)) {
                for (Signatures.Member candidate : Signatures.NETWORK_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(new Entry(candidate.name(), candidate.name(),
                                Entry.Kind.BUILTIN, candidate.shape()));
                    }
                }
                return whole(entries);
            }
            // Eine Gruppe ist kein Gerät: An ihr stehen members und send, und
            // sonst nichts.
            if (isGroupName(receiver, lines)) {
                for (Signatures.Member candidate : Signatures.GROUP_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(new Entry(candidate.name(), candidate.name(),
                                Entry.Kind.BUILTIN, candidate.shape()));
                    }
                }
                return whole(entries);
            }
            if ("it".equals(wordBeforeDot(upToCursor))) {
                for (Signatures.Member candidate : Signatures.ENTRY_MEMBERS) {
                    if (matches(candidate.name(), prefix)) {
                        entries.add(new Entry(candidate.name(), candidate.name(),
                                Entry.Kind.BUILTIN, candidate.shape()));
                    }
                }
                return whole(entries);
            }
            if (memberPrefix(upToCursor) == null) {
                return List.of();
            }
            for (Signatures.Member candidate : Signatures.MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(new Entry(candidate.name(), candidate.name(),
                            Entry.Kind.BUILTIN, candidate.shape()));
                }
            }
            return whole(entries);
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

        // Nach on: die Ereignisse, auf die sich hören lässt.
        //
        // Die vier des Netzes stehen in keiner Datei — wer sie nicht
        // auswendig weiß, sucht sie in der Doku. Und ein vertippter Name ist
        // hier besonders teuer: Ein on braucht keine Deklaration, der Block
        // wird übernommen und läuft nie. Der Editor kann diesen Fehler
        // verhindern, statt ihn hinterher zu melden.
        if (before.endsWith("on") && indentOf(line) == 0) {
            addEvents(entries, prefix, lines);
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
                addAll(entries, BUILTINS, prefix, Entry.Kind.BUILTIN);
                // crafting ist eine Quelle und kein Ziel: Gefertigt wird in
                // den Speicher, und von dort holt es ein zweiter Worker ab.
                // Es bei „to" vorzuschlagen führte in eine Meldung, die dem
                // Vorschlag widerspricht, der sie ausgelöst hat.
                if ("from".equals(where.signature().keyword())) {
                    addAll(entries, SOURCES, prefix, Entry.Kind.BUILTIN);
                }
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
            case SELECTION -> {
                // Die Vorlagen zuerst: Wer eine angelegt hat, meint an dieser
                // Stelle meistens sie und nicht den einzelnen Gegenstand.
                addTemplates(entries, prefix, lines);
                addItems(entries, prefix);
                // Die beiden ohne Doppelpunkt tauchen in der
                // Gegenstandssuche nicht auf — sie müssen eigens angeboten
                // werden, sonst findet sie niemand.
                addAll(entries, List.of("power", "all"), prefix, Entry.Kind.KEYWORD);
            }
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
        // Die vier des Netzes zuerst: Sie stehen in keiner Datei und wären
        // sonst nirgends zu finden — man muss sie auswendig wissen oder in
        // der Doku nachsehen.
        addAll(entries,
                List.copyOf(new java.util.TreeSet<>(
                        dev.devpanda.factorynetwork.lang.BuiltinEvents.ARITY.keySet())),
                prefix, Entry.Kind.KEYWORD);
        addDeclaredNames(entries, prefix, lines, "event ", "event");
    }

    /** Die Funktionen des Projekts — für den Knopf einer Anzeige. */
    private static void addFunctions(List<Entry> entries, String prefix, List<String> lines) {
        addDeclaredNames(entries, prefix, lines, "fn ", "fn");
    }

    /** Heißt so eine Gruppe im Projekt? */
    private static boolean isGroupName(String word, List<String> lines) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        List<Entry> groups = new ArrayList<>();
        addDeclaredNames(groups, "", lines, "group ", "group");
        return groups.stream().anyMatch(entry -> entry.text().equals(word));
    }

    /** Die Filter-Vorlagen des Projekts. */
    private static void addTemplates(List<Entry> entries, String prefix, List<String> lines) {
        addDeclaredNames(entries, prefix, lines, "filter ", "filter");
    }

    /**
     * Namen, die das Projekt vergibt — aus <b>allen</b> Dateien.
     *
     * <p>Alle Dateien teilen einen Namensraum: Ein {@code fn} in
     * {@code werte.mf} wird in {@code main.mf} aufgerufen, ohne dass irgendwo
     * ein Import steht. Wer nur die offene Datei liest, schlägt genau die
     * Namen nicht vor, für die man den Editor am ehesten braucht — die aus
     * einer Datei, die man gerade <b>nicht</b> vor sich hat.
     *
     * <p><b>Die offene Datei kommt trotzdem aus dem Text</b> und nicht aus
     * dem Entwurf auf dem Client: Sie enthält, was gerade getippt wird, und
     * eine Funktion soll aufrufbar sein, sobald ihr Name dasteht — nicht erst
     * nach dem Speichern.
     *
     * <p>Über den Text und nicht über den Baum, wie bei
     * {@code Definitions.references}: Unfertiger Code hat keinen Baum, und
     * gerade dort soll die Vervollständigung helfen.
     */
    private static void addDeclaredNames(List<Entry> entries, String prefix,
                                         List<String> lines, String keyword, String detail) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        collectNames(seen, lines, keyword);

        dev.devpanda.factorynetwork.lang.Project project =
                dev.devpanda.factorynetwork.client.ClientProjectState.draft();
        for (String file : project.names()) {
            collectNames(seen, List.of(project.source(file).split("\n", -1)), keyword);
        }

        for (String name : seen) {
            if (matches(name, prefix)) {
                entries.add(new Entry(name, name, Entry.Kind.KEYWORD, detail));
            }
        }
    }

    /** Die erste der beiden Klammern, oder -1. */
    private static int firstOf(String text, char one, char other) {
        int first = text.indexOf(one);
        int second = text.indexOf(other);
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static void collectNames(java.util.Set<String> into, List<String> lines,
                                     String keyword) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(keyword)) {
                continue;
            }
            // Der Name endet an der Klammer — bei einer Funktion an der
            // runden, bei einer Vorlage an der geschweiften. Ohne die zweite
            // hieß die Vorlage „erze {".
            int open = firstOf(trimmed, '(', '{');
            String name = (open < 0 ? trimmed.substring(keyword.length())
                    : trimmed.substring(keyword.length(), open)).trim();
            // Ein „fn" in einem Multiblock zählt mit: Es ist eine Erklärung
            // wie jede andere, nur eingerückt.
            if (!name.isEmpty()) {
                into.add(name);
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
            // In einer Vorlage ist jede Zeile eine Auswahl. Ohne das stünde
            // dort nur „except" zur Wahl — das Wort für die Ausnahme, aber
            // nichts für den Regelfall.
            if ("filter".equals(block)) {
                addItems(entries, prefix);
                return entries;
            }
            if (isCodeBlock(block)) {
                // In einer Funktion ist auch ein Ausdruck eine Anweisung —
                // also gehören die Bestände und die Connectoren dazu. Und die
                // drei Wörter ohne eigene Form.
                addAll(entries, List.of("else", "break", "continue"), prefix,
                        Entry.Kind.KEYWORD);
                // Die Funktionen ohne Empfänger: log und die drei Stufen
                // daneben. Sie standen nirgends und wurden deshalb nie
                // vorgeschlagen — man musste wissen, dass es sie gibt.
                for (Signatures.Member function : Signatures.FREE_FUNCTIONS) {
                    if (matches(function.name(), prefix)) {
                        entries.add(new Entry(function.name(), function.name(),
                                Entry.Kind.BUILTIN, function.shape()));
                    }
                }
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
     * Steht vor dem Punkt ein Aufruf, der eine Liste liefert?
     *
     * <p>Heute gibt es genau einen: {@code items()}. Das über den Text zu
     * erkennen ist grob, aber ehrlicher als die Alternative — der Editor
     * kennt keine Typen, und einen halben Typprüfer für die
     * Vervollständigung zu bauen wäre die falsche Antwort auf eine Frage,
     * die sich mit sechs Zeichen beantworten lässt.
     */
    private static boolean afterListCall(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        return before.endsWith("items().");
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
        String name = wordBeforeDot(upToCursor);
        return ClientNetworkState.connectors().contains(name) ? name : null;
    }

    /** Das Wort vor dem Punkt, ohne zu prüfen, ob es etwas bedeutet. */
    private static String wordBeforeDot(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        return currentWord(before.substring(0, before.length() - 1));
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
        return profile.abilities();
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
            // Ein Tag über Flüssigkeiten hat eine eigene Art, weil aus der
            // Zeile hervorgehen muss, ob Gegenstände oder Flüssigkeiten
            // gemeint sind.
            entries.add(new Entry("fluidtag:", "fluidtag:", Entry.Kind.KEYWORD));
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
