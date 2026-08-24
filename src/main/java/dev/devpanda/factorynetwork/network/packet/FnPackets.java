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
        registrar.playToClient(DeployResultPacket.TYPE, DeployResultPacket.STREAM_CODEC,
                DeployResultPacket::handle);
        registrar.playToClient(ProjectStatePacket.TYPE, ProjectStatePacket.STREAM_CODEC,
                ProjectStatePacket::handle);
        registrar.playToServer(SaveDraftPacket.TYPE, SaveDraftPacket.STREAM_CODEC,
                SaveDraftPacket::handle);
        registrar.playToClient(NetworkStatePacket.TYPE, NetworkStatePacket.STREAM_CODEC,
                NetworkStatePacket::handle);
        registrar.playToClient(StorageSnapshotPacket.TYPE, StorageSnapshotPacket.STREAM_CODEC,
                StorageSnapshotPacket::handle);
        registrar.playToServer(StorageActionPacket.TYPE, StorageActionPacket.STREAM_CODEC,
                StorageActionPacket::handle);
        registrar.playToServer(StorageTabPacket.TYPE, StorageTabPacket.STREAM_CODEC,
                StorageTabPacket::handle);
        registrar.playToServer(SetLabelPacket.TYPE, SetLabelPacket.STREAM_CODEC,
                SetLabelPacket::handle);
        registrar.playToServer(SetBlockNamePacket.TYPE, SetBlockNamePacket.STREAM_CODEC,
                SetBlockNamePacket::handle);
        registrar.playToClient(FlowStatePacket.TYPE, FlowStatePacket.STREAM_CODEC,
                FlowStatePacket::handle);
        registrar.playToServer(FlowActionPacket.TYPE, FlowActionPacket.STREAM_CODEC,
                FlowActionPacket::handle);
        registrar.playToClient(DisplayStatePacket.TYPE, DisplayStatePacket.STREAM_CODEC,
                DisplayStatePacket::handle);
        registrar.playToServer(DisplayActionPacket.TYPE, DisplayActionPacket.STREAM_CODEC,
                DisplayActionPacket::handle);
        registrar.playToClient(AnalyserDataPacket.TYPE, AnalyserDataPacket.STREAM_CODEC,
                AnalyserDataPacket::handle);
        registrar.playToServer(DeviceSnapshotRequestPacket.TYPE,
                DeviceSnapshotRequestPacket.STREAM_CODEC,
                DeviceSnapshotRequestPacket::handle);
        registrar.playToClient(DeviceSnapshotPacket.TYPE, DeviceSnapshotPacket.STREAM_CODEC,
                DeviceSnapshotPacket::handle);
        registrar.playToServer(RequestEditPacket.TYPE, RequestEditPacket.STREAM_CODEC,
                RequestEditPacket::handle);
    }

    private FnPackets() {
    }
}
