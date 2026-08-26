package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Ein Wert zur Laufzeit.
 *
 * <p>Versiegelt, damit jede Stelle, die Werte behandelt, vollständig sein
 * muss. Die Typen entsprechen denen aus {@code sprache.md}, Abschnitt 5.
 */
public sealed interface Value {

    record Int(long value) implements Value {}

    record Decimal(double value) implements Value {}

    record Bool(boolean value) implements Value {}

    record Text(String value) implements Value {}

    /** Eine Zeitangabe, in Ticks. */
    record Duration(long ticks) implements Value {}

    /**
     * Eine einzelne Ressource — eine Gegenstandsart, eine Flüssigkeitssorte,
     * eine Chemikalie.
     *
     * <p><b>Eine Form für drei Arten.</b> Vorher standen hier drei Records
     * nebeneinander, und mit ihnen drei Zwillingszweige in jeder Stelle, die
     * Werte behandelt. Was je Art verschieden ist, steht in
     * {@link ResourceKind}; was gleich ist, steht hier einmal.
     *
     * <p>{@code key} trägt die Ressource selbst: einen {@link Item}, einen
     * {@code Fluid} oder — bei einer Chemikalie — ihre Kennung als Text. Dass
     * sie zur Art passt, prüft der Konstruktor; wer sie herausholt, nimmt
     * {@link #item()}, {@link #fluid()} oder {@link #chemical()}.
     */
    record Resource(ResourceKind kind, Object key) implements Value {

        public Resource {
            if (!kind.type().isInstance(key)) {
                throw new IllegalArgumentException("Eine Ressource der Art " + kind
                        + " ist kein " + (key == null ? "nichts" : key.getClass()) + ".");
            }
        }

        public static Resource ofItem(Item item) {
            return new Resource(ResourceKinds.ITEM, item);
        }

        public static Resource ofFluid(net.minecraft.world.level.material.Fluid fluid) {
            return new Resource(ResourceKinds.FLUID, fluid);
        }

        public static Resource ofChemical(String id) {
            return new Resource(ResourceKinds.CHEMICAL, id);
        }

        public Item item() {
            return (Item) key;
        }

        public net.minecraft.world.level.material.Fluid fluid() {
            return (net.minecraft.world.level.material.Fluid) key;
        }

        public String chemical() {
            return (String) key;
        }
    }

    /**
     * Eine Auswahl mit wahlweise vorangestellter Menge, wie in
     * {@code 64 item:iron_ore}.
     *
     * <p>{@code amount} ist {@code -1}, wenn keine Menge dastand — dann ist
     * alles gemeint, was verfügbar ist. Die Auswahl bleibt hier als
     * geschriebener Text stehen und wird erst dort aufgelöst, wo die Registry
     * erreichbar ist.
     */
    record Request(String selector, long amount) implements Value {

        /** Worauf sich eine Auswahl bezieht. */
        public enum Kind { ITEM, FLUID, CHEMICAL, TAG, FLUIDTAG, ALL, UNKNOWN }

        public boolean hasAmount() {
            return amount >= 0;
        }

        /**
         * Die Art der Auswahl.
         *
         * <p>Sie steht im geschriebenen Text vor dem Doppelpunkt. Diese
         * Methode ist die einzige Stelle, die ihn dafür liest — überall sonst
         * wird nach {@code kind()} gefragt. Ohne diese Auskunft käme bei
         * {@code move 1000 fluid:water} die Meldung, es gebe kein Wasser im
         * Pack, statt der wahren: dass Flüssigkeiten hier nicht gemeint sein
         * können.
         */
        public Kind kind() {
            // „all" trägt keine Sorte und deshalb keinen Doppelpunkt.
            if ("all".equals(selector)) {
                return Kind.ALL;
            }
            int colon = selector.indexOf(':');
            if (colon < 0) {
                return Kind.UNKNOWN;
            }
            // Dieselbe Liste wie im Lexer und im Parser, und das ist der
            // Punkt: Sie stand hier einmal zum vierten Mal, mit einer
            // eigenen Antwort auf ein unbekanntes Wort.
            var written = ResourceKinds.kindOf(selector.substring(0, colon));
            if (written == null) {
                return Kind.UNKNOWN;
            }
            return switch (written) {
                case ITEM -> Kind.ITEM;
                case FLUID -> Kind.FLUID;
                case CHEMICAL -> Kind.CHEMICAL;
                case FLUIDTAG -> Kind.FLUIDTAG;
                case TAG -> Kind.TAG;
                // Eine angemeldete fremde Art. Aus einem Text allein lässt
                // sich hier nicht mehr sagen; wer mehr braucht, nimmt den
                // Auswahlausdruck und nicht seine Schreibweise.
                case CUSTOM, POWER, ALL -> Kind.UNKNOWN;
            };
        }
    }

