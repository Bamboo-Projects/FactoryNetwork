package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ChemicalStore;
import dev.devpanda.factorynetwork.storage.CellView;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Weg vom Kern zu den Chemikalien — und die Rückfahrkarte.
 *
 * <p>Diese Klasse trägt selbst keinen Mekanism-Typ in einer Signatur; sie ist
 * die Tür, durch die der Kern geht. Was dahinter liegt, wird nur betreten,
 * wenn {@link FnMekanism#installed()} wahr ist — und dann lädt die JVM erst
 * die Klassen, die Mekanism brauchen.
 *
 * <p>Das ist dasselbe Muster wie bei GuideME und Jade, nur eine Schicht
 * tiefer: Dort entscheidet der Mod-Konstruktor, hier jede einzelne Frage.
 */
public final class ChemicalStores {

    private ChemicalStores() {
    }

    /**
     * Der Chemikalienspeicher für ein Netz.
     *
     * <p>Ohne Mekanism der, der nichts kann. Er ist kein Platzhalter, sondern
     * die richtige Antwort: In einem Pack ohne die Mod gibt es keine
     * Chemikalien, und ein Speicher, der so tut, wäre eine Lüge mit
     * Nebenwirkungen.
     */
    public static ChemicalStore create() {
        return FnMekanism.installed() ? new MekChemicalStore() : ChemicalStore.NONE;
    }

    /**
     * Öffnet eine Chemikalienzelle, oder {@code null} ohne Mekanism.
     *
     * <p>Die Fabrik, die {@code DriveBlockEntity} von außen bekommt.
     */
    public static CellView open(ItemStack cell) {
        return FnMekanism.installed() ? MekCells.open(cell) : null;
    }

    /**
     * Was in einer Zelle liegt, als Kennung auf Menge.
     *
     * <p>Für den Tooltip: Der Gegenstand gibt es immer, auch ohne Mekanism —
     * dann ist die Antwort leer, und der Tooltip sagt, woran es liegt.
     */
    public static Map<String, Long> read(ItemStack cell) {
        return FnMekanism.installed() ? MekCells.read(cell) : new LinkedHashMap<>();
    }
}
