package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.storage.CellFormat;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import dev.devpanda.factorynetwork.storage.ChemicalCellItem;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chemikalienzellen, mit Mekanism-Typen.
 *
 * <p><b>Die Rechnung stand schon da.</b> {@code CellInventory} und
 * {@code CellFormat} sind seit den Flüssigkeiten offen für den Typ: Was sich
 * unterscheidet, ist die Registry und die Größe, nicht eine einzige Zeile der
 * Rechnung mit Sorten und Mengen. Hier steht deshalb kaum mehr als das
 * Format — und das ist das Ergebnis einer Entscheidung von vor zwei Tagen,
 * nicht Zufall.
 *
 * <p>Diese Klasse wird nur betreten, wenn Mekanism da ist. Sie darf deshalb
 * heißen, wie sie will, und Mekanism kennen.
 */
final class MekCells {

    /**
     * Wie der Inhalt einer Chemikalienzelle im Gegenstand steht.
     *
     * <p>Eigene Feldnamen, damit eine Zelle nie mit einer Flüssigkeitszelle
     * verwechselt wird: Beide zählen in Millibucket, und ein vertauschtes
     * Format läse Wasser als Wasserstoff.
     */
    static final CellFormat<Chemical> CHEMICALS = new CellFormat<>(
            mekanism.api.MekanismAPI.CHEMICAL_REGISTRY,
            "ChemicalCell", "Chemical", "Amount");

    private MekCells() {
    }

    /** Eine geöffnete Zelle, oder {@code null}, wenn dort keine steckt. */
    static CellView open(ItemStack cell) {
        if (ChemicalCellItem.tierOf(cell) == null) {
            return null;
        }
        return CellInventory.of(cell, ChemicalCellItem.tierOf(cell), CHEMICALS);
    }

    /** Was in der Zelle steht, als Kennung auf Menge. */
    static Map<String, Long> read(ItemStack cell) {
        Map<String, Long> found = new LinkedHashMap<>();
        CHEMICALS.read(cell).forEach((chemical, amount) -> {
            ResourceLocation id = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            if (id != null && amount > 0) {
                found.put(id.toString(), amount);
            }
        });
        return found;
    }

    /** Die Chemikalie zu einer Kennung, oder {@code null}. */
    static Chemical chemical(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(key)) {
            return null;
        }
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(key);
    }

    /** Und zurück. */
    static String idOf(Chemical chemical) {
        ResourceLocation id = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
        return id == null ? "" : id.toString();
    }
}
