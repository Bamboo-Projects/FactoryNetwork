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
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID)
public class HallWallBlock extends Block {

    public HallWallBlock(Properties properties) {
        super(properties);
    }

    /**
     * Not even in creative mode: a broken wall opens the hall to the void
     * and to the next hall's chunks. Operators still have /setblock.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void keepWalls(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getState().getBlock() instanceof HallWallBlock) {
            event.setCanceled(true);
        }
    }

    /**
     * With anything in hand the item decides: a block placed against the
     * wall, a cable, a port. Only the empty hand leads out — otherwise
     * building against a wall would throw the builder out of the hall.
     */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack held, BlockState state, Level level,
                                          BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (!held.isEmpty()) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(held, state, level, pos, player, hand, hit);
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
