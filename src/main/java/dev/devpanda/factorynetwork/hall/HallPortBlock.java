package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A port: a piece of hall wall that carries the cable through.
 *
 * <p>A cable on a port inside the hall belongs to the same network as the
 * cable on the hall's block outside — as if the cable ran on through the
 * wall. A hall may have any number of ports, and all of them are the same
 * line: one cable outside reaches every port.
 *
 * <p>Still a wall: unbreakable, it keeps the hall closed. Sneaking with an
 * empty hand turns it back into plain wall and returns the port.
 */
public class HallPortBlock extends HallWallBlock implements EntityBlock {

    public HallPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HallPortBlockEntity(pos, state);
    }

    /** No way out through a port: a click on it is meant for the cable. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        level.setBlockAndUpdate(pos, FnBlocks.HALL_WALL.get().defaultBlockState());
        if (!player.isCreative()) {
            ItemStack port = new ItemStack(FnItems.HALL_PORT.get());
            if (!player.getInventory().add(port)) {
                player.drop(port, false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
