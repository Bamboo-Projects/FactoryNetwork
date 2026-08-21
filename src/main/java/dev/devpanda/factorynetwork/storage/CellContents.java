package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Was in einer Gegenstandszelle liegt.
 *
 * <p><b>Der Inhalt steckt im Gegenstand, nicht im Laufwerk.</b> Das ist der
 * Grund, warum eine Zelle etwas wert ist: Man zieht sie heraus, trägt sie weg
 * und steckt sie anderswo hinein — der Bestand kommt mit. Läge er im
 * Laufwerk, wäre die Zelle nur ein Schlüssel und kein Speicher.
 *
 * <p>Gerechnet wird schlüsselbasiert, wie im ganzen Netz: eine Abbildung von
 * Art auf Menge. Eine Liste von Stapeln wäre bei sechzig Arten schon eine
 * lineare Suche je Zugriff, und davon gibt es Dutzende je Tick.
 *
 * <p>Die Arbeit selbst steht in {@link CellFormat}, denn Flüssigkeiten liegen
 * gleich. Diese Klasse ist der bequeme Zugang für den häufigeren Fall.
 */
public final class CellContents {

    private CellContents() {
    }

    public static Map<Item, Long> read(ItemStack cell) {
        return CellFormat.ITEMS.read(cell);
    }

    public static void write(ItemStack cell, Map<Item, Long> contents) {
        CellFormat.ITEMS.write(cell, contents);
    }

    /** Wie viel insgesamt darin liegt. */
    public static long total(Map<Item, Long> contents) {
        return CellFormat.total(contents);
    }
}
