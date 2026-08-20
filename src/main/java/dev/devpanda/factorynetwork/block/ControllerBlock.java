package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Wurzel eines Netzwerks.
 *
 * <p>In der ersten Fassung hält er auch den Speicher. Das Konzept sieht dafür
 * einen eigenen Block vor; ihn jetzt zu bauen hätte den vertikalen Schnitt
 * verlängert, ohne etwas zu zeigen, was der Controller nicht auch zeigt.
 */
public class ControllerBlock extends Block implements EntityBlock {

    public static final MapCodec<ControllerBlock> CODEC = simpleCodec(ControllerBlock::new);

    public ControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == FnBlockEntities.CONTROLLER.get()
                ? (l, p, s, entity) -> ((ControllerBlockEntity) entity).serverTick()
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            controller.rebuildNetwork();
            player.displayClientMessage(Component.translatable(
                    "message.factorynetwork.controller.status",
                    controller.graph().connectorCount(),
                    controller.graph().unnamedConnectors().size(),
                    controller.graph().cableCount()), false);
            if (!controller.graph().starvedConnectors().isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "message.factorynetwork.controller.starved",
                        controller.graph().starvedConnectors().size()), false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
