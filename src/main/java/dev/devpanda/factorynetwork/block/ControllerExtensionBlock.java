package dev.devpanda.factorynetwork.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An extension for the controller: more outer faces for cables, nothing else.
 *
 * <p>The controller has six sides, each carrying at most one run, and a dense
 * cable carries sixty-four channels — that makes 384 devices per network.
 * Whoever needs more sets extensions beside it; each brings its own sides.
 *
 * <p><b>It holds nothing.</b> No BlockEntity, no state, no program. That is
 * the whole reason it exists as its own block type rather than as a second
 * controller: as soon as several identical blocks form a network, one of them
 * has to be the master, and the question "which one" has no good answer. The
 * program lives as a file next to the world and is named after the
 * controller's position — a wandering master renames the file, and whoever has
 * it open in VS Code writes from then on into a program nobody reads any more.
 *
 * <p>Without a controller in the group it does nothing. It is not a conduit:
 * it connects no cable, it takes no power. Whoever places two extensions next
 * to each other gets sides and no wire.
 *
 * <p>The full rationale is in {@code docs/entscheidungen.md} under
 * "Der Controller bleibt ein Block".
 */
public class ControllerExtensionBlock extends Block {

    /** The outline from the boxes of the model. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(MachineLayouts.extension());

    public ControllerExtensionBlock(Properties properties) {
        super(properties);
    }
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
