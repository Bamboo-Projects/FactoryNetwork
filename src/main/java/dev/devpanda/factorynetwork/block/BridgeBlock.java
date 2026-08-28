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
 * Die Quantum-Brücke: ein Ende einer Leitung ohne Kabel dazwischen.
 *
 * <p>Zwei davon, jede mit einer Hälfte derselben Verschränkung, verbinden ihre
 * Netze über jede Entfernung. Was hindurchgeht, sind Kanäle wie durch ein
 * dichtes Kabel — sie ist eine Leitung und kein Vermehrer.
 *
 * <p><b>Sie hat keine Vorderseite.</b> Wie der Sendemast: Sie steht, und wohin
 * sie zeigt, ändert nichts an dem, was sie tut.
 */
public class BridgeBlock extends Block implements EntityBlock {

    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(BridgeLayout.boxes());

    /**
     * Antwortet die Gegenstelle?
     *
     * <p><b>Ohne diese Anzeige sucht man den Fehler im Kabel.</b> Eine
     * Brücke, deren Partner abgebaut oder nicht geladen ist, sieht sonst aus
     * wie eine, die arbeitet — und das Netz endet ohne sichtbaren Grund.
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
     * Rechtsklick mit einer Hälfte legt sie hinein.
     *
     * <p>Kein Fenster für einen einzigen Platz: Was hineingehört, ist genau
     * eine Sache, und man hält sie schon in der Hand.
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

    /** Und ohne etwas in der Hand kommt sie wieder heraus. */
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
     * Beim Abbauen fällt die Hälfte heraus.
     *
     * <p>Die Loot-Tabelle sieht sie nicht — und eine verlorene Hälfte macht
     * die andere wertlos, denn ein Paar entsteht nur beim Bauen.
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
