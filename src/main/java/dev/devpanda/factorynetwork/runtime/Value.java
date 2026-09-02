package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.Item;

import java.util.List;

/**
 * A value at runtime.
 *
 * <p>Sealed so that every place that handles values must be exhaustive. The
 * types correspond to those in {@code sprache.md}, section 5.
 */
public sealed interface Value {

    record Int(long value) implements Value {}

    record Decimal(double value) implements Value {}

    record Bool(boolean value) implements Value {}

    record Text(String value) implements Value {}

    /** A duration, in ticks. */
    record Duration(long ticks) implements Value {}

    /**
     * A single resource — an item kind, a fluid type, a chemical.
     *
     * <p><b>One shape for three kinds.</b> Previously three records stood here
     * side by side, and with them three twin branches in every place that
     * handles values. What differs per kind lives in {@link ResourceKind};
     * what is the same lives here, once.
     *
     * <p>{@code key} carries the resource itself: an {@link Item}, a
     * {@code Fluid} or — for a chemical — its identifier as text. The
     * constructor checks that it matches the kind; whoever extracts it uses
     * {@link #item()}, {@link #fluid()} or {@link #chemical()}.
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
     * A selection with an optional leading amount, as in
     * {@code 64 item:iron_ore}.
     *
     * <p>{@code amount} is {@code -1} when no amount was written — then
     * everything available is meant. The selection stays here as written
     * text and is only resolved where the registry is reachable.
     */
    record Request(String selector, long amount) implements Value {

        /** What a selection refers to. */
        public enum Kind { ITEM, FLUID, CHEMICAL, TAG, FLUIDTAG, ALL, UNKNOWN }

        public boolean hasAmount() {
            return amount >= 0;
        }

        /**
         * The kind of the selection.
         *
         * <p>It stands in the written text before the colon. This method is
         * the only place that reads the text for it — everywhere else asks
         * {@code kind()}. Without this information,
         * {@code move 1000 fluid:water} would report that there is no water
         * in the pack instead of the true reason: that fluids cannot be meant
         * here.
         */
        public Kind kind() {
            // "all" carries no type and therefore no colon.
            if ("all".equals(selector)) {
                return Kind.ALL;
            }
            int colon = selector.indexOf(':');
            if (colon < 0) {
                return Kind.UNKNOWN;
            }
            // The same list as in the lexer and the parser, and that is the
            // point: it once stood here for the fourth time, with its own
            // answer to an unknown word.
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
                // A registered third-party kind. Nothing more can be said
                // from text alone here; whoever needs more takes the
                // selection expression rather than its written form.
                case CUSTOM, POWER, ALL -> Kind.UNKNOWN;
            };
        }
    }

    /**
     * A selection, already resolved — and at the same time an entry from a
     * stock list.
     *
     * <p>Amounts for items in pieces, for fluids and chemicals in
     * millibuckets. {@code amount} is {@code -1} when none was written.
     *
     * <p>The resources themselves live in {@code keys}, in the form that
     * {@link ResourceKind#type()} names for this kind. Mixing is not possible:
     * the constructor checks every entry. A selection over water and stone
     * would be something different at every usage site — the same rule that
     * {@code FilterKind} establishes for templates.
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
         * The first entry as a single value.
         *
         * <p>Intended for the case in which there is only one. That this is
         * so is checked by the caller — it has the message for it; here there
         * would only be a guessed one.
         */
        public Resource single() {
            return new Resource(kind, keys.get(0));
        }
    }

    /** A device in the network, by its name. */
    record Device(String name) implements Value {}

    /**
     * Specific slots of a device — {@code brecher_1.slots(2..3)}.
     *
     * <p><b>The same shape for reading and for moving.</b> When read it
     * behaves like a list of entries; as the source or target of a
     * {@code move} like a device that has only these slots. That way no second
     * notation is needed for "take only from the output".
     */
    record DeviceSlots(String device, List<Integer> slots) implements Value {}

    /**
     * A device group, by its name.
     *
     * <p><b>The name and not the members.</b> Who is in the group today is
     * decided by the network: a pattern like {@code furnace_*} picks up the
     * furnace as soon as it is placed. A value that carried the members along
     * would already be stale when read one tick later.
     */
    record Group(String name) implements Value {}

    /** The network storage or another built-in target. */
    record Builtin(String name) implements Value {}

    record ValueList(List<Value> entries) implements Value {}

    /** Stands for "no value" — for example the return value of a function without return. */
    record Nothing() implements Value {

        private static final Nothing INSTANCE = new Nothing();

        public static Nothing get() {
            return INSTANCE;
        }
    }

    /** For messages: how the value appears in the terminal. */
    default String describe() {
        return switch (this) {
            case Int value -> String.valueOf(value.value());
            case Decimal value -> String.valueOf(value.value());
            case Bool value -> value.value() ? "wahr" : "falsch";
            case Text value -> value.value();
            case Duration value -> value.ticks() + "t";
            case Request value -> (value.hasAmount() ? value.amount() + " " : "")
                    + value.selector();
            // Via nameOf, so that the log says "Hydrogen" and not the
            // identifier. Without Mekanism there is no name, and then the
            // identifier is shown — nothing is made up.
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

    /** This many entries of a list are named, the rest counted. */
    int LIST_SHOWN = 6;

    /**
     * A list as it appears in the log and in the network tab.
     *
     * <p>Previously this showed the number of entries, and that helped
     * nobody: whoever writes out a list wants to know what is in it. It is
     * shortened with a count and not with a cut-off — a list that ends
     * silently reads like a complete one.
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
     * A literal from the syntax tree as a value.
     *
     * <p>Needed for the initial value of a global: it stands in the program
     * as a literal and must become a value the runtime holds when it is
     * adopted. An expression that is not a literal yields {@link Nothing} —
     * that it must be a literal is checked by {@code GlobalCheck}, which
     * reports it where it can be seen.
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
            // The initial value of a list is a literal too. Without this
            // case, "global warteschlange = []" would come out as Nothing
            // when adopted — a global that exists and is nothing.
            case dev.devpanda.factorynetwork.lang.ast.Expr.ListLit list ->
                    new ValueList(list.entries().stream().map(Value::ofLiteral).toList());
            case null, default -> Nothing.get();
        };
    }
}
