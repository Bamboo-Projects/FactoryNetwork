package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.runtime.ResourceKind;
import dev.devpanda.factorynetwork.runtime.ResourceKinds;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Bestände eines Netzes, nach Ressourcenart.
 *
 * <p>Vorher trug jede Stelle, die einen Bestand braucht, drei Felder und drei
 * Signaturen: der Controller, {@code WorldHost}, {@code WorkerRuntime}. Ein
 * vierter Speicher hätte sie alle noch einmal angefasst — und das war der
 * eigentliche Preis, nicht die Klasse selbst.
 *
 * <p>Jetzt gibt es eine Stelle, an der steht, welche Art wo lagert. Wer einen
 * Bestand braucht, nimmt {@link #of(ResourceKind)}; wer eine bestimmte Art
 * meint und ihre eigenen Fragen stellt — Speicherbusse, Sortenplätze —, nimmt
 * die benannten Zugänge.
 *
 * <p><b>Warum hier und nicht in {@code runtime}:</b> Der Bestand gehört dem
 * Netz. Dass diese Klasse dafür {@link ResourceKind} aus {@code runtime}
 * kennt, ist der Preis; die Gegenrechnung wäre ein zweiter Aufzählungswert im
 * Netzpaket — also genau der Zwilling, den dieser Umbau abschafft.
 */
public final class NetworkStores {

    /**
     * Je Art ein Speicher, und welche es gibt, sagt die Registry.
     *
     * <p><b>Nach Identität und nicht nach Gleichheit.</b> Eine Art wird
     * einmal angemeldet und ist dann dieses eine Ding; zwei Einträge mit
     * demselben Präfix lehnt die Registry ab. Damit ist {@code ==} die
     * richtige Frage, und eine fremde Art, die {@code equals} eigenwillig
     * überschreibt, kann hier nichts verwechseln.
     */
    private final Map<ResourceKind, ResourceStore> byKind = new IdentityHashMap<>();

    private final NetworkStorage items;
    private final NetworkFluids fluids;

    public NetworkStores() {
        for (ResourceKind kind : ResourceKinds.all()) {
            byKind.put(kind, kind.newStore());
        }
        // Die beiden mit eigenen Fragen — Speicherbusse, Sortenplätze. Dass
        // ihre Art diesen Speicher liefert, steht in ResourceKinds und ist
        // die einzige Stelle, an der es so sein muss.
        this.items = (NetworkStorage) byKind.get(ResourceKinds.ITEM);
        this.fluids = (NetworkFluids) byKind.get(ResourceKinds.FLUID);
    }

    /**
     * Wo diese Art lagert.
     *
     * <p>Eine Art ohne Speicher gibt es nicht — {@link ResourceStore#NONE}
     * steht dort, wo die Mod dazu fehlt, und der antwortet ehrlich mit
     * nichts. Deshalb kommt hier nie {@code null} heraus.
     */
    public ResourceStore of(ResourceKind kind) {
        ResourceStore store = byKind.get(kind);
        return store == null ? ResourceStore.NONE : store;
    }

    /** Der Gegenstandsspeicher, mit seinen eigenen Fragen: Busse, Artenplätze. */
    public NetworkStorage items() {
        return items;
    }

    /** Der Flüssigkeitsspeicher, mit seinen eigenen Fragen: Sortenplätze. */
    public NetworkFluids fluids() {
        return fluids;
    }

    /**
     * Ohne Mekanism der Speicher, der nichts kann.
     *
     * <p>Das ist keine Notlösung: In einem Pack ohne die Mod gibt es keine
     * Chemikalien, und ein Speicher, der so täte, wäre eine Lüge mit
     * Nebenwirkungen.
     */
    public ResourceStore chemicals() {
        return of(ResourceKinds.CHEMICAL);
    }

    /**
     * Welche Laufwerke im Netz hängen.
     *
     * <p>Dieselbe Liste an alle: Ein Laufwerk trägt Zellen aller Arten, und
     * welche davon ein Speicher lesen kann, weiß er selbst. Vorher stand
     * dieser Aufruf dreimal untereinander, und beim vierten hätte ihn jemand
     * vergessen.
     */
    public void setDrives(List<DriveBlockEntity> found) {
        byKind.values().forEach(store -> store.setDrives(found));
    }
}
