package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fabricator: it builds what the network orders.
 *
 * <p><b>No pattern items.</b> What it can build the game already knows — every
 * crafting-table recipe lives in the server. A network that first has to have
 * its recipes written onto scraps of paper demands work for information that is
 * already there.
 *
 * <p><b>No BlockEntity.</b> It holds nothing: the order lives at the
 * controller, the ingredients in the storage, the recipe in the server. What
 * it contributes is the permission for building to happen — and how many hang
 * in the network decides how many steps happen per cycle. Whoever wants to
 * fabricate faster places a second one.
 *
 * <p>It costs one channel and power like every other device on the network.
 */
public class FabricatorBlock extends Block {

    /** The outline from the boxes of the model. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(MachineLayouts.fabricator());

    public static final MapCodec<FabricatorBlock> CODEC = simpleCodec(FabricatorBlock::new);

    public FabricatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