    /**
     * Eine Auswahl, schon aufgelöst — und zugleich ein Posten aus einer
     * Bestandsliste.
     *
     * <p>Mengen bei Gegenständen in Stück, bei Flüssigkeiten und Chemikalien
     * in Millibucket. {@code amount} ist {@code -1}, wenn keine dastand.
     *
     * <p>Die Ressourcen selbst liegen in {@code keys}, in der Form, die
     * {@link ResourceKind#type()} für diese Art nennt. Gemischt geht nicht:
     * Der Konstruktor prüft jeden Eintrag. Eine Auswahl über Wasser und Stein
     * wäre an jeder Verwendungsstelle etwas anderes — dieselbe Regel, die
     * {@code FilterKind} für Vorlagen aufstellt.
     */
    record Selection(ResourceKind kind, List<?> keys, long amount) implements Value {

        public Selection {
            keys = List.copyOf(keys);
            for (Object key : keys) {
                if (!kind.type().isInstance(key)) {
                    throw new IllegalArgumentException("Eine Auswahl der Art " + kind
                            + " kann kein " + key.getClass() + " enthalten.");
                }
            }
        }

        public static Selection ofItems(List<Item> items, long amount) {
            return new Selection(ResourceKinds.ITEM, items, amount);
        }

        public static Selection ofFluids(
                List<net.minecraft.world.level.material.Fluid> fluids, long amount) {
            return new Selection(ResourceKinds.FLUID, fluids, amount);
        }

        public static Selection ofChemicals(List<String> ids, long amount) {
            return new Selection(ResourceKinds.CHEMICAL, ids, amount);
        }

        @SuppressWarnings("unchecked")
        public List<Item> items() {
            return (List<Item>) keys;
        }

        @SuppressWarnings("unchecked")
        public List<net.minecraft.world.level.material.Fluid> fluids() {
            return (List<net.minecraft.world.level.material.Fluid>) keys;
        }

        @SuppressWarnings("unchecked")
        public List<String> chemicals() {
            return (List<String>) keys;
        }

        /**
         * Der erste Eintrag als einzelner Wert.
         *
         * <p>Gemeint ist der Fall, in dem es nur einen gibt. Dass es so ist,
         * prüft der Aufrufer — er hat die Meldung dafür, hier gäbe es nur
         * eine geratene.
         */
        public Resource single() {
            return new Resource(kind, keys.get(0));
        }
    }

    /** Ein Gerät im Netzwerk, über seinen Namen. */
    record Device(String name) implements Value {}

    /**
     * Bestimmte Fächer eines Geräts — {@code brecher_1.slots(2..3)}.
     *
     * <p><b>Zum Lesen und zum Bewegen dieselbe Form.</b> Gelesen verhält sie
     * sich wie eine Liste von Posten, als Quelle oder Ziel eines
     * {@code move} wie ein Gerät, das nur diese Fächer hat. Damit braucht es
     * keine zweite Schreibweise für „nimm nur aus dem Ausgang".
     */
    record DeviceSlots(String device, List<Integer> slots) implements Value {}

    /**
     * Eine Gerätegruppe, über ihren Namen.
     *
     * <p><b>Der Name und nicht die Mitglieder.</b> Wer heute in der Gruppe
     * steht, entscheidet das Netz: Ein Muster wie {@code furnace_*} nimmt den
     * Ofen auf, sobald er dasteht. Ein Wert, der die Mitglieder mitträgt,
     * wäre schon veraltet, wenn er einen Tick später gelesen wird.
     */
    record Group(String name) implements Value {}

