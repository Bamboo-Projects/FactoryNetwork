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
    public static void registerBlockColours(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex < 0 || level == null || pos == null) {
                return NEUTRAL;
            }
            return tint(CableBlock.colourOf(state));
        }, FnBlocks.CABLE.get(), FnBlocks.DENSE_CABLE.get());
    }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        // In the hand there is no BlockEntity — the colour stands in the item.
        // Both kinds here too: a dense cable in the backpack looked like any
        // other.
        List.of(FnItems.CABLES, FnItems.DENSE_CABLES).forEach(cables ->
                cables.values().forEach(holder ->
                        event.register((stack, tintIndex) -> {
                            if (tintIndex != 0) {
                                return NEUTRAL;
                            }
                            return stack.getItem() instanceof ColouredCableItem cable
                                    ? tint(cable.colour()) : NEUTRAL;
                        }, holder.get())));
    }

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
