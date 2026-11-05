package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.ProgramDiskItem;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/**
 * A program fetched from the share, onto the disk in the player's hand.
 *
 * <p>The client fetches, the server writes: the disk is the server's. What
 * arrives is checked like anything typed in the editor — valid file names,
 * the same size limit — and only ever lands on a disk, never in a network.
 */
public record DiskWritePacket(String title, Map<String, String> files) implements CustomPacketPayload {

    private static final int MAX_LENGTH = 64 * 1024;
    private static final int MAX_FILES = 64;

    public static final Type<DiskWritePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "disk_write"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskWritePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), DiskWritePacket::title,
                    ByteBufCodecs.map(java.util.HashMap::new,
                            ByteBufCodecs.stringUtf8(Project.MAX_NAME),
                            ByteBufCodecs.stringUtf8(MAX_LENGTH), MAX_FILES),
                    DiskWritePacket::files,
                    DiskWritePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiskWritePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !dev.devpanda.factorynetwork.FnConfig.programShare()) {
                return;
            }
            var disk = player.getMainHandItem();
            if (!(disk.getItem() instanceof ProgramDiskItem) || packet.files().isEmpty()) {
                return;
            }
            for (String name : packet.files().keySet()) {
                if (!Project.isValidName(name)) {
                    return;
                }
            }
            // Someone else's program: an upload from this disk must not
            // overwrite the share it held before.
            ProgramDiskItem.setShare(disk, null);
            ProgramDiskItem.write(disk, new Project(Map.copyOf(packet.files())));
            if (!packet.title().isBlank()) {
                disk.set(DataComponents.CUSTOM_NAME, Component.literal(packet.title()));
            }
        });
    }
}
