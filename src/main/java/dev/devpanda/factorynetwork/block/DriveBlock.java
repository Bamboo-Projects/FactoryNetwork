package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/**
 * Holds storage cells.
 *
 * <p>The network's storage is the sum of its drives — without one there is no
 * space. That is the difference from before, when the controller stored
 * without limit: storage space is now something you build.
 */
public class DriveBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * The hitbox, once for each of the four directions.
     *
     * <p>The block state rotates the model; nothing rotates the hitbox — so
     * {@link FacingShapes} rotates it here, from the same boxes.
     */
    private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES =
            FacingShapes.horizontal(DriveLayout.boxes());

    public static final MapCodec<DriveBlock> CODEC = simpleCodec(DriveBlock::new);

    public DriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DriveBlockEntity(pos, state);
    }


    /**
     * A click opens the screen.
     *
     * <p>Always, no matter what is in hand — like any chest. Previously a part
     * held in hand went straight in; that did save a step, but it was a rule
     * of its own for two blocks, and anyone who does not know it stows
     * something away by accident instead of taking a look.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        player.openMenu(shelf, buffer -> buffer.writeByte(
                dev.devpanda.factorynetwork.client.menu.ShelfMenu
                        .kindOf(shelf.layout())));
        return InteractionResult.CONSUME;
    }

    /**
     * Right-clicking with an AE2 cell tips its contents into the network.
     *
     * <p><b>For migrating.</b> Anyone who has an AE2 network standing and
     * switches over here should be able to bring their things along, without
     * first having to empty them into a thousand chests.
     *
     * <p>A one-way street, and whatever the network does not take stays in the
     * cell: a migration that loses something along the way is no migration.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!dev.devpanda.factorynetwork.compat.ae2.Ae2Cells.isCell(held)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        var controller = dev.devpanda.factorynetwork.network.ControllerRegistry
                .owning(level, pos);
        if (controller.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("message.factorynetwork.import.no_network"), true);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        long moved = dev.devpanda.factorynetwork.compat.ae2.Ae2Cells.drainInto(
                held, controller.get().storage());
        controller.get().setChanged();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                moved > 0 ? "message.factorynetwork.import.moved"
                          : "message.factorynetwork.import.nothing", moved), true);
        if (moved > 0) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    /**
     * Breaking the block drops the cells.
     *
     * <p>Without this an accidental hit would mean losing half the storage —
     * the contents sit in the cells, and those sit here. The loot table cannot
     * do it: it sees the block state, not the BlockEntity.
     */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof DriveBlockEntity drive) {
            for (net.minecraft.world.item.ItemStack cell : drive.cells()) {
                if (!cell.isEmpty()) {
                    popResource(level, pos, cell);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }
}
