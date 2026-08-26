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
 * Färbt die Stränge eines Kabelbündels ein.
 *
 * <p>Das Modell ist grau und kennt die Farben nicht — es weiß nur, wie viele
 * Stränge im Block liegen und an welcher Stelle. Welche Farbe der zweite
 * Strang hat, steht in der BlockEntity, und genau dorthin greift dieser
 * Handler.
 *
 * <p>Der Weg über die Einfärbung spart siebzehn Sätze Texturen: Eine graue
 * Textur wird zur Laufzeit mit der Farbe des Blockzustands multipliziert.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class CableColours {

    /** Grauwert der Textur, auf den die Farbe multipliziert wird. */
    private static final int NEUTRAL = 0xFFFFFF;

    /**
     * Beide Kabelarten.
     *
     * <p><b>Das dichte fehlte hier</b>, seit es das dichte gibt: Es trug
     * seine Farbe im Blockzustand, Jade nannte sie im Tooltip, und der Block
     * blieb grau. Angemeldet wird deshalb aus einer Liste — die nächste
     * Kabelart kann nicht mehr vergessen werden.
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
        // In der Hand gibt es keine BlockEntity — die Farbe steht im Gegenstand.
        // Auch hier beide Arten: Ein dichtes Kabel im Rucksack sah aus wie
        // jedes andere.
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
     * Der Multiplikator für eine Farbe.
     *
     * <p>Die Standardfarbe bleibt unverändert — ihre Textur ist schon das
     * gewöhnliche Metall und soll nicht getönt werden.
     */
    private static int tint(CableColour colour) {
        if (colour == CableColour.NONE || colour.dye() == null) {
            return NEUTRAL;
        }
        int packed = colour.dye().getTextureDiffuseColor();
        // Etwas abdunkeln, damit das Kabel neben den Blöcken liegt und nicht
        // vor ihnen leuchtet.
        int r = (int) (((packed >> 16) & 0xFF) * 0.82);
        int g = (int) (((packed >> 8) & 0xFF) * 0.82);
        int b = (int) ((packed & 0xFF) * 0.82);
        return (r << 16) | (g << 8) | b;
    }

    private CableColours() {
    }
}
