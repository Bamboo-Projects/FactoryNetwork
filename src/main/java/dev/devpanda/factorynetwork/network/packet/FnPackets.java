package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class FnPackets {

    private static final String VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(DeployProgramPacket.TYPE, DeployProgramPacket.STREAM_CODEC,
                DeployProgramPacket::handle);
        registrar.playToClient(NetworkStatePacket.TYPE, NetworkStatePacket.STREAM_CODEC,
                NetworkStatePacket::handle);
    }

    private FnPackets() {
    }
}
