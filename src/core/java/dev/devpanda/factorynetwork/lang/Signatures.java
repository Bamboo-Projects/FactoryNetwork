package dev.devpanda.factorynetwork.lang;

import java.util.List;
import java.util.Locale;

/**
 * What has to stand behind a keyword.
 *
 * <p><b>The place where the grammar stops being a document.</b> In
 * {@code grammatik.md} it says {@code row STRING expr} — in the editor it stood
 * nowhere. Whoever had typed {@code row} was offered the list of keywords
 * again and had to lay the docs next to it to know that a text comes now and an
 * expression after it.
 *
 * <p>Here the same table stands once, as data. Two things read it: the
 * completion, to offer the fitting thing at each position, and the hint line,
 * which shows the whole shape and marks where you currently are.
 *
 * <p><b>Deliberately in the language package and not in the editor.</b> It
 * belongs to the language, not to a window — the in-game editor is only the
 * first that needs it. A language server for VS Code reads the same table.
 */
public final class Signatures {

    /** What kind of value stands at a position. */
    public enum Kind {
        /** A text in quotation marks. */
        STRING,
        /** An expression — stock levels, computations, comparisons. */
        EXPR,
        /** A whole number. */
        INT,
        /** A duration, e.g. {@code 5s} or {@code 2min}. */
        DURATION,
        /** A connector, {@code storage} or {@code crafting}. */
        TARGET,
        /** A selector expression over items or tags. */
        SELECTION,
        /** The name of a distribution. */
        STRATEGY,
        /** The name of a function in the project. */
        FUNCTION,
        /** A list of connectors or patterns. */
        MEMBERS,
        /** A name that only comes into being here — there is no suggestion for it. */
        NEW_NAME,
        /** The name of an event from the project. */
        EVENT,
        /** A fixed word that has to stand exactly so. */
        LITERAL
    }

