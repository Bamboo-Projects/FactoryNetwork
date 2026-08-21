package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Der Bestand des Netzwerks für die Speicheransicht.
 *
 * <p>Beim Öffnen einmal vollständig, danach nur noch die Änderungen — und die
 * gebündelt, nicht einzeln. Ein Worker, der in jedem Tick etwas bewegt, darf
 * nicht in jedem Tick ein Paket auslösen.
 *
 * <p>Gesucht wird auf dem Client über diesen Schnappschuss. Das ist der Grund,
 * warum sich die Suche sofort anfühlt — und der Grund für die Obergrenze:
 * Ein Pack mit zwanzigtausend Arten würde sonst jedes Öffnen zu einer
 * Übertragung machen, die man merkt.
 */
public record StorageSnapshotPacket(List<Entry> entries, List<FluidEntry> fluids,
                                    boolean replace, int totalTypes)
        implements CustomPacketPayload {

    /** So viele Arten gehen höchstens über die Leitung. */
    public static final int MAX_ENTRIES = 4096;

    public record Entry(Item item, long amount) {
    }

    /** Eine Flüssigkeit mit ihrer Menge in Millibucket. */
    public record FluidEntry(net.minecraft.world.level.material.Fluid fluid, long amount) {
    }

    public static final Type<StorageSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_snapshot"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM),
                    Entry::item,
                    ByteBufCodecs.VAR_LONG, Entry::amount,
                    Entry::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, FluidEntry> FLUID_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(net.minecraft.core.registries.Registries.FLUID),
                    FluidEntry::fluid,
                    ByteBufCodecs.VAR_LONG, FluidEntry::amount,
                    FluidEntry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSnapshotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    StorageSnapshotPacket::entries,
                    FLUID_CODEC.apply(ByteBufCodecs.list(256)),
                    StorageSnapshotPacket::fluids,
                    ByteBufCodecs.BOOL, StorageSnapshotPacket::replace,
                    ByteBufCodecs.VAR_INT, StorageSnapshotPacket::totalTypes,
                    StorageSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Wurde etwas weggelassen, weil es zu viel war? */
    public boolean isTruncated() {
        return totalTypes > entries.size();
    }

    public static void handle(StorageSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorageView.accept(packet));
    }
}
