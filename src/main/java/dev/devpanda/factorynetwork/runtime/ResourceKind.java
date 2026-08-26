package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Was für eine Ressource ein Wert meint.
 *
 * <p>Vorher stand diese Auskunft nirgends: Es gab drei Wertepaare —
 * {@code ItemValue}/{@code Selection}, {@code FluidValue}/{@code FluidSelection},
 * {@code ChemicalValue}/{@code ChemicalSelection} —, und jede Stelle, die
 * Werte behandelt, trug alle drei nebeneinander. Gemessen waren das zehn
 * Stellen für eine einzige neue Art, neun davon Kopien voneinander
 * (siehe {@code ressourcenarten.md}, Abschnitt 2). Kopien laufen auseinander,
 * und genau das war passiert: {@code move} entschied den Weg an der Art und
 * kannte dabei die aufgelöste Flüssigkeitsauswahl, die aufgelöste
 * Chemikalienauswahl aber nicht.
 *
 * <p>Jetzt trägt der Wert seine Art als Feld, und was je Art verschieden ist,
 * steht hier — an einer Stelle und vollzählig.
 *
 * <p><b>Noch ein Aufzählungswert und keine Registry.</b> Ob fremde Mods eigene
 * Arten anmelden dürfen, ist eine Haltungsfrage und in
 * {@code ressourcenarten.md}, Abschnitt 6 noch offen. Dieser Schritt ist von
 * ihr unabhängig richtig: Er macht den Code kleiner, egal wie sie ausgeht.
 *
 * <p><b>Eine Chemikalie ist ein Text.</b> Das ist die Naht, an der die
 * Mekanism-Anbindung hängt — eine Signatur mit einem Mekanism-Typ ließe sich
 * ohne die Mod nicht mehr laden. Deshalb ist der Träger einer Ressource hier
 * {@code Object} und nicht ein gemeinsamer Obertyp: Einen solchen gäbe es nur,
 * wenn alle drei aus derselben Hand kämen, und {@link String} kommt aus keiner.
 */
public enum ResourceKind {

    /** Gegenstandsarten. Ein {@code tag:} löst sich hierauf auf. */
    ITEM("item", "Arten", Item.class, "item", "sel"),

    /** Flüssigkeitssorten, gemessen in Millibucket. */
    FLUID("fluid", "Flüssigkeiten", Fluid.class, "fluid", "fluidsel"),

    /** Chemikalien aus Mekanism, als Kennung. */
    CHEMICAL("chemical", "Chemikalien", String.class, "chem", "chemsel");

    private final String prefix;
    private final String plural;
    private final Class<?> type;
    private final String tag;
    private final String selectionTag;

    ResourceKind(String prefix, String plural, Class<?> type, String tag, String selectionTag) {
        this.prefix = prefix;
        this.plural = plural;
        this.type = type;
        this.tag = tag;
        this.selectionTag = selectionTag;
    }

    /**
     * Was vor dem Doppelpunkt steht — und wie der Posten danach gefragt wird.
     *
     * <p>Dieselbe Silbe an beiden Stellen, und das ist keine Sparsamkeit:
     * Wer {@code chemical:mekanism/hydrogen} schreibt, liest den Posten mit
     * {@code it.chemical} ab. Zwei Wörter für dieselbe Art wären zwei Dinge
     * zum Merken.
     */
    public String prefix() {
        return prefix;
    }

    /** Wie eine Auswahl dieser Art im Protokoll gezählt wird. */
    public String plural() {
        return plural;
    }

    /** Woran eine Ressource dieser Art zu erkennen ist. */
    public Class<?> type() {
        return type;
    }

    /**
     * Wie diese Art auf der Platte heißt.
     *
     * <p><b>Unregelmäßig, und das bleibt so.</b> {@code item} gegen
     * {@code sel}, aber {@code chem} gegen {@code chemsel} — gewachsen, nicht
     * entworfen. Ein wartender Ablauf liegt mit diesen Namen in der Welt;
     * sie geradezuziehen hieße, alte Welten ihre Abläufe verlieren zu lassen.
     * Festgehalten in {@code ValueCodecFormatTest}.
     */
    public String tag() {
        return tag;
    }

    /** Dasselbe für eine Auswahl. */
    public String selectionTag() {
        return selectionTag;
    }

    /**
     * Die Kennung einer Ressource, wie sie auf der Platte steht.
     *
     * @throws IllegalArgumentException wenn die Ressource nicht zur Art passt
     */
    public String idOf(Object resource) {
        return switch (this) {
            case ITEM -> BuiltInRegistries.ITEM.getKey(cast(resource, Item.class)).toString();
            case FLUID -> BuiltInRegistries.FLUID.getKey(cast(resource, Fluid.class)).toString();
            case CHEMICAL -> cast(resource, String.class);
        };
    }