    /**
     * A position in a shape.
     *
     * @param kind  what has to stand there
     * @param label how it is called in the hint line
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
         * A fixed word that may also be missing.
         *
         * <p>It always introduces exactly one value — {@code from ziel},
         * {@code to ziel}. If the word is missing, the value is missing too, and
         * when counting, both positions are skipped. That is the rule with which
         * {@code move menge [from ziel] to ziel} gets by without a second
         * compiler.
         */
        static Slot maybe(String word) {
            return new Slot(Kind.LITERAL, word, true);
        }
    }

    /**
     * A keyword with everything that belongs behind it.
     *
     * @param keyword the word itself
     * @param slots   the positions behind it, in order
     * @param help    a sentence about it, for the explanation when shown
     */
    public record Signature(String keyword, List<Slot> slots, String help) {

        /** The whole shape as text: {@code row string expr}. */
        public String shape() {
            StringBuilder out = new StringBuilder(keyword);
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.optional()) {
                    // The fixed word and its value stand together in square
                    // brackets — that is how the grammar writes it too.
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

        /** What stands at this position, or {@code null} past the end. */
        public Slot slotAt(int index) {
            return index >= 0 && index < slots.size() ? slots.get(index) : null;
        }
    }

    private static Signature of(String keyword, String help, Slot... slots) {
        return new Signature(keyword, List.of(slots), help);
    }

    /** The entries in a display. */
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
                    Slot.of(Kind.STRING), Slot.of(Kind.FUNCTION)),
            of("scale", "Wie groß die Schrift ist; 1 ist normal.",
                    Slot.of(Kind.INT)));

    /**
     * The entries in a recipe at a machine.
     *
     * <p>Two, and both may appear multiple times: {@code in} for each
     * ingredient, {@code out} for each result. The amount is always there, even
     * the one.
     */
    public static final List<Signature> RECIPE = List.of(
            of("in", "Eine Zutat, mit Menge.",
                    Slot.of(Kind.INT), Slot.of(Kind.SELECTION)),
            of("out", "Was dabei herauskommt, mit Menge.",
                    Slot.of(Kind.INT), Slot.of(Kind.SELECTION)));

    /**
     * The entries in a {@code store}.
     *
     * <p>Two, and both may be missing: a store without entries takes everything
     * and stands level with the cells. The most common case needs no more — a
     * chest that should belong to the network.
     */
    public static final List<Signature> STORE = List.of(
            of("priority", "Wohin zuerst eingelagert wird. Die Zellen stehen auf 0.",
                    Slot.of(Kind.INT)),
            of("filter", "Was hinein darf. Ohne Angabe alles.",
                    Slot.of(Kind.SELECTION)));

    /** The entries in a worker. */
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

    /** The entries in a group. */
    public static final List<Signature> GROUP = List.of(
            of("members", "Die Connectoren der Gruppe, mit Komma getrennt.",
                    Slot.of(Kind.MEMBERS)),
            of("strategy", "Wie auf sie verteilt wird.", Slot.of(Kind.STRATEGY)));

    /**
     * The lines in a filter template.
     *
     * <p>Only one shape, and it is the exception: an ordinary line is a selector
     * and needs no word in front. The editor therefore suggests the selectors
     * themselves at this position, and {@code except} on top.
     */
    public static final List<Signature> FILTER = List.of(
            of("except", "Nimmt wieder heraus, was die Zeilen darüber einschließen.",
                    Slot.of(Kind.SELECTION)));

    /**
     * The statements in a function or an event block.
     *
     * <p>Conditions stand without round brackets and a block is mandatory — so
     * the shape of {@code if} ends at the expression. The editor sets the curly
     * brace itself at the line break.
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

    /**
     * The functions that exist without a receiver.
     *
     * <p>They were listed nowhere, and so no editor suggested them —
     * {@code log()} has existed since the first day, and one had to find it in
     * the docs.
     */
    public static final List<Member> FREE_FUNCTIONS = List.of(
            new Member("log", "log(text)", "Schreibt ins Protokoll, als info."),
            new Member("info", "info(text)", "Was gut lief."),
            new Member("warn", "warn(text)",
                    "Etwas stimmt nicht, die Fabrik läuft weiter."),
            new Member("error", "error(text)", "Etwas ist stehen geblieben."),
            new Member("debug", "debug(text)",
                    "Zwischenstände beim Suchen. Im Terminal erst auf Wunsch sichtbar."),
            new Member("min", "min(zahl, zahl) zahl", "Die kleinere. Auch mit mehr als zwei."),
            new Member("max", "max(zahl, zahl) zahl", "Die größere. Auch mit mehr als zwei."),
            new Member("abs", "abs(zahl) zahl", "Ohne Vorzeichen."),
            new Member("round", "round(zahl) int", "Auf die nächste ganze Zahl."),
            new Member("floor", "floor(zahl) int", "Abgerundet."),
            new Member("ceil", "ceil(zahl) int", "Aufgerundet."),
            new Member("random", "random(von, bis) int",
                    "Eine Zahl dazwischen, beide Enden eingeschlossen."),
            new Member("craft", "craft(auswahl) int",
                    "Bestellt eine Fertigung und liefert die Kennung des Auftrags. "
                            + "Null heißt: kein Rezept."));

    /** The distributions that {@code strategy} knows. */
    public static final List<String> STRATEGIES = List.of(
            "round_robin", "first_available", "least_filled", "random", "priority");

    /**
     * What has a shape at the top level.
     *
     * <p>So far there was nothing here: all declarations open a block, and the
     * question "what comes after the word" only arose inside it. {@code global}
     * is the first that finishes on one line — and thus the first for which a
     * shape line at the top level makes any sense.
     */
    public static final List<Signature> TOP_LEVEL = List.of(
            of("global", "Ein Wert, den alle Dateien sehen.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("="),
                    Slot.of(Kind.EXPR)),
            of("const", "Ein Wert, der sich nie ändert.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("="),
                    Slot.of(Kind.EXPR)));

    /**
     * The words a declaration begins with.
     *
     * <p><b>Here and only here.</b> It stood in three places — in the in-game
     * editor, in the export for VS Code, and in the parser anyway. This came to
     * light when adding {@code store}: the in-game editor offered it, VS Code
     * did not, and both test runs were green, because each checked its own list.
     */
    public static final List<String> DECLARATIONS = List.of(
            "worker", "group", "filter", "multiblock", "event", "display", "recipe",
            "store", "fn", "on", "global", "const");

    /**
     * The declarations in which entries with a shape stand.
     *
     * <p>Not all: {@code fn}, {@code on} and {@code multiblock} contain
     * statements, {@code event} and {@code global} finish on one line. For the
     * others there is a list of allowed words, and both editors need it.
     */
    public static final List<String> BLOCKS_WITH_ENTRIES = List.of(
            "display", "worker", "group", "filter", "recipe", "store", "fn");

    /** A thing that stands on a device. */
    public record Member(String name, String shape, String help) {
    }

    /**
     * What stands on a device — {@code crusher_1.online}.
     *
     * <p><b>Only what the interpreter really knows.</b> The list below is
     * complete; {@code output()} and {@code busy} were struck on Aug 25, because
     * the one said the same as {@code move} and the other nothing verifiable.
     * What stands here has to run — a suggestion that leads into a runtime error
     * is worse than none at all.
     *
     * <p>The same for every device: device-specific things after the dot exist
     * only once the members from §6 are built. Then it is one entry here and
     * nothing more. What a <b>group</b> can do stands separately in
     * {@link #GROUP_MEMBERS} — those are other things.
     */
    public static final List<Member> MEMBERS = List.of(
            new Member("online", "bool",
                    "Ob das Gerät gerade im Netz hängt."),
            new Member("name", "string",
                    "Der Name, den die Beschriftungspistole vergeben hat."),
            new Member("redstone", "redstone() int, redstone(int)",
                    "Die Redstone-Stärke, 0 bis 15. Mit Zahl gesetzt, ohne gelesen."),
            new Member("count", "count(selection) int",
                    "Wie viel von einer Art in diesem Gerät liegt. Ohne Auswahl alles."),
            new Member("insert", "insert(selection) int",
                    "Legt aus dem Speicher etwas hinein. Gibt zurück, wie viel ankam — "
                            + "weniger ist normal."),
            new Member("items", "items() list",
                    "Was gerade im Gerät liegt. Leere Fächer fallen weg."),
            new Member("slots", "slots(nummer) list",
                    "Bestimmte Fächer — eine Nummer oder ein Bereich wie 1..5. "
                            + "Auch als Quelle oder Ziel eines move."),
            new Member("energy", "energy() int",
                    "Wie viel Strom in der Maschine steht, in FE. Ohne Speicher null."),
            new Member("click", "click() bool",
                    "Fasst die Maschine an, wie ein Rechtsklick. Gibt zurück, ob er "
                            + "angekommen ist. Ein Fenster geht dabei nicht auf."));

    /**
     * What stands on the network itself: {@code network.power}.
     *
     * <p><b>Without parentheses</b>, unlike {@code geraet.energy()}. The
     * difference is not taste: the state of a foreign machine is a look into the
     * world, one's own supply sits in the controller.
     */
    public static final List<Member> NETWORK_MEMBERS = List.of(
            new Member("power", "int", "Wie viel Strom im Netz steht, in FE."),
            new Member("capacity", "int",
                    "Wie viel hineinpasst, in FE. Die Bezugsgröße zu power."));

    /**
     * What stands on a list.
     *
     * <p>All five that {@code sprache.md} §12 names. {@code where} and
     * {@code sort} evaluate their expression per entry, with {@code it} as that
     * entry.
     */
    public static final List<Member> LIST_MEMBERS = List.of(
            new Member("count", "count() int", "Wie viele Einträge."),
            new Member("first", "first() any", "Der erste Eintrag, oder nichts."),
            new Member("sum", "sum() int", "Alle Zahlen aufaddiert."),
            new Member("where", "where(expr) list",
                    "Behält, wofür der Ausdruck wahr ist. it ist der Eintrag."),
            new Member("sort", "sort(expr) list",
                    "Ordnet nach dem Ausdruck. it ist der Eintrag."),
            new Member("plus", "plus(any) list", "Dieselbe Liste mit einem mehr."),
            new Member("without", "without(any) list",
                    "Dieselbe Liste ohne jedes Vorkommen davon."),
            new Member("rest", "rest() list", "Alles außer dem ersten Eintrag."));

    /**
     * What stands on a device group.
     *
     * <p>Two calls and nothing else. What a single device can do you ask at a
     * member — a group is not a device with more compartments, but a
     * distribution.
     */
    public static final List<Member> GROUP_MEMBERS = List.of(
            new Member("members", "members() list",
                    "Die Geräte der Gruppe, in der Reihenfolge ihrer Verteilung."),
            new Member("send", "send(selection) int",
                    "Schickt aus dem Speicher an die Gruppe. Wohin, entscheidet ihre "
                            + "strategy."));

    /**
     * What stands on a single entry — {@code it.amount}.
     *
     * <p>The amount and three ways to name the sort. The editor knows no types
     * and therefore cannot know whether an entry means items, fluids, or
     * chemicals; it offers all and says in the shape what each applies to.
     */
    public static final List<Member> ENTRY_MEMBERS = List.of(
            new Member("amount", "amount int", "Die Menge dieses Postens."),
            new Member("item", "item item",
                    "Die Art dieses Postens — nur, wenn er genau eine meint."),
            new Member("fluid", "fluid fluid",
                    "Die Sorte dieses Postens, bei einer Flüssigkeitsliste."),
            new Member("chemical", "chemical chemical",
                    "Dieselbe Angabe für Chemikalien. Braucht Mekanism."));

    /**
     * The shapes that apply in this kind of block.
     *
     * @param declaration the declaration word, e.g. {@code display}
     */
    public static List<Signature> forBlock(String declaration) {
        if (declaration == null) {
            // No block means the top level, and since global something with a
            // shape stands there.
            return TOP_LEVEL;
        }
        return switch (declaration) {
            case "display" -> DISPLAY;
            case "recipe" -> RECIPE;
            case "store" -> STORE;
            case "worker" -> WORKER;
            case "group" -> GROUP;
            case "filter" -> FILTER;
            // fn, on and multiblock contain statements, not entries.
            case "fn", "on", "multiblock" -> STATEMENT;
            default -> List.of();
        };
    }

    /** The shape for a keyword in this kind of block, or {@code null}. */
    public static Signature find(String declaration, String keyword) {
        for (Signature signature : forBlock(declaration)) {
            if (signature.keyword().equals(keyword)) {
                return signature;
            }
        }
        return null;
    }

    /**
     * Where the cursor stands within an entry.
     *
     * @param signature the shape that was begun on this line
     * @param slotIndex the position that is currently up; past the last one
     *                  stands the number of positions, then the entry is full
     */
    public record Where(Signature signature, int slotIndex) {

        /** What has to stand here, or {@code null} when the entry is full. */
        public Slot slot() {
            return signature.slotAt(slotIndex);
        }

        public boolean isComplete() {
            return slot() == null;
        }
    }

    /**
     * Reads from the started line which entry is meant and which position of it
     * is up.
     *
     * <p>Counting is over the words behind it, where a text in quotation marks
     * is <b>one</b> — otherwise {@code row "Freier
     * Platz"} would slip one word further, and the hint line would point at the
     * wrong position.
     *
     * <p>If the line ends with a space, the next position is begun; otherwise
     * the last one is still being typed. That is the difference between
     * "{@code row "a" }" — now the expression comes — and
     * "{@code row "a}" — the text is not yet finished.
     *
     * @param declaration    the declaration word of the surrounding block
     * @param lineUpToCursor the line up to the cursor
     * @return where you are, or {@code null} outside any known entry
     */
    public static Where at(String declaration, String lineUpToCursor) {
        String text = lineUpToCursor.stripLeading();
        if (text.isEmpty()) {
            return null;
        }
        int wordEnd = text.indexOf(' ');
        if (wordEnd < 0) {
            // The keyword itself is still being typed.
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
            // The last word is still being typed and does not count yet.
            words = words.subList(0, words.size() - 1);
        }
        return new Where(signature, slotAfter(signature, words));
    }

    /**
     * Which position is up after these words.
     *
     * <p>Word by word through the shape. If a position comes up that <b>can
     * be</b> a fixed word, and the word does not match it, it falls away
     * together with its value: {@code move 64 to kiste} has no {@code from}, and
     * without this rule the {@code to} would land on the position of the source.
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

    /** The words behind the keyword; a text in quotation marks is one. */
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
