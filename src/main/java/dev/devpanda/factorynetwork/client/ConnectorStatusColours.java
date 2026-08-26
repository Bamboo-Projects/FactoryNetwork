package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.Connectors;
import dev.devpanda.factorynetwork.client.render.DeviceStateColours;
import dev.devpanda.factorynetwork.network.DeviceState;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Färbt das Statuslämpchen am Connectorblock.
 *
 * <p>Derselbe Weg, den die Kabelfarben schon gehen: Das Modell trägt einen
 * {@code tintindex}, und die Farbe kommt zur Laufzeit. <b>Kein
 * BlockEntity-Renderer</b> — der brächte jeden Connector in die Zeichenliste,
 * für vier Quads, die das gebackene Modell umsonst mitbringt.
 *
 * <p>Der Anschluss am Kabel geht den anderen Weg: Dort zeichnet ohnehin ein
 * Renderer, weil die Teile in keinem Blockzustand stehen.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class ConnectorStatusColours {

    /** Ohne Auskunft bleibt die Textur, wie sie ist. */
    private static final int NEUTRAL = 0xFFFFFF;

    @SubscribeEvent
    public static void registerBlockColours(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex < 0 || level == null || pos == null) {
                return NEUTRAL;
            }
            var connector = Connectors.at(level, pos);
            return DeviceStateColours.of(
                    connector == null ? DeviceState.OFFLINE : connector.state());
        }, FnBlocks.CONNECTOR.get());
    }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        // In der Hand hängt er an keinem Netz — und ein graues Lämpchen am
        // Gegenstand im Rucksack sagt nichts, sondern sieht kaputt aus.
        event.register((stack, tintIndex) -> NEUTRAL, FnBlocks.CONNECTOR.get().asItem());
    }

    private ConnectorStatusColours() {
    }
}
