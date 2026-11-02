package dev.devpanda.factorynetwork.block;

import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Stands between our cable and a foreign storage system.
 *
 * <p>A connector on our side, the foreign system's cable on the other. One
 * block for all systems: which one is attached, the block entity finds out.
 */
public class StorageLinkBlock extends Block implements EntityBlock {

    public StorageLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageLinkBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FnBlockEntities.STORAGE_LINK.get()) {
            return null;
        }
        return (l, p, s, entity) -> ((StorageLinkBlockEntity) entity).serverTick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos from, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, from, moved);
        if (level.getBlockEntity(pos) instanceof StorageLinkBlockEntity link) {
            link.neighbourChanged();
        }
    }

    /** A click says which system is attached, or that none is. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof StorageLinkBlockEntity link) {
            var adapter = link.adapter();
            dev.devpanda.factorynetwork.platform.Chat.tell(player, adapter == null
                    ? Component.translatable("message.factorynetwork.storage_link.none")
                    : Component.translatable("message.factorynetwork.storage_link.attached",
                            adapter.systemName()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            dev.devpanda.factorynetwork.network.ControllerRegistry.markChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock())) {
            dev.devpanda.factorynetwork.network.ControllerRegistry.markChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
