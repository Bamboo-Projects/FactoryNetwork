package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.item.ColouredCableItem;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

/**
 * Colours the strands of a cable bundle.
 *
 * <p>The model is grey and does not know the colours — it only knows how many
 * strands lie in the block and at which spot. Which colour the second strand
 * has stands in the BlockEntity, and that is exactly where this handler
 * reaches.
 *
 * <p>Going through tinting saves seventeen sets of textures: a grey texture is
 * multiplied at runtime by the colour of the block state.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class CableColours {

    /** Grey value of the texture that the colour is multiplied onto. */
    private static final int NEUTRAL = 0xFFFFFF;

    /**
     * Both cable kinds.
     *
     * <p><b>The dense one was missing here</b>, ever since the dense one has
     * existed: it carried its colour in the block state, Jade named it in the
     * tooltip, and the block stayed grey. Registration therefore happens from
     * a list — the next cable kind can no longer be forgotten.
     */
    @SubscribeEvent
    public static void registerBlockColours(RegisterColorHandlersEvent.BlockTintSources event) {
        // Since 26.1 a block names one tint source per tint index; the cable
        // models use index 0 for the ring and nothing else.
        event.register(java.util.List.of(new net.minecraft.client.color.block.BlockTintSource() {
            @Override
            public int color(net.minecraft.world.level.block.state.BlockState state) {
                return 0xFF000000 | tint(CableBlock.colourOf(state));
            }

            @Override
            public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>>
                    relevantProperties() {
                return java.util.Set.of(CableBlock.COLOUR);
            }
        }), FnBlocks.CABLE.get(), FnBlocks.DENSE_CABLE.get());
    }

    // In the hand there is no BlockEntity and, since 1.21.4, no code either:
    // each coloured cable item names its tint in assets/factorynetwork/items,
    // computed the same way as tint() below.

    /**
     * The multiplier for a colour.
     *
     * <p>The default colour stays unchanged — its texture is already the
     * ordinary metal and should not be tinted.
     */
    private static int tint(CableColour colour) {
        if (colour == CableColour.NONE || colour.dye() == null) {
            return NEUTRAL;
        }
        int packed = colour.dye().getTextureDiffuseColor();
        // Darken a little, so the cable sits alongside the blocks and does not
        // glow in front of them.
        int r = (int) (((packed >> 16) & 0xFF) * 0.82);
        int g = (int) (((packed >> 8) & 0xFF) * 0.82);
        int b = (int) ((packed & 0xFF) * 0.82);
        return (r << 16) | (g << 8) | b;
    }

    private CableColours() {
    }
}
