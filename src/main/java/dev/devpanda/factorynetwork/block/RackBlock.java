package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Der Serverschrank: Ohne ihn rechnet das Netz nicht.
 *
 * <p>So wie ein Laufwerk die Voraussetzung dafür ist, dass das Netz lagert,
 * ist der Schrank die Voraussetzung dafür, dass es rechnet. Jede Fähigkeit
 * des Netzes hängt an einem Block, den man bauen muss.
 *
 * <p><b>Zwei Blöcke hoch, ein Gerät.</b> Ein Schrank mit zwölf Einschüben,
 * der einen Würfel groß ist, sieht aus wie ein Kasten und nicht wie ein
 * Schrank — und zwölf Kacheln auf einer Würfelseite sind kleiner als das
 * Fadenkreuz. Die untere Hälfte trägt alles: die BlockEntity, den Inhalt,
 * den Platz im Netz. Die obere ist Blech, das mitgeht.
 */
public class RackBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);

    /** Untere oder obere Hälfte — wie bei Tür und Bett. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<
            DoubleBlockHalf> HALF =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Die Hälfte, an der alles hängt. */
    public static BlockPos baseOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /**
     * Nur, wenn darüber Platz ist.
     *
     * <p>{@code null} heißt „nicht setzen" — besser als ein halber Schrank,
     * den man danach nicht mehr von einem ganzen unterscheiden kann.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(),
                state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Die obere Hälfte steht nur, solange die untere darunter steht. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Fällt eine Hälfte, geht die andere mit.
     *
     * <p>Dasselbe Muster wie bei Tür und Doppelblume. Ohne das bliebe nach
     * einer Explosion eine schwebende Blechhaube stehen, die nichts kann.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardsOtherHalf) {
            return neighbour.is(this) && neighbour.getValue(HALF) != half
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Wer oben zuschlägt, baut trotzdem den Schrank ab.
     *
     * <p>Der Gegenstand und der Inhalt hängen an der unteren Hälfte — die
     * Loot-Tabelle gibt nur dort etwas her, damit eine Explosion nicht zwei
     * Schränke aus einem macht. Also wird sie zuerst und regulär abgebaut;
     * die obere räumt {@link #updateShape} danach von selbst ab.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.destroyBlock(below, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Nur die untere Hälfte hat eine BlockEntity — es ist ein Gerät. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new RackBlockEntity(pos, state)
                : null;
    }

    /**
     * Ein Klick öffnet das Fenster — oben wie unten.
     *
     * <p>Wer den Schrank anklickt, will ihn öffnen; ob er dabei die obere
     * oder die untere Hälfte trifft, ist eine Frage der Körpergröße und
     * keine Entscheidung.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(baseOf(state, pos)) instanceof ShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        player.openMenu(shelf, buffer -> buffer.writeBoolean(shelf.layout()
                == dev.devpanda.factorynetwork.client.menu.ShelfMenu.DRIVE));
        return InteractionResult.CONSUME;
    }

    /** Beim Abbauen fallen die Bauteile heraus. Die Loot-Tabelle sieht sie nicht. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            for (ItemStack stack : rack.contents()) {
                if (!stack.isEmpty()) {
                    popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
