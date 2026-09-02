package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A power source without fuel, for creative mode.
 *
 * <p>The mod does not generate power — it draws Forge Energy from the pack, as
 * the press already does. But for trying things out there is no pack on hand,
 * and without a source every network stands still at once. <b>This block is
 * there for exactly that and nothing else:</b> no recipe, no chain, it only
 * appears in the creative tab.
 *
 * <p>It pushes into every neighbour that accepts power — machines of other
 * mods included. Anyone who got hold of it in survival mode would already have
 * won the game.
 */
public class CreativeSourceBlock extends Block {

    /** The outline from the boxes of the model. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(MachineLayouts.source());

    public static final MapCodec<CreativeSourceBlock> CODEC =
            simpleCodec(CreativeSourceBlock::new);

    /** Plenty: the block should never be the bottleneck. */
    private static final int PER_TICK = 100_000;

    /**
     * And the same for pulling.
     *
     * <p>Pushing alone is no longer enough now that a worker can <b>pull</b>
     * power: {@code from quelle to network} asks the block for its storage.
     * Without this one here the creative source would be the only power source
     * that no program can reach.
     */
    public static final IEnergyStorage TAP = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return Math.max(0, toExtract);
        }

        @Override
        public int getEnergyStored() {
            return PER_TICK;
        }

        @Override
        public int getMaxEnergyStored() {
            return PER_TICK;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public CreativeSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Pushing happens via the block tick.
     *
     * <p>A random tick would come too rarely, a BlockEntity would be effort
     * for a block that keeps no state. The scheduled tick carries itself on,
     * as long as the block stands.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                        BlockPos pos, net.minecraft.util.RandomSource random) {
        for (Direction side : Direction.values()) {
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(side), side.getOpposite());
            if (sink != null && sink.canReceive()) {
                sink.receiveEnergy(PER_TICK, false);
            }
        }
        level.scheduleTick(pos, this, 1);
    }
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
