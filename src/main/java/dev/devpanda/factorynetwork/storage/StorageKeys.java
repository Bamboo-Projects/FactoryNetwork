package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Was ins Netzlager darf — und was heute noch draußen bleibt.
 *
 * <p><b>Das Lager führt eine Kennung und eine Menge, mehr nicht.</b> Ein
 * verzaubertes Buch, ein benanntes Werkzeug, eine angeschlagene Spitzhacke,
 * ein gekoppeltes Ferngerät: Sie alle gingen als „ein Stück davon" hinein
 * und kämen nackt zurück. Nichts warnte, nichts fiel auf — bis jemand sein
 * Werkzeug wiederholte.
 *
 * <p><b>Das hier ist eine Notbremse, keine Lösung.</b> Die Lösung ist ein
 * Speicher, der Gegenstände statt Kennungen führt — wie AE2s
 * {@code AEItemKey}. Was das kostet, steht in
 * {@code docs/item-daten-im-lager.md}; es berührt vierundvierzig Dateien und
 * stellt Fragen ans Spiel, die nicht die Technik beantwortet.
 *
 * <p>Bis dahin gilt: lieber im Rucksack behalten als still verlieren.
 */
public final class StorageKeys {

    /**
     * Darf dieser Stapel ins Lager, ohne dass etwas verlorengeht?
     *
     * <p>Die Frage ist nicht „hat er NBT", sondern „unterscheidet er sich von
     * einem frisch gebauten". Genau das beantwortet
     * {@link ItemStack#isComponentsPatchEmpty()}: Was der Gegenstand von Haus
     * aus mitbringt, zählt nicht — nur, was jemand daran geändert hat.
     */
    public static boolean storable(ItemStack stack) {
        return stack.isComponentsPatchEmpty();
    }

    private StorageKeys() {
    }
}
