package dev.devpanda.factorynetwork.terminal;

import dev.devpanda.factorynetwork.block.entity.MastBlockEntity;
import dev.devpanda.factorynetwork.item.RemoteDeviceItem;
import dev.devpanda.factorynetwork.upgrade.Range;
import dev.devpanda.factorynetwork.upgrade.RemoteDevice;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Whether a window may stay open from a distance.
 *
 * <p>At the block the question is simple: is the block still there and is the
 * player near enough? From a distance there are three questions, and all three
 * can flip mid-game.
 *
 * <p><b>It lives here and not in the menu</b>, so that a test run can pose it.
 * A test that instead waits for the window to close checks the ticker and not
 * the rule.
 */
public final class RemoteAccess {

    /**
     * May the player keep working with this device?
     *
     * <p>Three conditions, in this order:
     *
     * <ol>
     *   <li><b>The device is still where it was.</b> Whoever puts it away,
     *       into a chest, or drops it, no longer has one in hand. Without this
     *       check the window would stay open while the item has long since
     *       been sitting in a chest.</li>
     *   <li><b>It is still the same network.</b> Whoever swaps a second device
     *       into the slot, one that hangs off a different mast, would otherwise
     *       hold a window open onto a network the device in their hand does not
     *       belong to at all.</li>
     *   <li><b>The mast is still standing — and its piece of world is
     *       loaded.</b> It may have been mined away while the window was
     *       open.</li>
     *   <li><b>The player is in range.</b> It comes from the cards in the mast
     *       and the device — this is the place where the range does anything at
     *       all. If they stand in another dimension, only the infinity card
     *       reaches.</li>
     * </ol>
     *
     * @param slot the slot in the inventory where the device lay when it was opened
     * @param expected the mast this window hangs off
     */
    public static boolean allowed(Player player, int slot, GlobalPos expected) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return false;
        }
        ItemStack device = player.getInventory().getItem(slot);
        RemoteDevice kind = RemoteDeviceItem.deviceOf(device);
        if (kind == null) {
            return false;
        }
        GlobalPos mast = RemoteDeviceItem.mastOf(device);
        if (mast == null || !mast.equals(expected)) {
            return false;
        }
        MastBlockEntity standing = mastAt(player, mast);
        if (standing == null) {
            return false;
        }
        boolean sameLevel = player.level().dimension().equals(mast.dimension());
        // The distance counts from the mast, not from the controller: whoever
        // puts up a second mast extends their range with it, and that is
        // exactly what one builds it for. In another dimension there is no
        // distance — there the card alone decides.
        double distance = sameLevel
                ? Math.sqrt(player.distanceToSqr(mast.pos().getX() + 0.5,
                        mast.pos().getY() + 0.5, mast.pos().getZ() + 0.5))
                : 0;
        return Range.covers(standing.loadout(), RemoteDeviceItem.loadoutOf(device),
                sameLevel, distance);
    }

    /**
     * The mast at this spot — even in another dimension.
     *
     * <p><b>First ask whether the piece of world is loaded.</b>
     * {@code getBlockEntity} would otherwise load it in, and this question is
     * asked for every open window on every tick — a network at the other end
     * of the world would thereby keep land loaded permanently that no one sets
     * foot on.
     *
     * <p>A mast that is not loaded counts as unreachable. That is honest: no
     * one computes what happens there as long as no player is nearby.
     */
    public static MastBlockEntity mastAt(Player player, GlobalPos mast) {
        Level level = player.level();
        if (!level.dimension().equals(mast.dimension())) {
            if (player.getServer() == null) {
                return null;
            }
            level = player.getServer().getLevel(mast.dimension());
        }
        if (level == null || !level.isLoaded(mast.pos())) {
            return null;
        }
        return level.getBlockEntity(mast.pos()) instanceof MastBlockEntity standing
                ? standing : null;
    }

    /**
     * Where in the inventory this device lies, or -1.
     *
     * <p>What is searched for is the item itself, not an equal one: two
     * terminals in the inventory are different devices with different masts,
     * and whoever takes out the second should not close the first.
     */
    public static int slotOf(Player player, ItemStack device) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == device) {
                return slot;
            }
        }
        return -1;
    }

    private RemoteAccess() {
    }
}
