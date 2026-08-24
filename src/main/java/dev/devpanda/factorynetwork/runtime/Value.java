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

    /** Eine Gegenstandsart. */
    record ItemValue(Item item) implements Value {}

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
        public enum Kind { ITEM, FLUID, CHEMICAL, TAG, UNKNOWN }

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
            int colon = selector.indexOf(':');
            if (colon < 0) {
                return Kind.UNKNOWN;
            }
            return switch (selector.substring(0, colon)) {
                case "item" -> Kind.ITEM;
                case "fluid" -> Kind.FLUID;
                case "chemical" -> Kind.CHEMICAL;
                case "tag" -> Kind.TAG;
                default -> Kind.UNKNOWN;
            };
        }
    }

    /** Eine einzelne Flüssigkeitssorte. */
    record FluidValue(net.minecraft.world.level.material.Fluid fluid) implements Value {}

    /** Eine Auswahl von Flüssigkeiten, schon aufgelöst. Mengen in Millibucket. */
    record FluidSelection(List<net.minecraft.world.level.material.Fluid> fluids, long amount)
            implements Value {}

    /** Eine Auswahl von Gegenstandsarten, schon aufgelöst. */
    record Selection(List<Item> items, long amount) implements Value {}

    /** Ein Gerät im Netzwerk, über seinen Namen. */
    record Device(String name) implements Value {}

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
            case ItemValue value -> value.item().toString();
            case Request value -> (value.hasAmount() ? value.amount() + " " : "")
                    + value.selector();
            case Selection value -> value.items().size() + " Arten";
            case FluidSelection value -> value.fluids().size() + " Flüssigkeiten";
            case FluidValue value -> net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(value.fluid()).toString();
            case Device value -> value.name();
            case Builtin value -> value.name();
            case ValueList value -> value.entries().size() + " Einträge";
            case Nothing ignored -> "nichts";
        };
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
            case null, default -> Nothing.get();
        };
    }
}
