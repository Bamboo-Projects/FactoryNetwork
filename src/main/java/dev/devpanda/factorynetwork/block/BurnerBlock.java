package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Die Brennkammer: Strom aus Ofenbrennstoff.
 *
 * <p>Absichtlich mittelmäßig — sie soll nicht mit Generatoren anderer Mods
 * konkurrieren, sondern dafür sorgen, dass die Fertigungskette der Mod ohne
 * Fremdmod überhaupt anläuft.
 *
 * <p>Ob sie brennt, steht im Blockzustand und nicht in der BlockEntity: Es ist
 * genau ein Wahrheitswert, und daran hängt sowohl die Textur als auch das
 * Licht.
 */
public class BurnerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<BurnerBlock> CODEC = simpleCodec(BurnerBlock::new);

    /** Brennt sie gerade? Daran hängen Textur und Licht. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public BurnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type != FnBlockEntities.BURNER.get() ? null
                : (ticked, pos, blockState, entity) ->
                        BurnerBlockEntity.serverTick(ticked, pos, blockState,
                                (BurnerBlockEntity) entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BurnerBlockEntity burner) {
            player.openMenu(burner);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Beim Abbauen fällt der Brennstoff heraus. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BurnerBlockEntity burner) {
            net.minecraft.world.Containers.dropContents(level, pos, burner);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