    /** Der Netzwerkspeicher oder ein anderes eingebautes Ziel. */
    record Builtin(String name) implements Value {}

    record ValueList(List<Value> entries) implements Value {}

    /** Steht für „kein Wert" — etwa der Rückgabewert einer Funktion ohne return. */
    record Nothing() implements Value {

        private static final Nothing INSTANCE = new Nothing();

        public static Nothing get() {
            return INSTANCE;
        }
    }

    /** Für Meldungen: wie der Wert im Terminal erscheint. */
    default String describe() {
        return switch (this) {
            case Int value -> String.valueOf(value.value());
            case Decimal value -> String.valueOf(value.value());
            case Bool value -> value.value() ? "wahr" : "falsch";
            case Text value -> value.value();
            case Duration value -> value.ticks() + "t";
            case Request value -> (value.hasAmount() ? value.amount() + " " : "")
                    + value.selector();
            // Über nameOf, damit im Protokoll „Wasserstoff" steht und nicht
            // die Kennung. Ohne Mekanism gibt es keinen Namen, und dann steht
            // die Kennung da — erfunden wird nichts.
            case Resource value -> value.kind().nameOf(value.key());
            case Selection value -> value.keys().size() + " " + value.kind().plural();
            case Device value -> value.name();
            case Group value -> value.name();
            case DeviceSlots value -> value.device() + " Fach "
                    + (value.slots().size() == 1 ? value.slots().get(0)
                            : value.slots().size() + " Stück");
            case Builtin value -> value.name();
            case ValueList value -> listText(value);
            case Nothing ignored -> "nichts";
        };
    }

    /** So viele Einträge einer Liste werden genannt, der Rest gezählt. */
    int LIST_SHOWN = 6;

    /**
     * Eine Liste, wie sie im Protokoll und im Netz-Reiter erscheint.
     *
     * <p>Vorher stand hier die Zahl der Einträge, und die half niemandem:
     * Wer eine Liste hinschreibt, will wissen, was darin steht. Gekürzt wird
     * mit einer Zählung und nicht mit einem Abbruch — eine Liste, die still
     * endet, liest sich wie eine vollständige.
     */
    private static String listText(ValueList list) {
        StringBuilder out = new StringBuilder("[");
        int shown = Math.min(list.entries().size(), LIST_SHOWN);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(list.entries().get(i).describe());
        }
        if (list.entries().size() > shown) {
            out.append(shown == 0 ? "" : ", ").append("… +")
                    .append(list.entries().size() - shown);
        }
        return out.append(']').toString();
    }

    /**
     * Ein Literal aus dem Syntaxbaum als Wert.
     *
     * <p>Gebraucht für den Anfangswert eines globalen Werts: Er steht als
     * Literal im Programm und muss beim Übernehmen zu einem Wert werden, den
     * die Laufzeit hält. Ein Ausdruck, der keiner ist, liefert
     * {@link Nothing} — dass es ein Literal sein muss, prüft
     * {@code GlobalCheck} und meldet es dort, wo man es sieht.
     */
    static Value ofLiteral(dev.devpanda.factorynetwork.lang.ast.Expr expr) {
        return switch (expr) {
            case dev.devpanda.factorynetwork.lang.ast.Expr.IntLit number ->
                    new Int(number.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.FloatLit number ->
                    new Decimal(number.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.StringLit text ->
                    new Text(text.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.BoolLit flag ->
                    new Bool(flag.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.DurationLit duration ->
                    new Duration(duration.ticks());
            // Auch der Anfangswert einer Liste ist ein Literal. Ohne diesen
            // Fall stünde „global warteschlange = []" beim Übernehmen als
            // Nothing da — ein globaler Wert, den es gibt und der nichts ist.
            case dev.devpanda.factorynetwork.lang.ast.Expr.ListLit list ->
                    new ValueList(list.entries().stream().map(Value::ofLiteral).toList());
            case null, default -> Nothing.get();
        };
    }
}
