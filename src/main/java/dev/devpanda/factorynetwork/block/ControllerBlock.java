package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    /** Der Umriss aus Deckplatten, Kantensäulen und dem Körper dazwischen. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(ControllerLayout.boxes());

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

    /**
     * Merkt sich, wer den Controller gesetzt hat.
     *
     * <p>Gebraucht nur, wenn der Server den Schutz einschaltet — gemerkt wird
     * es trotzdem immer: Wer ihn erst später einschaltet, hätte sonst lauter
     * herrenlose Anlagen.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            controller.setOwner(player.getUUID());
        }
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
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
