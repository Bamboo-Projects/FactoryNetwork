package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Eine offene Zelle im Laufwerk.
 *
 * <p>Das Laufwerk hält seine Zellen im Speicher und schreibt erst zurück,
 * wenn es sein muss (siehe {@code DriveBlockEntity.inventories}). Drei Fragen
 * braucht es dafür, und nur diese drei: <b>zu welchem Gegenstand gehörst du,
 * steckt dort überhaupt eine Zelle, und schreib dich zurück.</b>
 *
 * <p>Deshalb steht das hier und nicht als drei gleichlautende Methoden in
 * jeder Zellenart: Das Laufwerk verwaltet Gegenstands-, Flüssigkeits- und
 * Energiezellen mit derselben Rechnung, und was sie unterscheidet, ist ihr
 * Inhalt — nicht ihre Verwaltung.
 */
public interface CellView {

    /** Der Gegenstand, zu dem diese Sicht gehört. */
    ItemStack stack();

    /** Steckt dort wirklich eine Zelle dieser Art? */
    boolean isValid();

    /** Schreibt den Inhalt in den Gegenstand zurück. */
    void flush();
}
