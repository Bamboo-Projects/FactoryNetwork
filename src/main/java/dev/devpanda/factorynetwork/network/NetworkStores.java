package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.runtime.ResourceKind;

import java.util.EnumMap;
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

    private final NetworkStorage items = new NetworkStorage();
    private final NetworkFluids fluids = new NetworkFluids();

    /**
     * Ohne Mekanism der Speicher, der nichts kann.
     *
     * <p>Das ist keine Notlösung: In einem Pack ohne die Mod gibt es keine
     * Chemikalien, und ein Speicher, der so täte, wäre eine Lüge mit
     * Nebenwirkungen.
     */
    private final ResourceStore chemicals =
            dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.create();

    private final Map<ResourceKind, ResourceStore> byKind =
            new EnumMap<>(ResourceKind.class);

    public NetworkStores() {
        byKind.put(ResourceKind.ITEM, items);
        byKind.put(ResourceKind.FLUID, fluids);
        byKind.put(ResourceKind.CHEMICAL, chemicals);
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

    public ResourceStore chemicals() {
        return chemicals;
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
