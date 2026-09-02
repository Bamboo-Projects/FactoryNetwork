package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The window in which a block is given its name.
 *
 * <p>One for the connector and the display: it is the same action — telling a
 * block in the network what it is called. Two versions would be two places
 * where the naming rules drift apart.
 *
 * <p><b>Why a window on the block at all?</b> Until now, naming was only
 * possible with the label gun, and that meant: without a crafted gun, no
 * device could be addressed. A right-click merely told you the name you saw
 * anyway. The gun stays — it can do something a window cannot: type a name
 * once and hand it out twenty times.
 *
 * <p>No slots: a name is a string, not an item. What travels back and forth
 * is a position and a word.
 */
public class NameMenu extends AbstractContainerMenu {

    /** How far you may move from the block before it closes. */
    private static final double REACH = 8.0;

    private final BlockPos position;
    /**
     * The face the connector sits on — {@code null} for anything that is a
     * whole block.
     *
     * <p>A cable block carries up to six connectors. Only the click that
     * opened the window knows which one is meant: afterwards it can no longer
     * be found out.
     */
    private final @org.jetbrains.annotations.Nullable Direction side;

    public NameMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, buffer.readBlockPos(), sideOf(buffer.readByte()));
    }

    public NameMenu(int id, BlockPos position) {
        this(id, position, null);
    }

    public NameMenu(int id, BlockPos position,
                    @org.jetbrains.annotations.Nullable Direction side) {
        super(FnMenus.NAME.get(), id);
        this.position = position;
        this.side = side;
    }

    private static @org.jetbrains.annotations.Nullable Direction sideOf(byte written) {
        return written < 0 ? null : Direction.from3DDataValue(written);
    }

    public BlockPos position() {
        return position;
    }

    public @org.jetbrains.annotations.Nullable Direction side() {
        return side;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * As long as you are standing nearby.
     *
     * <p>Not via {@code stillValid(access, player, block)} like the other
     * windows: the menu does not know the block it opens for — the connector
     * and the display share it.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5,
                position.getZ() + 0.5) <= REACH * REACH;
    }
}
