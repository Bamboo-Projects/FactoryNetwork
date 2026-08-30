package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;

/**
 * Das dichte Kabel.
 *
 * <p>Zehn Blockpixel stark und mit vierundsechzig Kanälen — dieselbe
 * Verdopplung wie bei Applied Energistics, nur eine Stufe höher angesetzt.
 * Es bündelt nicht; wer zwei Netze durch dieselbe Wand führen will, nimmt den
 * Verteiler.
 *
 * <p>Eine eigene Klasse nur wegen des Codecs: Minecraft braucht je Block
 * einen, und {@code simpleCodec} verlangt einen Konstruktor mit genau den
 * Eigenschaften. Das Verhalten steckt vollständig in {@link CableBlock}.
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
