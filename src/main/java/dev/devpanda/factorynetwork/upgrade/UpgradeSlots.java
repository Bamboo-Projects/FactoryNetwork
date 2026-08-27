package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.item.UpgradeItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die Steckplätze eines Geräts oder Blocks.
 *
 * <p><b>Die Zahl der Plätze ist fest und die eigentliche Entscheidung.</b>
 * Wer alles will, muss wählen, was er weglässt — ein Behälter, der wächst,
 * nähme dem Ausbau seinen Preis.
 *
 * <p>Er nimmt nur Ausbauten an. Ein Platz, in den alles passt, ist ein
 * Rucksack und kein Steckplatz.
 *
 * <p><b>Zur Zählung:</b> Ein Platz hält einen Stapel, und jedes Stück darin
 * zählt. Drei Reichweitenkarten auf einem Platz wirken damit wie drei Karten
 * — die Folge daraus, dass gleiche Karten sich addieren. Wenn das im Spiel
 * falsch wirkt, ist die Antwort eine Stapelgrenze am Gegenstand und nicht
 * eine zweite Rechnung hier.
 */
public class UpgradeSlots {

    private final NonNullList<ItemStack> contents;

    public UpgradeSlots(int size) {
        this.contents = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public int size() {
        return contents.size();
    }

    /** Passt dieser Stapel in einen Steckplatz? Leer passt immer. */
    public boolean accepts(ItemStack stack) {
        return stack.isEmpty() || UpgradeItem.upgradeOf(stack) != null;
    }

    public ItemStack get(int slot) {
        return contents.get(slot);
    }

    /**
     * Legt einen Stapel in einen Platz.
     *
     * @throws IllegalArgumentException wenn es kein Ausbau ist — wer das
     *         aufruft, hat {@link #accepts} nicht gefragt.
     */
    public void set(int slot, ItemStack stack) {
        if (!accepts(stack)) {
            throw new IllegalArgumentException("kein Ausbau: " + stack.getItem());
        }
        contents.set(slot, stack);
    }

    /**
     * Was diese Bestückung kann und wie hoch ihre Werte sind.
     *
     * <p>Die Zählung selbst steht in {@link Loadout#ofCounts} und wird dort
     * geprüft. Hier wird nur gesammelt, was in den Plätzen liegt: Diese
     * Klasse hält {@link ItemStack} und lässt sich deshalb in keinem
     * gewöhnlichen Test anfassen.
     */
    public Loadout loadout() {
        Map<Upgrade, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : contents) {
            Upgrade upgrade = UpgradeItem.upgradeOf(stack);
            if (upgrade != null) {
                counts.merge(upgrade, stack.getCount(), Integer::sum);
            }
        }
        return Loadout.ofCounts(counts);
    }

    /** Der rohe Inhalt — für Behälterfenster und zum Fallenlassen. */
    public NonNullList<ItemStack> contents() {
        return contents;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, contents, registries);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
    }
}
