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
 * The network's stock for the storage view.
 *
 * <p>On opening, once in full; after that only the changes — and those
 * bundled, not one by one. A worker that moves something every tick must not
 * trigger a packet every tick.
 *
 * <p>Searching happens on the client over this snapshot. That is the reason
 * the search feels instant — and the reason for the cap: a pack with twenty
 * thousand types would otherwise turn every opening into a transfer you can
 * feel.
 */
public record StorageSnapshotPacket(List<Entry> entries, List<FluidEntry> fluids,
                                    boolean replace, int totalTypes,
                                    int freeTypes, int freeFluidTypes)
        implements CustomPacketPayload {

    // freeTypes and freeFluidTypes are the number you do not see without
    // help: a cell with all its type slots occupied takes nothing new anymore,
    // even though by amount it is nearly empty. Without the display you look
    // for the fault at the worker.

    /** This many types go over the wire at most. */
    public static final int MAX_ENTRIES = 4096;

    /**
     * An entry of the stock.
     *
     * <p><b>An item and not a key.</b> Otherwise an enchanted book and an empty
     * one would stand in the same line, and whoever took one out would get just
     * any one.
     */
    public record Entry(dev.devpanda.factorynetwork.storage.ItemKey key, long amount) {
    }

    /** A fluid with its amount in millibuckets. */
    public record FluidEntry(net.minecraft.world.level.material.Fluid fluid, long amount) {
    }

    public static final Type<StorageSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_snapshot"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
            StreamCodec.composite(
                    dev.devpanda.factorynetwork.storage.ItemKey.STREAM_CODEC,
                    Entry::key,
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
                    ByteBufCodecs.VAR_INT, StorageSnapshotPacket::freeTypes,
                    ByteBufCodecs.VAR_INT, StorageSnapshotPacket::freeFluidTypes,
                    StorageSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Was something omitted because it was too much? */
    public boolean isTruncated() {
        return totalTypes > entries.size();
    }

    public static void handle(StorageSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorageView.accept(packet));
    }
}