    /**
     * Die Ressource hinter einer Kennung.
     *
     * <p>Gegenstände und Flüssigkeiten werden gegen die Registry geprüft und
     * scheitern mit Meldung, wenn es sie nicht mehr gibt: Eine Variable, mit
     * der weitergerechnet wird, darf sich nicht heimlich in etwas anderes
     * verwandeln.
     *
     * <p><b>Chemikalien nicht.</b> Ihre Registry gehört Mekanism, und ohne
     * die Mod gibt es sie nicht. Eine Kennung, die niemand mehr auflösen
     * kann, ist immer noch die Wahrheit darüber, was der Ablauf gemeint hat.
     */
    public Object fromId(String id) {
        return switch (this) {
            case ITEM -> {
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                    throw new ScriptError("Den Gegenstand " + id + " gibt es nicht mehr.",
                            "Der Ablauf hielt ihn in einer Variablen fest. Wurde eine Mod "
                                    + "aus dem Pack genommen?");
                }
                yield BuiltInRegistries.ITEM.get(key);
            }
            case FLUID -> {
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
                    throw new ScriptError("Die Flüssigkeit " + id + " gibt es nicht mehr.",
                            "Der Ablauf hielt sie in einer Variablen fest. Wurde eine Mod "
                                    + "aus dem Pack genommen?");
                }
                yield BuiltInRegistries.FLUID.get(key);
            }
            case CHEMICAL -> id;
        };
    }

    /**
     * Wie eine einzelne Ressource im Protokoll erscheint.
     *
     * <p>Bei einer Chemikalie über {@code Chemicals.nameOf}, damit dort
     * „Wasserstoff" steht und nicht die Kennung. Fehlt Mekanism, steht die
     * Kennung da — erfunden wird nichts.
     */
    public String nameOf(Object resource) {
        return switch (this) {
            case ITEM -> cast(resource, Item.class).toString();
            case FLUID -> idOf(resource);
            case CHEMICAL -> dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                    .nameOf(cast(resource, String.class));
        };
    }

    /**
     * Worauf sich ein Auswahlausdruck dieser Art auflöst.
     *
     * <p>Die drei Auflöser bleiben getrennt — sie sind keine Zwillinge,
     * sondern drei verschiedene Registries mit drei verschiedenen
     * Zwischenspeichern. Was hier zusammenkommt, ist allein die Frage,
     * welchen davon eine Stelle braucht.
     */
    public List<?> resolve(Expr expr) {
        return switch (this) {
            case ITEM -> ItemSelection.resolve(expr);
            case FLUID -> FluidSelection.resolve(expr);
            case CHEMICAL -> dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(expr);
        };
    }

    /**
     * Die Art hinter einer Schreibweise, oder {@code null}.
     *
     * <p>{@code null} heißt „keine Ressourcenart" und nicht „Gegenstände":
     * {@code power} und {@code all} tragen keine Sorte, und wer sie als
     * Gegenstände läse, bewegte bei {@code power} das Falsche.
     */
    public static ResourceKind of(Expr.Selector.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ITEM;
            case FLUID, FLUIDTAG -> FLUID;
            case CHEMICAL -> CHEMICAL;
            case POWER, ALL -> null;
        };
    }

    /** Dieselbe Frage für eine Auswahl, die noch als Text dasteht. */
    public static ResourceKind of(Value.Request.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ITEM;
            case FLUID, FLUIDTAG -> FLUID;
            case CHEMICAL -> CHEMICAL;
            case ALL, UNKNOWN -> null;
        };
    }

    /**
     * Dieselbe Frage, wo eine Antwort gebraucht wird und {@code null} keine
     * wäre.
     *
     * <p>Ohne Art sind <b>Gegenstände</b> gemeint: {@code all} sagt das
     * ausdrücklich, ein Worker ohne Filter tut es seit jeher, und eine
     * Schreibweise, die niemand kennt, fällt beim Auflösen auf und nicht
     * hier. Gebraucht überall dort, wo als Nächstes aufgelöst wird.
     */
    public static ResourceKind orItems(Expr.Selector.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ITEM : kind;
    }

    /** Dasselbe für eine Auswahl, die noch als Text dasteht. */
    public static ResourceKind orItems(Value.Request.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ITEM : kind;
    }

    /**
     * Die Art, für die ein Wert steht, oder {@code null}.
     *
     * <p><b>Die Stelle, an der {@code move} seinen Weg wählt.</b> Vorher gab
     * es dafür zwei Fragen — eine für Flüssigkeiten, eine für Chemikalien —,
     * und jede kannte einen anderen Ausschnitt der Werte: Die eine hatte den
     * Nachtrag für die aufgelöste Auswahl bekommen, die andere nicht. Eine
     * Chemikalie aus einer Schleife lief damit in die
     * Gegenstandsauflösung, traf dort nichts, und keine Auswahl heißt dort
     * <i>alles</i>.
     */
    public static ResourceKind of(Value value) {
        return switch (value) {
            case Value.Resource resource -> resource.kind();
            case Value.Selection selection -> selection.kind();
            case Value.Request request -> of(request.kind());
            case null, default -> null;
        };
    }

    private <T> T cast(Object resource, Class<T> expected) {
        if (!expected.isInstance(resource)) {
            throw new IllegalArgumentException("Eine Ressource der Art " + this
                    + " ist kein " + (resource == null ? "nichts" : resource.getClass()) + ".");
        }
        return expected.cast(resource);
    }
}
