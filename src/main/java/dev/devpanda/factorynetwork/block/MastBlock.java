package dev.devpanda.factorynetwork.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Der Sendemast: von hier aus funkt das Netz.
 *
 * <p>Er hat keine Vorderseite — ein Mast steht, und wohin seine Ausleger
 * zeigen, ändert nichts an dem, was er tut.
 */
public class MastBlock extends Block {

    private static final VoxelShape SHAPE = FacingShapes.whole(MastLayout.boxes());

    public MastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }
}
