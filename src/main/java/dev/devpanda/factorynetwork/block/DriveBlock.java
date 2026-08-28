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
        player.openMenu(shelf, buffer -> buffer.writeByte(
                dev.devpanda.factorynetwork.client.menu.ShelfMenu
                        .kindOf(shelf.layout())));
        return InteractionResult.CONSUME;
    }

    /**
     * Rechtsklick mit einer AE2-Zelle schüttet sie ins Netz.
     *
     * <p><b>Für den Umzug.</b> Wer ein AE2-Netz stehen hat und hierher
     * wechselt, soll seine Sachen mitnehmen können, ohne sie erst in tausend
     * Kisten zu leeren.
     *
     * <p>Eine Einbahnstraße, und was das Netz nicht nimmt, bleibt in der
     * Zelle: Ein Umzug, der dabei etwas verliert, ist kein Umzug.
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
