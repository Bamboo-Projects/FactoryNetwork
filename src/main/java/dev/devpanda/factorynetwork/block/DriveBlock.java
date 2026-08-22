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
     * Eine Zelle in der Hand geht hinein.
     *
     * <p><b>Ohne das war der ganze Speicher nicht erreichbar.</b> Die Zellen
     * kamen bisher nur über Prüfungen ins Laufwerk — im Spiel stand ein Block
     * herum, in den nichts hineinging.
     *
     * <p>Kein Fenster mit zehn Feldern, sondern ein Griff: Zelle in der Hand
     * hinein, leere Hand heraus. Ein Fenster wäre der gewohntere Weg, aber es
     * ist ein eigener Bildschirm samt Hintergrundbild für eine Handlung, die
     * aus einem Klick besteht. Was drinsteckt, sagt Jade.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!(stack.getItem() instanceof dev.devpanda.factorynetwork.storage.StorageCellItem)) {
            return net.minecraft.world.ItemInteractionResult
                    .PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof DriveBlockEntity drive)) {
            return net.minecraft.world.ItemInteractionResult.FAIL;
        }
        int slot = drive.firstFreeSlot();
        if (slot < 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.factorynetwork.drive.full"), true);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        drive.setCell(slot, stack.split(1));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.3F);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.factorynetwork.drive.inserted",
                drive.usedSlots(), DriveBlockEntity.SLOTS), true);
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    /**
     * Die leere Hand öffnet das Fenster.
     *
     * <p>Voll heißt einstecken, leer heißt aufmachen. Das Einstecken per Klick
     * bleibt, weil es der häufigere Griff ist — wer zehn Zellen einsetzt, will
     * dafür kein Fenster.
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
}
