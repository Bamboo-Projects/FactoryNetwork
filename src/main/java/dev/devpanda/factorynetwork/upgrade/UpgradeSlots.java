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
 * The slots of a device or block.
 *
 * <p><b>The number of slots is fixed and the real decision.</b> Whoever wants
 * everything must choose what to leave out — a container that grows would rob
 * the upgrade of its price.
 *
 * <p>It only accepts upgrades. A slot that everything fits into is a backpack,
 * not an upgrade slot.
 *
 * <p><b>On counting:</b> a slot holds a stack, and every item in it counts.
 * Three range cards in one slot thus act like three cards — the consequence of
 * identical cards adding up. If that feels wrong in the game, the answer is a
 * stack limit on the item, not a second calculation here.
 */
public class UpgradeSlots {

    private final NonNullList<ItemStack> contents;

    public UpgradeSlots(int size) {
        this.contents = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public int size() {
        return contents.size();
    }

    /** Does this stack fit into a slot? Empty always fits. */
    public boolean accepts(ItemStack stack) {
        return stack.isEmpty() || UpgradeItem.upgradeOf(stack) != null;
    }

    public ItemStack get(int slot) {
        return contents.get(slot);
    }

    /**
     * Places a stack into a slot.
     *
     * @throws IllegalArgumentException if it is not an upgrade — whoever calls
     *         this did not ask {@link #accepts}.
     */
    public void set(int slot, ItemStack stack) {
        if (!accepts(stack)) {
            throw new IllegalArgumentException("kein Ausbau: " + stack.getItem());
        }
        contents.set(slot, stack);
    }

    /**
     * What this loadout can do and how high its values are.
     *
     * <p>The counting itself lives in {@link Loadout#ofCounts} and is tested
     * there. Here only what lies in the slots is gathered: this class holds
     * {@link ItemStack} and therefore cannot be touched in any ordinary test.
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

    /** The raw contents — for container windows and for dropping. */
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
