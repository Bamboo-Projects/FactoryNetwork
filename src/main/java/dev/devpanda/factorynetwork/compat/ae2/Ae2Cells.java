package dev.devpanda.factorynetwork.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.ItemStack;

/**
 * Liest eine AE2-Speicherzelle in unser Netz.
 *
 * <p><b>Wofür das da ist:</b> Wer ein AE2-Netz stehen hat und hierher
 * umzieht, soll seine Sachen mitnehmen können, ohne sie erst in tausend
 * Kisten zu leeren. Eine AE2-Zelle ist nichts anderes als eine Liste aus
 * Gegenstand und Menge — dieselbe Form, die unser Lager seit dem 28.08.
 * führt.
 *
 * <p><b>Vorher wäre genau das unmöglich gewesen.</b> AE2 speichert
 * {@code AEItemKey}: Gegenstand samt allem, was er trägt. Unser Lager kannte
 * bis dahin nur die Kennung — ein verzaubertes Buch aus einer AE2-Zelle wäre
 * beim Einlesen zu einem leeren geworden. Der Umbau macht diesen Weg erst
 * gangbar, und dieser Leser ist sein erster Nutznießer.
 *
 * <p><b>Eine Einbahnstraße.</b> Aus der Zelle heraus, ins Netz hinein — nie
 * zurück. Was das Netz nicht nimmt, bleibt in der Zelle: Ein Netz kann voll
 * sein, und ein Umzug, der dabei etwas verliert, ist kein Umzug.
 */
public final class Ae2Cells {

    /** Ist das eine Zelle, die AE2 kennt? */
    public static boolean isCell(ItemStack stack) {
        return FnAe2.installed() && !stack.isEmpty() && StorageCells.isCellHandled(stack);
    }

    /**
     * Schüttet den Inhalt einer AE2-Zelle in unser Netz.
     *
     * <p>Nur Gegenstände: Was AE2 sonst noch führt — Flüssigkeiten,
     * fremde Arten aus anderen Mods — bleibt liegen, statt beim Umziehen
     * still zu verschwinden.
     *
     * @return wie viele Stücke übergegangen sind
     */
    public static long drainInto(ItemStack cell, NetworkStorage storage) {
        if (!isCell(cell)) {
            return 0;
        }
        var inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) {
            return 0;
        }
        IActionSource source = IActionSource.empty();
        long moved = 0;
        // Über eine Kopie der Liste: Das Entnehmen ändert sie, und wer
        // dabei über das Original läuft, überspringt Posten.
        for (var entry : inventory.getAvailableStacks().keySet().stream().toList()) {
            if (!(entry instanceof AEItemKey found)) {
                continue;
            }
            long there = inventory.getAvailableStacks().get(found);
            if (there <= 0) {
                continue;
            }
            ItemKey key = ItemKey.of(found.toStack());
            if (key == null) {
                continue;
            }
            // Erst fragen, wie viel hineinpasst, dann genau so viel
            // herausnehmen. Umgekehrt läge der Rest auf dem Boden.
            long fits = there - storage.insert(key, there);
            if (fits <= 0) {
                continue;
            }
            long taken = inventory.extract(found, fits, Actionable.MODULATE, source);
            if (taken < fits) {
                // Die Zelle gab weniger her als angekündigt — den Überschuss
                // zurück ins Netz legen, statt ihn zu erfinden.
                storage.extract(key, fits - taken);
            }
            moved += taken;
        }
        return moved;
    }

    private Ae2Cells() {
    }
}
