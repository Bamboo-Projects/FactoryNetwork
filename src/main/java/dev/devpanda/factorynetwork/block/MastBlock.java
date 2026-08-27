package dev.devpanda.factorynetwork.block;

import dev.devpanda.factorynetwork.block.entity.MastBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Der Sendemast: von hier aus funkt das Netz.
 *
 * <p>Er hat keine Vorderseite — ein Mast steht, und wohin seine Ausleger
 * zeigen, ändert nichts an dem, was er tut.
 */
public class MastBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = FacingShapes.whole(MastLayout.boxes());

    public MastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MastBlockEntity(pos, state);
    }

    /** Rechtsklick öffnet die vier Steckplätze. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MastBlockEntity mast)) {
            return InteractionResult.PASS;
        }
        player.openMenu(mast, buffer -> buffer.writeByte(
                dev.devpanda.factorynetwork.client.menu.ShelfMenu
                        .kindOf(dev.devpanda.factorynetwork.client.menu.ShelfMenu.MAST)));
        return InteractionResult.CONSUME;
    }

    /**
     * Beim Abbauen fallen die Karten heraus.
     *
     * <p>Die Loot-Tabelle sieht sie nicht — wer einen bestückten Mast abbaut,
     * verlöre sonst vier Karten, und eine davon kann die teuerste im Spiel
     * sein.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MastBlockEntity mast) {
            Containers.dropContents(level, pos, mast);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
