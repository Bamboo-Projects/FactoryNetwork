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
 * Die Ressourcenarten, die es gibt — die eingebauten und die angemeldeten.
 *
 * <p>Am 26.08. entschieden: Fremde Mods dürfen die Sprache erweitern. Diese
 * Klasse ist die Tür dazu. Wer eine eigene Art mitbringt, erfüllt
 * {@link ResourceKind} und ruft im Mod-Konstruktor {@link #register}.
 *
 * <p><b>Angemeldet wird beim Laden und danach nie wieder.</b> Genau das ist
 * die Unumkehrbarkeit, die mit der Entscheidung angenommen wurde: Ein
 * Programm mit {@code source:mana} läuft in einer Welt, deren Kern das Wort
 * nie gesehen hat, und wer die Art später zurückzöge, bräche fremde
 * Programme. {@link #freeze()} macht daraus eine Zusage statt einer Bitte.
 *
 * <p><b>Ein Präfix gehört einer Art.</b> Zwei Einträge mit demselben Wort
 * lehnt die Registry ab, statt einen davon zu verdecken — welcher gewönne,
 * hinge an der Ladereihenfolge, und die ist keine Erklärung, die ein Spieler
 * lesen kann.
 */
public final class ResourceKinds {

    /**
     * Was der Sprache gehört und keine Art meint.
     *
     * <p>{@code tag} und {@code fluidtag} sind Schreibweisen für Arten, die
     * es schon gibt. {@code power} und {@code all} tragen gar keine Sorte.
     * Wer eines davon belegte, machte bestehende Programme mehrdeutig.
     */
    public static final Set<String> RESERVED = Set.of("tag", "fluidtag", "power", "all");

    /** Reihenfolge der Anmeldung — sie entscheidet nichts, liest sich aber besser. */
    private static final Map<String, ResourceKind> BY_PREFIX = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceKind> BY_ID = new LinkedHashMap<>();
    private static final Map<String, ResourceKind> BY_TAG = new LinkedHashMap<>();

    private static boolean frozen;

    /**
     * Die Liste für den Lexer, einmal gebaut.
     *
     * <p>Er fragt sie bei <b>jedem</b> Wort im Quelltext. Sie dort jedes Mal
     * neu zusammenzusetzen wäre eine Allokation je Token — und die Liste
     * ändert sich nur beim Anmelden.
     */
    private static Set<String> selectorPrefixes;

    private ResourceKinds() {
    }

    /** Gegenstandsarten. Ein {@code tag:} löst sich hierauf auf. */
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

    /** Flüssigkeitssorten, gemessen in Millibucket. */
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
     * Chemikalien aus Mekanism, als Kennung.
     *
     * <p>Ein Text und kein Mekanism-Typ: Eine Signatur mit {@code Chemical}
     * darin ließe die Klasse ohne die Mod nicht mehr laden. Ohne Mekanism
     * trifft eine Auswahl nichts und der Speicher kann nichts — beides ist
     * die Wahrheit über ein solches Pack und keine Notlösung.
     */
    public static final ResourceKind CHEMICAL = new Builtin(
            "chemical", "Chemikalien", String.class, "chem", "chemsel") {

        @Override
        public String idOf(Object key) {
            return (String) key;
        }

        @Override
        public Object fromId(String id) {
            // Ohne Prüfung gegen eine Registry: Die gehört Mekanism, und ohne
            // die Mod gibt es sie nicht. Eine Kennung, die niemand mehr
            // auflösen kann, ist immer noch die Wahrheit darüber, was der
            // Ablauf gemeint hat.
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
     * Meldet eine Art an.
     *
     * @throws IllegalStateException wenn das Laden vorbei ist, das Präfix der
     *                               Sprache gehört oder es schon vergeben ist
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
     * Schließt die Anmeldung.
     *
     * <p>Ruft der Mod-Konstruktor, nachdem alle Mods geladen sind. Danach ist
     * die Liste fest, und das ist die Zusage, auf der alles andere ruht: Was
     * ein Programm bedeutet, hängt nicht davon ab, wann jemand etwas anmeldet.
     */
    public static void freeze() {
        frozen = true;
    }

    /** Die Art zu einem Präfix, oder {@code null}. */
    public static ResourceKind byPrefix(String prefix) {
        return BY_PREFIX.get(prefix);
    }

    /** Die Art zu einer Kennung, oder {@code null}. */
    public static ResourceKind byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /**
     * Die Art zu einem Namen auf der Platte, oder {@code null}.
     *
     * <p>Beide Namen einer Art führen hierher — der für den Einzelwert und
     * der für die Auswahl. Welcher gemeint war, entscheidet der Aufrufer.
     */
    public static ResourceKind byTag(String tag) {
        return BY_TAG.get(tag);
    }

    /** Alle Arten, in der Reihenfolge ihrer Anmeldung. */
    public static Collection<ResourceKind> all() {
        return List.copyOf(BY_PREFIX.values());
    }

    /** Die Präfixe, die ein Programm schreiben darf. */
    public static Set<String> prefixes() {
        return Set.copyOf(BY_PREFIX.keySet());
    }

    /**
     * Was der Lexer als Auswahl zusammenkleben darf.
     *
     * <p>Die angemeldeten Arten und dazu {@code tag} und {@code fluidtag} —
     * die sind keine Arten, sondern Schreibweisen für zwei davon.
     * {@code power} und {@code all} stehen ohne Doppelpunkt und gehören
     * deshalb nicht dazu.
     *
     * <p><b>Diese Liste stand einmal viermal da:</b> im Lexer, im Parser, in
     * {@code Selectors} und im Wertemodell. Vier Kopien mit drei
     * verschiedenen Antworten auf ein unbekanntes Wort — der Lexer klebte
     * nicht, der Parser machte einen Tag daraus, {@code Selectors} gab
     * {@code null}. Jetzt steht sie hier.
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
     * Die Schreibweise hinter einem Präfix, oder {@code null}.
     *
     * <p>{@code null} heißt: Dieses Wort ist keine Ressourcenart. Der
     * Aufrufer meldet das — er weiß, wo im Programm es steht.
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
     * Das Präfix, das jemand gemeint haben könnte, oder {@code null}.
     *
     * <p>Derselbe Maßstab wie bei Connectornamen und Ereignissen: Ein
     * ähnlicher Name ist die beste Auskunft, die ganze Liste die zweitbeste.
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

    /** Die bekannten Präfixe, sortiert — für eine Meldung. */
    public static String known() {
        return String.join(", ", new java.util.TreeSet<>(selectorPrefixes()));
    }

    /**
     * Was alle eingebauten Arten gemeinsam haben.
     *
     * <p>Nur die Angaben — was sie <b>tun</b>, steht bei jeder einzeln. Eine
     * fremde Art braucht diese Klasse nicht; sie erfüllt
     * {@link ResourceKind} und sonst nichts.
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
