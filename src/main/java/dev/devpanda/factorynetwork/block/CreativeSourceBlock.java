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
 * Eine Stromquelle ohne Brennstoff, für den Kreativmodus.
 *
 * <p>Die Mod erzeugt keinen Strom — sie nimmt Forge Energy aus dem Pack, wie
 * es die Presse schon tut. Zum Ausprobieren steht aber kein Pack daneben, und
 * ohne eine Quelle steht jedes Netz sofort still. <b>Dieser Block ist genau
 * dafür da und für nichts sonst:</b> kein Rezept, keine Kette, er steht nur
 * im Kreativ-Reiter.
 *
 * <p>Er schiebt in jeden Nachbarn, der Strom annimmt — auch in Maschinen
 * anderer Mods. Wer ihn im Überlebensmodus bekäme, hätte das Spiel schon
 * gewonnen.
 */
public class CreativeSourceBlock extends Block {

    public static final MapCodec<CreativeSourceBlock> CODEC =
            simpleCodec(CreativeSourceBlock::new);

    /** Reichlich: Der Block soll nie der Engpass sein. */
    private static final int PER_TICK = 100_000;

    public CreativeSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Geschoben wird über den Blocktick.
     *
     * <p>Ein zufälliger Tick käme zu selten, eine BlockEntity wäre Aufwand für
     * einen Block, der nichts merkt. Der geplante Tick trägt sich selbst
     * weiter, solange der Block steht.
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
}
