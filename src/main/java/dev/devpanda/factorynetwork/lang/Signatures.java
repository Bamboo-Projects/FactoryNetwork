package dev.devpanda.factorynetwork.lang;

import java.util.List;
import java.util.Locale;

/**
 * Was hinter einem Schlüsselwort stehen muss.
 *
 * <p><b>Die Stelle, an der die Grammatik aufhört, ein Dokument zu sein.</b>
 * In {@code grammatik.md} steht {@code row STRING expr} — im Editor stand es
 * nirgends. Wer {@code row} getippt hatte, bekam wieder die Liste der
 * Schlüsselwörter angeboten und musste die Doku danebenlegen, um zu wissen,
 * dass jetzt ein Text kommt und danach ein Ausdruck.
 *
 * <p>Hier steht dieselbe Tabelle einmal als Daten. Zwei Dinge lesen sie: die
 * Vervollständigung, um an jeder Stelle das Passende anzubieten, und die
 * Hinweiszeile, die die ganze Form zeigt und markiert, wo man gerade steht.
 *
 * <p><b>Absichtlich im Sprachpaket und nicht im Editor.</b> Sie gehört zur
 * Sprache, nicht zu einem Fenster — der Editor im Spiel ist nur der erste,
 * der sie braucht. Ein Sprachserver für VS Code liest dieselbe Tabelle.
 */
public final class Signatures {

    /** Was für ein Wert an einer Stelle steht. */
    public enum Kind {
        /** Ein Text in Anführungszeichen. */
        STRING,
        /** Ein Ausdruck — Bestände, Rechnungen, Vergleiche. */
        EXPR,
        /** Eine ganze Zahl. */
        INT,
        /** Eine Dauer, etwa {@code 5s} oder {@code 2m}. */
        DURATION,
        /** Ein Connector, {@code storage} oder {@code crafting}. */
        TARGET,
        /** Ein Auswahlausdruck über Gegenstände oder Tags. */
        SELECTION,
        /** Der Name einer Verteilung. */
        STRATEGY,
        /** Der Name einer Funktion im Projekt. */
        FUNCTION,
        /** Eine Aufzählung von Connectoren oder Mustern. */
        MEMBERS,
        /** Ein Name, der hier erst entsteht — dafür gibt es keinen Vorschlag. */
        NEW_NAME,
        /** Der Name eines Ereignisses aus dem Projekt. */
        EVENT,
        /** Ein festes Wort, das genau so dastehen muss. */
        LITERAL
    }

    /**
     * Eine Stelle in einer Form.
     *
     * @param kind  was dort stehen muss
     * @param label wie es in der Hinweiszeile heißt
     */
    public record Slot(Kind kind, String label, boolean optional) {

        public Slot(Kind kind, String label) {
            this(kind, label, false);
        }

        static Slot of(Kind kind) {
            return new Slot(kind, kind.name().toLowerCase(Locale.ROOT));
        }

        static Slot named(Kind kind, String label) {
            return new Slot(kind, label);
        }

        static Slot literal(String word) {
            return new Slot(Kind.LITERAL, word);
        }

        /**
         * Ein festes Wort, das auch fehlen darf.
         *
         * <p>Es führt immer genau einen Wert ein — {@code from ziel},
         * {@code to ziel}. Fehlt das Wort, fehlt auch der Wert, und beim
         * Zählen werden beide Stellen übersprungen. Das ist die Regel, mit
         * der {@code move menge [from ziel] to ziel} ohne einen zweiten
         * Übersetzer auskommt.
         */
        static Slot maybe(String word) {
            return new Slot(Kind.LITERAL, word, true);
        }
    }

    /**
     * Ein Schlüsselwort mit allem, was dahinter gehört.
     *
     * @param keyword das Wort selbst
     * @param slots   die Stellen dahinter, in der Reihenfolge
     * @param help    ein Satz dazu, für die Erklärung beim Zeigen
     */
    public record Signature(String keyword, List<Slot> slots, String help) {

