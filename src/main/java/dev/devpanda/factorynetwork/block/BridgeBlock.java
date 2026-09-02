package dev.devpanda.factorynetwork.block;

import dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The quantum bridge: one end of a conduit with no cable in between.
 *
 * <p>Two of them, each holding one half of the same entanglement, connect their
 * networks across any distance. What passes through are channels, as through a
 * dense cable — it is a conduit, not a multiplier.
 *
 * <p><b>It has no front face.</b> Like the mast: it just stands there, and which
 * way it points changes nothing about what it does.
 */
public class BridgeBlock extends Block implements EntityBlock {

    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(BridgeLayout.boxes());

    /**
     * Is the far end responding?
     *
     * <p><b>Without this indicator you go hunting for the fault in the cable.</b> A
     * bridge whose partner has been removed or is not loaded otherwise looks
     * just like one that is working — and the network ends for no visible reason.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty
            LINKED = net.minecraft.world.level.block.state.properties.BooleanProperty
                    .create("linked");

    public BridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<
                    net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LINKED);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BridgeBlockEntity(pos, state);
    }

    /**
     * Right-clicking with a half places it inside.
     *
     * <p>No screen for a single slot: what belongs in there is exactly one
     * thing, and you are already holding it in your hand.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (dev.devpanda.factorynetwork.item.EntanglementItem.idOf(held) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof BridgeBlockEntity bridge)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!bridge.getItem(0).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        bridge.setItem(0, held.split(1));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.6F);
        return ItemInteractionResult.CONSUME;
    }

    /** And with an empty hand it comes back out again. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BridgeBlockEntity bridge)
                || bridge.getItem(0).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack taken = bridge.removeItemNoUpdate(0);
        if (!player.getInventory().add(taken)) {
            popResource(level, pos, taken);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * When broken, the half drops out.
     *
     * <p>The loot table does not see it — and a lost half makes the other one
     * worthless, since a pair only comes into being when crafted.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BridgeBlockEntity bridge) {
            Containers.dropContents(level, pos, bridge);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
