package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resource kinds that exist — the built-in ones and the registered ones.
 *
 * <p>Decided on 26 August: third-party mods may extend the language. This
 * class is the door to that. Whoever brings their own kind implements
 * {@link ResourceKind} and calls {@link #register} in the mod constructor.
 *
 * <p><b>Registration happens at load time and never again afterwards.</b>
 * That is precisely the irreversibility accepted with the decision: a program
 * with {@code source:mana} runs in a world whose core has never seen the
 * word, and whoever withdrew the kind later would break third-party programs.
 * {@link #freeze()} turns that from a request into a promise.
 *
 * <p><b>A prefix belongs to one kind.</b> The registry rejects two entries
 * with the same word instead of shadowing one of them — which one would win
 * would depend on load order, and that is not an explanation a player can
 * read.
 */
public final class ResourceKinds {

    /**
     * What belongs to the language and does not denote a kind.
     *
     * <p>{@code tag} and {@code fluidtag} are notations for kinds that
     * already exist. {@code power} and {@code all} carry no type at all.
     * Whoever claimed one of these would make existing programs ambiguous.
     */
    public static final Set<String> RESERVED = Set.of("tag", "fluidtag", "power", "all");

    /** Registration order — it decides nothing, but reads better. */
    private static final Map<String, ResourceKind> BY_PREFIX = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceKind> BY_ID = new LinkedHashMap<>();
    private static final Map<String, ResourceKind> BY_TAG = new LinkedHashMap<>();

    private static boolean frozen;

    /**
     * The list for the lexer, built once.
     *
     * <p>It asks for it at <b>every</b> word in the source text. Assembling
     * it anew there each time would be one allocation per token — and the
     * list only changes on registration.
     */
    private static Set<String> selectorPrefixes;

    private ResourceKinds() {
    }

    /** Item kinds. A {@code tag:} resolves to this. */
    public static final ResourceKind ITEM = new Builtin(
            "item", "Arten", Item.class, "item", "sel") {

        @Override
        public String idOf(Object key) {
            return BuiltInRegistries.ITEM.getKey((Item) key).toString();
        }

        @Override
        public Object fromId(String id) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                throw new ScriptError("Den Gegenstand " + id + " gibt es nicht mehr.",
                        "Der Ablauf hielt ihn in einer Variablen fest. Wurde eine Mod "
                                + "aus dem Pack genommen?");
            }
            return BuiltInRegistries.ITEM.get(key);
        }

        @Override
        public String nameOf(Object key) {
            return key.toString();
        }

        @Override
        public List<?> resolve(Expr selector) {
            return ItemSelection.resolve(selector);
        }

        @Override
        public ResourceStore newStore() {
            return new dev.devpanda.factorynetwork.network.NetworkStorage();
        }
    };

    /** Fluid types, measured in millibuckets. */
    public static final ResourceKind FLUID = new Builtin(
            "fluid", "Flüssigkeiten", Fluid.class, "fluid", "fluidsel") {

        @Override
        public String idOf(Object key) {
            return BuiltInRegistries.FLUID.getKey((Fluid) key).toString();
        }

        @Override
        public Object fromId(String id) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
                throw new ScriptError("Die Flüssigkeit " + id + " gibt es nicht mehr.",
                        "Der Ablauf hielt sie in einer Variablen fest. Wurde eine Mod "
                                + "aus dem Pack genommen?");
            }
            return BuiltInRegistries.FLUID.get(key);
        }

        @Override
        public String nameOf(Object key) {
            return idOf(key);
        }

        @Override
        public List<?> resolve(Expr selector) {
            return FluidSelection.resolve(selector);
        }

        @Override
        public ResourceStore newStore() {
            return new dev.devpanda.factorynetwork.network.NetworkFluids();
        }
    };

    /**
     * Chemicals from Mekanism, as an identifier.
     *
     * <p>A string and not a Mekanism type: a signature with {@code Chemical}
     * in it would keep the class from loading without the mod. Without
     * Mekanism a selection matches nothing and the store can do nothing —
     * both are the truth about such a pack, not a workaround.
     */
    public static final ResourceKind CHEMICAL = new Builtin(
            "chemical", "Chemikalien", String.class, "chem", "chemsel") {

        @Override
        public String idOf(Object key) {
            return (String) key;
        }

        @Override
        public Object fromId(String id) {
            // No check against a registry: it belongs to Mekanism, and
            // without the mod it does not exist. An identifier nobody can
            // resolve any more is still the truth about what the flow
            // meant.
            return id;
        }

        @Override
        public String nameOf(Object key) {
            return dev.devpanda.factorynetwork.compat.mekanism.Chemicals.nameOf((String) key);
        }

        @Override
        public List<?> resolve(Expr selector) {
            return dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(selector);
        }

        @Override
        public ResourceStore newStore() {
            return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.create();
        }
    };

    static {
        register(ITEM);
        register(FLUID);
        register(CHEMICAL);
    }

    /**
     * Registers a kind.
     *
     * @throws IllegalStateException if loading is over, the prefix belongs to
     *                               the language, or it is already taken
     */
    public static void register(ResourceKind kind) {
        if (frozen) {
            throw new IllegalStateException("Ressourcenarten werden beim Laden angemeldet, "
                    + "nicht später: " + kind.id());
        }
        String prefix = kind.prefix();
        if (RESERVED.contains(prefix)) {
            throw new IllegalStateException("„" + prefix + "“ gehört der Sprache und meint "
                    + "keine Ressourcenart.");
        }
        claim(BY_PREFIX, prefix, kind, "Das Präfix „" + prefix + "“");
        claim(BY_ID, kind.id(), kind, "Die Kennung " + kind.id());
        claim(BY_TAG, kind.tag(), kind, "Der Name auf der Platte „" + kind.tag() + "“");
        claim(BY_TAG, kind.selectionTag(), kind,
                "Der Name auf der Platte „" + kind.selectionTag() + "“");
        selectorPrefixes = null;
    }

    private static <K> void claim(Map<K, ResourceKind> where, K key, ResourceKind kind,
            String what) {
        ResourceKind taken = where.get(key);
        if (taken != null) {
            throw new IllegalStateException(what + " gehört schon " + taken.id()
                    + " — " + kind.id() + " kann es nicht bekommen.");
        }
        where.put(key, kind);
    }

    /**
     * Closes registration.
     *
     * <p>Called by the mod constructor once all mods are loaded. After that
     * the list is fixed, and that is the promise everything else rests on:
     * what a program means does not depend on when somebody registers
     * something.
     */
    public static void freeze() {
        frozen = true;
    }

    /** The kind for a prefix, or {@code null}. */
    public static ResourceKind byPrefix(String prefix) {
        return BY_PREFIX.get(prefix);
    }

    /** The kind for an identifier, or {@code null}. */
    public static ResourceKind byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /**
     * The kind for a name on disk, or {@code null}.
     *
     * <p>Both names of a kind lead here — the one for the single value and
     * the one for the selection. Which was meant is up to the caller.
     */
    public static ResourceKind byTag(String tag) {
        return BY_TAG.get(tag);
    }

    /** All kinds, in the order of their registration. */
    public static Collection<ResourceKind> all() {
        return List.copyOf(BY_PREFIX.values());
    }

    /** The prefixes a program may write. */
    public static Set<String> prefixes() {
        return Set.copyOf(BY_PREFIX.keySet());
    }

    /**
     * What the lexer may glue together as a selection.
     *
     * <p>The registered kinds plus {@code tag} and {@code fluidtag} — those
     * are not kinds but notations for two of them. {@code power} and
     * {@code all} are written without a colon and therefore do not belong.
     *
     * <p><b>This list once existed four times:</b> in the lexer, in the
     * parser, in {@code Selectors} and in the value model. Four copies with
     * three different answers to an unknown word — the lexer did not glue,
     * the parser made a tag out of it, {@code Selectors} returned
     * {@code null}. Now it lives here.
     */
    public static Set<String> selectorPrefixes() {
        Set<String> known = selectorPrefixes;
        if (known == null) {
            java.util.Set<String> all = new java.util.LinkedHashSet<>(BY_PREFIX.keySet());
            all.add("tag");
            all.add("fluidtag");
            known = java.util.Set.copyOf(all);
            selectorPrefixes = known;
        }
        return known;
    }

    /**
     * The written form behind a prefix, or {@code null}.
     *
     * <p>{@code null} means: this word is not a resource kind. The caller
     * reports that — it knows where in the program it stands.
     */
    public static Expr.Selector.Kind kindOf(String prefix) {
        return switch (prefix) {
            case "item" -> Expr.Selector.Kind.ITEM;
            case "fluid" -> Expr.Selector.Kind.FLUID;
            case "chemical" -> Expr.Selector.Kind.CHEMICAL;
            case "fluidtag" -> Expr.Selector.Kind.FLUIDTAG;
            case "tag" -> Expr.Selector.Kind.TAG;
            default -> BY_PREFIX.containsKey(prefix) ? Expr.Selector.Kind.CUSTOM : null;
        };
    }

    /**
     * The prefix somebody might have meant, or {@code null}.
     *
     * <p>The same yardstick as for connector names and events: a similar
     * name is the best information, the whole list the second best.
     */
    public static String suggest(String wanted) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : selectorPrefixes()) {
            int distance = dev.devpanda.factorynetwork.util.NameDistance
                    .between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return dev.devpanda.factorynetwork.util.NameDistance.isCloseEnough(wanted, bestDistance)
                ? best : null;
    }

    /** The known prefixes, sorted — for a message. */
    public static String known() {
        return String.join(", ", new java.util.TreeSet<>(selectorPrefixes()));
    }

    /**
     * What all built-in kinds have in common.
     *
     * <p>Only the data — what they <b>do</b> is stated with each one
     * individually. A third-party kind does not need this class; it
     * implements {@link ResourceKind} and nothing else.
     */
    private abstract static class Builtin implements ResourceKind {

        private final ResourceLocation id;
        private final String prefix;
        private final String plural;
        private final Class<?> type;
        private final String tag;
        private final String selectionTag;

        Builtin(String prefix, String plural, Class<?> type, String tag, String selectionTag) {
            this.id = ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, prefix);
            this.prefix = prefix;
            this.plural = plural;
            this.type = type;
            this.tag = tag;
            this.selectionTag = selectionTag;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public String prefix() {
            return prefix;
        }

        @Override
        public String plural() {
            return plural;
        }

        @Override
        public Class<?> type() {
            return type;
        }

        @Override
        public String tag() {
            return tag;
        }

        @Override
        public String selectionTag() {
            return selectionTag;
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