        /** Die ganze Form als Text: {@code row string expr}. */
        public String shape() {
            StringBuilder out = new StringBuilder(keyword);
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.optional()) {
                    // Das feste Wort und sein Wert stehen zusammen in eckigen
                    // Klammern — so schreibt es auch die Grammatik.
                    out.append(" [").append(slot.label());
                    if (i + 1 < slots.size()) {
                        out.append(' ').append(slots.get(i + 1).label());
                        i++;
                    }
                    out.append(']');
                    continue;
                }
                out.append(' ').append(slot.label());
            }
            return out.toString();
        }

        /** Was an dieser Stelle steht, oder {@code null} hinter dem Ende. */
        public Slot slotAt(int index) {
            return index >= 0 && index < slots.size() ? slots.get(index) : null;
        }
    }

    private static Signature of(String keyword, String help, Slot... slots) {
        return new Signature(keyword, List.of(slots), help);
    }

    /** Die Angaben in einer Anzeige. */
    public static final List<Signature> DISPLAY = List.of(
            of("title", "Die Überschrift der Wand.", Slot.of(Kind.STRING)),
            of("row", "Eine Zeile mit Beschriftung und Wert.",
                    Slot.of(Kind.STRING), Slot.of(Kind.EXPR)),
            of("text", "Eine Zeile ohne Beschriftung.", Slot.of(Kind.EXPR)),
            of("progress", "Ein Balken von null bis eins.",
                    Slot.of(Kind.STRING), Slot.of(Kind.EXPR)),
            of("indicator", "Eine Lampe: an, wenn der Ausdruck wahr ist.",
                    Slot.of(Kind.STRING), Slot.of(Kind.EXPR)),
            of("list", "Eine Aufzählung aus einem Ausdruck.",
                    Slot.of(Kind.STRING), Slot.of(Kind.EXPR)),
            of("button", "Ein Knopf, der eine Funktion aufruft.",
                    Slot.of(Kind.STRING), Slot.of(Kind.FUNCTION)));

    /** Die Angaben in einem Worker. */
    public static final List<Signature> WORKER = List.of(
            of("from", "Woher die Gegenstände kommen.", Slot.of(Kind.TARGET)),
            of("to", "Wohin sie gehen.", Slot.of(Kind.TARGET)),
            of("filter", "Was bewegt wird. Ohne Angabe alles.",
                    Slot.of(Kind.SELECTION)),
            of("maintain", "So viele sollen am Ziel liegen bleiben.",
                    Slot.of(Kind.INT)),
            of("rate", "Höchstens so viele in dieser Zeit.",
                    Slot.of(Kind.INT), Slot.literal("per"), Slot.of(Kind.DURATION)),
            of("when", "Nur, solange der Ausdruck wahr ist.", Slot.of(Kind.EXPR)),
            of("priority", "Wer bei knappem Nachschub zuerst drankommt.",
                    Slot.of(Kind.INT)),
            of("strategy", "Wie auf mehrere Ziele verteilt wird.",
                    Slot.of(Kind.STRATEGY)),
            of("overflow", "Wohin, wenn das Ziel voll ist.",
                    Slot.literal("to"), Slot.of(Kind.TARGET)));

    /** Die Angaben in einer Gruppe. */
    public static final List<Signature> GROUP = List.of(
            of("members", "Die Connectoren der Gruppe, mit Komma getrennt.",
                    Slot.of(Kind.MEMBERS)),
            of("strategy", "Wie auf sie verteilt wird.", Slot.of(Kind.STRATEGY)));

    /**
     * Die Anweisungen in einer Funktion oder einem Ereignisblock.
     *
     * <p>Bedingungen stehen ohne runde Klammern und ein Block ist Pflicht —
     * deshalb endet die Form von {@code if} beim Ausdruck. Die geschweifte
     * Klammer setzt der Editor beim Zeilenumbruch selbst.
     */
    public static final List<Signature> STATEMENT = List.of(
            of("let", "Ein neuer Wert mit Namen.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("="),
                    Slot.of(Kind.EXPR)),
            of("if", "Nur, wenn der Ausdruck wahr ist.", Slot.of(Kind.EXPR)),
            of("while", "Solange der Ausdruck wahr ist.", Slot.of(Kind.EXPR)),
            of("for", "Einmal je Element.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("in"),
                    Slot.of(Kind.EXPR)),
            of("move", "Bewegt Gegenstände von einem Gerät zum anderen.",
                    Slot.named(Kind.SELECTION, "menge"), Slot.maybe("from"),
                    Slot.named(Kind.TARGET, "quelle"), Slot.literal("to"),
                    Slot.named(Kind.TARGET, "ziel")),
            of("emit", "Löst ein Ereignis aus.", Slot.of(Kind.EVENT)),
            of("sleep", "Wartet eine Zeit lang.", Slot.of(Kind.DURATION)),
            of("return", "Gibt einen Wert zurück und beendet die Funktion.",
                    Slot.of(Kind.EXPR)),
            of("await", "Wartet auf ein Ereignis.", Slot.of(Kind.EVENT)));

    /** Die Verteilungen, die {@code strategy} kennt. */
    public static final List<String> STRATEGIES = List.of(
            "round_robin", "first_available", "least_filled", "random", "priority");

    /**
     * Was auf oberster Ebene eine Form hat.
     *
     * <p>Bisher gab es hier nichts: Alle Deklarationen öffnen einen Block, und
     * die Frage „was kommt hinter dem Wort" stellte sich erst darin.
     * {@code global} ist die erste, die in einer Zeile fertig wird — und
     * damit die erste, für die eine Formzeile auf oberster Ebene einen Sinn
     * ergibt.
     */
    public static final List<Signature> TOP_LEVEL = List.of(
            of("global", "Ein Wert, den alle Dateien sehen.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("="),
                    Slot.of(Kind.EXPR)));

    /** Ein Ding, das an einem Gerät steht. */
    public record Member(String name, String shape, String help) {
    }

    /**
     * Was an einem Gerät steht — {@code crusher_1.online}.
     *
     * <p><b>Vier und nicht mehr.</b> Der Interpreter kennt {@code online},
     * {@code name}, {@code redstone()} und {@code count()};
     * {@code sprache.md} §6 beschreibt darüber hinaus {@code insert()},
     * {@code items()} und {@code busy}, die es noch nicht gibt. Was hier
     * steht, muss laufen — ein Vorschlag, der in einen Laufzeitfehler führt,
     * ist schlimmer als gar keiner.
     *
     * <p>Für jedes Gerät dieselben: Gerätespezifisches gibt es nach dem Punkt
     * erst, wenn die Mitglieder aus §6 gebaut sind. Dann ist es ein Eintrag
     * hier und nichts weiter.
     */
    public static final List<Member> MEMBERS = List.of(
            new Member("online", "bool",
                    "Ob das Gerät gerade im Netz hängt."),
            new Member("name", "string",
                    "Der Name, den die Beschriftungspistole vergeben hat."),
            new Member("redstone", "redstone() int, redstone(int)",
                    "Die Redstone-Stärke, 0 bis 15. Mit Zahl gesetzt, ohne gelesen."),
            new Member("count", "count(selection) int",
                    "Wie viel von einer Art im Netzspeicher liegt."),
            new Member("insert", "insert(selection) int",
                    "Legt aus dem Speicher etwas hinein. Gibt zurück, wie viel ankam — "
                            + "weniger ist normal."),
            new Member("items", "items() list",
                    "Was gerade im Gerät liegt. Leere Fächer fallen weg."));

    /**
     * Was an einer Liste steht.
     *
     * <p>Alle fünf, die {@code sprache.md} §12 nennt. {@code where} und
     * {@code sort} werten ihren Ausdruck je Eintrag aus, mit {@code it} als
     * diesem Eintrag.
     */
    public static final List<Member> LIST_MEMBERS = List.of(
            new Member("count", "count() int", "Wie viele Einträge."),
            new Member("first", "first() any", "Der erste Eintrag, oder nichts."),
            new Member("sum", "sum() int", "Alle Zahlen aufaddiert."),
            new Member("where", "where(expr) list",
                    "Behält, wofür der Ausdruck wahr ist. it ist der Eintrag."),
            new Member("sort", "sort(expr) list",
                    "Ordnet nach dem Ausdruck. it ist der Eintrag."));

    /**
     * Die Formen, die in dieser Blockart gelten.
     *
     * @param declaration das Deklarationswort, etwa {@code display}
     */
    public static List<Signature> forBlock(String declaration) {
        if (declaration == null) {
            // Kein Block heißt oberste Ebene, und dort steht seit global
            // etwas mit Form.
            return TOP_LEVEL;
        }
        return switch (declaration) {
            case "display" -> DISPLAY;
            case "worker" -> WORKER;
            case "group" -> GROUP;
            // fn, on und multiblock enthalten Anweisungen, keine Angaben.
            case "fn", "on", "multiblock" -> STATEMENT;
            default -> List.of();
        };
    }

    /** Die Form zu einem Schlüsselwort in dieser Blockart, oder {@code null}. */
    public static Signature find(String declaration, String keyword) {
        for (Signature signature : forBlock(declaration)) {
            if (signature.keyword().equals(keyword)) {
                return signature;
            }
        }
        return null;
    }

    /**
     * Wo der Cursor in einer Angabe steht.
     *
     * @param signature die Form, die auf dieser Zeile begonnen wurde
     * @param slotIndex die Stelle, die gerade dran ist; hinter der letzten
     *                  steht die Zahl der Stellen, dann ist die Angabe voll
     */
    public record Where(Signature signature, int slotIndex) {

        /** Was hier stehen muss, oder {@code null}, wenn die Angabe voll ist. */
        public Slot slot() {
            return signature.slotAt(slotIndex);
        }

        public boolean isComplete() {
            return slot() == null;
        }
    }

    /**
     * Liest aus der angefangenen Zeile, welche Angabe gemeint ist und welche
     * Stelle davon dran ist.
     *
     * <p>Gezählt wird über die Wörter dahinter, wobei ein Text in
     * Anführungszeichen <b>eines</b> ist — sonst rutschte {@code row "Freier
     * Platz"} um ein Wort weiter, und die Hinweiszeile zeigte auf die falsche
     * Stelle.
     *
     * <p>Endet die Zeile mit einem Leerzeichen, wird die nächste Stelle
     * angefangen; sonst wird die letzte noch getippt. Das ist der Unterschied
     * zwischen „{@code row "a" }" — jetzt kommt der Ausdruck — und
     * „{@code row "a}" — der Text ist noch nicht fertig.
     *
     * @param declaration    das Deklarationswort des umgebenden Blocks
     * @param lineUpToCursor die Zeile bis zum Cursor
     * @return wo man steht, oder {@code null} außerhalb jeder bekannten Angabe
     */
    public static Where at(String declaration, String lineUpToCursor) {
        String text = lineUpToCursor.stripLeading();
        if (text.isEmpty()) {
            return null;
        }
        int wordEnd = text.indexOf(' ');
        if (wordEnd < 0) {
            // Das Schlüsselwort selbst wird noch getippt.
            return null;
        }
        Signature signature = find(declaration, text.substring(0, wordEnd));
        if (signature == null) {
            return null;
        }
        String rest = text.substring(wordEnd);
        List<String> words = splitWords(rest);
        boolean typing = !rest.isEmpty() && rest.charAt(rest.length() - 1) != ' ';
        if (typing && !words.isEmpty()) {
            // Das letzte Wort wird noch getippt und zählt noch nicht.
            words = words.subList(0, words.size() - 1);
        }
        return new Where(signature, slotAfter(signature, words));
    }

    /**
     * Welche Stelle nach diesen Wörtern dran ist.
     *
     * <p>Wort für Wort durch die Form. Steht dabei eine Stelle an, die ein
     * festes Wort <b>sein kann</b>, und das Wort passt nicht dazu, fällt sie
     * samt ihrem Wert weg: {@code move 64 to kiste} hat kein {@code from},
     * und ohne diese Regel landete das {@code to} auf der Stelle der Quelle.
     */
    private static int slotAfter(Signature signature, List<String> words) {
        int slot = 0;
        for (String word : words) {
            while (slot < signature.slots().size()) {
                Slot candidate = signature.slots().get(slot);
                if (candidate.optional() && !candidate.label().equals(word)) {
                    slot += 2;
                    continue;
                }
                break;
            }
            slot++;
        }
        return slot;
    }

    /** Die Wörter hinter dem Schlüsselwort; ein Text in Anführungszeichen ist eines. */
    private static List<String> splitWords(String rest) {
        List<String> words = new java.util.ArrayList<>();
        int i = 0;
        while (i < rest.length()) {
            while (i < rest.length() && rest.charAt(i) == ' ') {
                i++;
            }
            if (i >= rest.length()) {
                break;
            }
            int start = i;
            if (rest.charAt(i) == '"') {
                i++;
                while (i < rest.length() && rest.charAt(i) != '"') {
                    i++;
                }
                i = Math.min(i + 1, rest.length());
            } else {
                while (i < rest.length() && rest.charAt(i) != ' ') {
                    i++;
                }
            }
            words.add(rest.substring(start, i));
        }
        return words;
    }

    private Signatures() {
    }
}
