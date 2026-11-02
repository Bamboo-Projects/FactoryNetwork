package dev.devpanda.factorynetwork.hall;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The wall of a factory hall: unbreakable, and a right-click on it leads out.
 *
 * <p>Every wall block is an exit, not one marked spot: in a hall of 48 blocks
 * a single door would be a walk, and a door that can be built over would be
 * lost.
 */
public class HallWallBlock extends Block {

    public HallWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.dimension() != Halls.DIMENSION) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            HallDoor.leave(server, pos);
        }
        return InteractionResult.CONSUME;
    }
}
