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
                                    boolean replace, int totalTypes,
                                    int freeTypes, int freeFluidTypes)
        implements CustomPacketPayload {

    // freeTypes und freeFluidTypes sind die Zahl, die man ohne Hilfe nicht
    // sieht: Eine Zelle mit allen Artenplätzen belegt nimmt nichts Neues mehr
    // an, obwohl sie nach Menge fast leer ist. Ohne die Anzeige sucht man den
    // Fehler beim Worker.

    /** So viele Arten gehen höchstens über die Leitung. */
    public static final int MAX_ENTRIES = 4096;

    /**
     * Ein Posten des Bestands.
     *
     * <p><b>Ein Gegenstand und keine Kennung.</b> Sonst stünden ein
     * verzaubertes Buch und ein leeres in derselben Zeile, und wer eines
     * herausnähme, bekäme irgendeines.
     */
    public record Entry(dev.devpanda.factorynetwork.storage.ItemKey key, long amount) {
    }

    /** Eine Flüssigkeit mit ihrer Menge in Millibucket. */
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

    /** Wurde etwas weggelassen, weil es zu viel war? */
    public boolean isTruncated() {
        return totalTypes > entries.size();
    }

    public static void handle(StorageSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorageView.accept(packet));
    }
}
