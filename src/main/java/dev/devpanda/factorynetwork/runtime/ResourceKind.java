package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.resources.ResourceLocation;

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
 * <p><b>Und die Liste der Arten ist offen.</b> Seit dem 26.08. ist
 * entschieden, dass fremde Mods die Sprache erweitern dürfen: Wer eine eigene
 * Art mitbringt, erfüllt diese Schnittstelle und meldet sie bei
 * {@link ResourceKinds} an. Der Kern muss dafür nicht angefasst werden. Die
 * Begründung und das, was daran nicht umkehrbar ist, stehen in
 * {@code entscheidungen.md}, „Fremde Mods dürfen die Sprache erweitern".
 *
 * <p><b>Der Schlüssel ist ein {@code Object}.</b> Ein gemeinsamer Obertyp
 * gäbe es nur, wenn alle Arten aus derselben Hand kämen — ein Gegenstand ist
 * ein {@code Item}, eine Chemikalie ist ein {@link String}, weil eine Signatur
 * mit einem Mekanism-Typ die Klasse beim Laden auflösen würde. Welche Form zu
 * dieser Art gehört, sagt {@link #type()}, und der Wert prüft sie beim
 * Anlegen.
 *
 * <p><b>Eine Art ist ein einziges Ding.</b> Angemeldet wird sie einmal, und
 * Gleichheit ist Identität — {@code ==} statt {@code equals}. Zwei Einträge
 * mit demselben Präfix lehnt die Registry ab, statt einen davon zu verdecken.
 */
public interface ResourceKind {

    /** Die Kennung dieser Art, etwa {@code arsnouveau:source}. */
    ResourceLocation id();

    /**
     * Was vor dem Doppelpunkt steht — und wie der Posten danach gefragt wird.
     *
     * <p>Dieselbe Silbe an beiden Stellen, und das ist keine Sparsamkeit:
     * Wer {@code chemical:mekanism/hydrogen} schreibt, liest den Posten mit
     * {@code it.chemical} ab. Zwei Wörter für dieselbe Art wären zwei Dinge
     * zum Merken.
     */
    String prefix();

    /** Wie eine Auswahl dieser Art im Protokoll gezählt wird. */
    String plural();

    /** Woran eine Ressource dieser Art zu erkennen ist. */
    Class<?> type();

    /**
     * Wie diese Art auf der Platte heißt.
     *
     * <p>Bei den eingebauten drei ist der Name <b>unregelmäßig</b>, und das
     * bleibt so: {@code item} gegen {@code sel}, aber {@code chem} gegen
     * {@code chemsel} — gewachsen, nicht entworfen. Ein wartender Ablauf
     * liegt mit diesen Namen in der Welt; sie geradezuziehen hieße, alten
     * Welten ihre Abläufe verlieren zu lassen. Festgehalten in
     * {@code ValueCodecFormatTest}.
     *
     * <p>Eine fremde Art wählt ihren eigenen. Er muss über Neustarts hinweg
     * derselbe bleiben und darf keinem anderen gleichen — beides prüft
     * {@link ResourceKinds}.
     */
    String tag();

    /** Dasselbe für eine Auswahl. */
    String selectionTag();

    /** Die Kennung einer Ressource, wie sie auf der Platte steht. */
    String idOf(Object key);

    /**
     * Die Ressource hinter einer Kennung.
     *
     * <p>Ob dabei gegen eine Registry geprüft wird, entscheidet die Art.
     * Gegenstände und Flüssigkeiten scheitern mit Meldung, wenn es sie nicht
     * mehr gibt: Eine Variable, mit der weitergerechnet wird, darf sich nicht
     * heimlich in etwas anderes verwandeln. Chemikalien tun es nicht — ihre
     * Registry gehört Mekanism, und ohne die Mod gibt es sie nicht.
     */
    Object fromId(String id);

    /** Wie eine einzelne Ressource im Protokoll erscheint. */
    String nameOf(Object key);

    /**
     * Worauf sich ein Auswahlausdruck dieser Art auflöst.
     *
     * <p>Die Auflöser bleiben getrennt — sie sind keine Zwillinge, sondern
     * verschiedene Registries mit verschiedenen Zwischenspeichern. Was
     * zusammenkommt, ist allein die Frage, welchen davon eine Stelle braucht.
     */
    List<?> resolve(Expr selector);

    /**
     * Der Speicher, in dem diese Art im Netz liegt.
     *
     * <p><b>Standardmäßig keiner.</b> Eine Art darf beweglich sein, ohne
     * lagerbar zu sein — und {@link ResourceStore#NONE} ist die ehrliche
     * Antwort darauf: Er nimmt nichts an und gibt nichts her. Wer lagern
     * will, liefert hier je Netz einen neuen.
     */
    default ResourceStore newStore() {
        return ResourceStore.NONE;
    }

    /**
     * Die Art hinter einer Schreibweise, oder {@code null}.
     *
     * <p>{@code null} heißt „keine Ressourcenart" und nicht „Gegenstände":
     * {@code power} und {@code all} tragen keine Sorte, und wer sie als
     * Gegenstände läse, bewegte bei {@code power} das Falsche.
     */
    static ResourceKind of(Expr.Selector.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ResourceKinds.ITEM;
            case FLUID, FLUIDTAG -> ResourceKinds.FLUID;
            case CHEMICAL -> ResourceKinds.CHEMICAL;
            case POWER, ALL -> null;
        };
    }

    /** Dieselbe Frage für eine Auswahl, die noch als Text dasteht. */
    static ResourceKind of(Value.Request.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> ResourceKinds.ITEM;
            case FLUID, FLUIDTAG -> ResourceKinds.FLUID;
            case CHEMICAL -> ResourceKinds.CHEMICAL;
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
     * hier.
     */
    static ResourceKind orItems(Expr.Selector.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ResourceKinds.ITEM : kind;
    }

    /** Dasselbe für eine Auswahl, die noch als Text dasteht. */
    static ResourceKind orItems(Value.Request.Kind written) {
        ResourceKind kind = of(written);
        return kind == null ? ResourceKinds.ITEM : kind;
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
    static ResourceKind of(Value value) {
        return switch (value) {
            case Value.Resource resource -> resource.kind();
            case Value.Selection selection -> selection.kind();
            case Value.Request request -> of(request.kind());
            case null, default -> null;
        };
    }
}
