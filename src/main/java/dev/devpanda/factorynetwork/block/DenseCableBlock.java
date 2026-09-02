package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;

/**
 * The dense cable.
 *
 * <p>Ten block pixels thick and carrying sixty-four channels — the same
 * doubling as in Applied Energistics, just set one tier higher. It does not
 * bundle; whoever wants to run two networks through the same wall uses the
 * distributor.
 *
 * <p>Its own class only because of the codec: Minecraft needs one per block,
 * and {@code simpleCodec} requires a constructor with exactly those
 * properties. The behaviour lives entirely in {@link CableBlock}.
 */
public class DenseCableBlock extends CableBlock {

    public static final MapCodec<DenseCableBlock> CODEC = simpleCodec(DenseCableBlock::new);

    public DenseCableBlock(Properties properties) {
        super(properties, CableLayout.DENSE,
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE);
    }

    @Override
    protected java.util.Map<CableColour, net.neoforged.neoforge.registries.DeferredItem<
            net.minecraft.world.item.BlockItem>> items() {
        return dev.devpanda.factorynetwork.registry.FnItems.DENSE_CABLES;
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return CODEC;
    }
}
