package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Nimmt Speicherzellen auf.
 *
 * <p>Der Speicher des Netzes ist die Summe seiner Laufwerke — ohne eines gibt
 * es keinen Platz. Das ist der Unterschied zu vorher, als der Controller
 * unbegrenzt lagerte: Lagerraum ist jetzt etwas, das man baut.
 */
public class DriveBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * Die Trefferfläche, für jede der vier Richtungen einmal.
     *
     * <p>Das Modell dreht der Blockzustand, die Trefferfläche niemand — also
     * dreht sie {@link FacingShapes} hier, aus denselben Kästen.
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
     * Ein Klick öffnet das Fenster.
     *
     * <p>Immer, egal was in der Hand liegt — wie bei jeder Kiste. Vorher ging
     * ein Bauteil in der Hand direkt hinein; das ersparte zwar einen Griff,
     * war aber eine eigene Regel für zwei Blöcke, und wer sie nicht kennt,
     * steckt versehentlich etwas ein, statt nachzusehen.
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
        player.openMenu(shelf, buffer -> buffer.writeBoolean(shelf.layout()
                == dev.devpanda.factorynetwork.client.menu.ShelfMenu.DRIVE));
        return InteractionResult.CONSUME;
    }

    /**
     * Beim Abbauen fallen die Zellen heraus.
     *
     * <p>Ohne das wäre ein versehentlicher Schlag der Verlust des halben
     * Lagers — der Inhalt steckt in den Zellen, und die stecken hier. Die
     * Loot-Tabelle kann es nicht: Sie sieht den Blockzustand, nicht die
     * BlockEntity.
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
