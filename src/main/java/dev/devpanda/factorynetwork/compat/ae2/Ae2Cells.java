package dev.devpanda.factorynetwork.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.ItemStack;

/**
 * Reads an AE2 storage cell into our network.
 *
 * <p><b>What this is for:</b> anyone who has an AE2 network standing and moves
 * over here should be able to bring their things along, without first emptying
 * them into a thousand chests. An AE2 cell is nothing more than a list of item
 * and amount — the same shape our storage has held since 28 Aug.
 *
 * <p><b>Before, this very thing would have been impossible.</b> AE2 stores
 * {@code AEItemKey}: an item together with everything it carries. Until then
 * our storage knew only the identifier — an enchanted book from an AE2 cell
 * would have turned into a blank one on read-in. The rework is what makes this
 * path viable in the first place, and this reader is its first beneficiary.
 *
 * <p><b>A one-way street.</b> Out of the cell, into the network — never back.
 * Whatever the network won't take stays in the cell: a network can be full,
 * and a move that loses something along the way is no move at all.
 */
public final class Ae2Cells {

    /** Is this a cell that AE2 recognizes? */
    public static boolean isCell(ItemStack stack) {
        return FnAe2.installed() && !stack.isEmpty() && StorageCells.isCellHandled(stack);
    }

    /**
     * Pours the contents of an AE2 cell into our network.
     *
     * <p>Items only: whatever else AE2 carries — fluids, foreign kinds from
     * other mods — is left behind, rather than quietly vanishing during the
     * move.
     *
     * @return how many pieces were transferred
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
        // Over a copy of the list: extracting mutates it, and iterating over
        // the original would skip entries.
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
            // Ask first how much fits, then take out exactly that much. The
            // other way around, the remainder would end up on the floor.
            long fits = there - storage.insert(key, there);
            if (fits <= 0) {
                continue;
            }
            long taken = inventory.extract(found, fits, Actionable.MODULATE, source);
            if (taken < fits) {
                // The cell yielded less than announced — put the surplus back
                // into the network, rather than inventing it.
                storage.extract(key, fits - taken);
            }
            moved += taken;
        }
        return moved;
    }

    private Ae2Cells() {
    }
}
