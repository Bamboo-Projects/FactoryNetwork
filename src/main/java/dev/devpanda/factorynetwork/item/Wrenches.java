package dev.devpanda.factorynetwork.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Who counts as a wrench, and when it disassembles.
 *
 * <p><b>A tag and not a class.</b> {@code c:tools/wrench} is the convention
 * that Mekanism, Create, Thermal and Immersive Engineering serve. Whoever
 * has one of them on hand can work with it here, without this mod knowing a
 * single one of them — and our own sits alongside in the same tag, so that
 * it also works without a third-party mod.
 *
 * <p>The same approach as with the connectors: take the standard interface,
 * and everything that speaks it comes along for free.
 *
 * <p><b>The gesture is sneak plus right-click.</b> Borrowed from AE2, and
 * not out of convenience: a player learns a gesture once. If it means
 * "disassemble" in one mod and "rotate" in the next, they learn it three
 * times and forget it twice.
 */
public final class Wrenches {

    /** The convention's tag that everyone listens to. */
    public static final TagKey<Item> WRENCH = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    /** Is this a wrench? */
    public static boolean is(ItemStack stack) {
        return stack.is(WRENCH);
    }

    /**
     * Does the player want to disassemble with it right now?
     *
     * <p>Only with a wrench <b>and</b> while sneaking. Without sneaking the
     * same click should do what it otherwise does — open a window, for
     * example.
     */
    public static boolean disassembling(Player player, ItemStack stack) {
        return player.isShiftKeyDown() && is(stack);
    }

    /**
     * Takes off the connector this click means.
     *
     * <p>Lives here and not in the event handler so that a test run can call
     * it: a test that instead calls {@code removePart} checks the container
     * and not the wrench.
     *
     * @return whether something was taken off
     */
    public static boolean takePart(net.minecraft.world.level.Level level,
                                   net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block
                .entity.CableBusBlockEntity bus)) {
            return false;
        }
        net.minecraft.core.Direction side = dev.devpanda.factorynetwork.block.CableBlock
                .partSideAt(level, pos, hit);
        if (side == null || bus.partAt(side) == null) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        bus.removePart(side);
        // The connector comes back, otherwise the wrench would be a
        // destruction tool and not a wrench.
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()));
        // If it was a holder and that was its last connector, nothing
        // remains that would still hold anything — then the block goes too.
        if (!dev.devpanda.factorynetwork.block.CableBlock.carries(level.getBlockState(pos))
                && bus.parts().isEmpty()) {
            level.removeBlock(pos, false);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
            return true;
        }
        // The arm to the connector is stored in the block state: without
        // this line it would remain and point into empty space.
        level.setBlock(pos, dev.devpanda.factorynetwork.block.CableBlock.withConnections(
                        level.getBlockState(pos), level, pos),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    private Wrenches() {
    }
}
