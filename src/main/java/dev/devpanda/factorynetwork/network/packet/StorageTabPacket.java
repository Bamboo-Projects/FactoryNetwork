package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Meldet, ob der Speicher-Reiter offen ist.
 *
 * <p>Welcher Reiter angezeigt wird, ist sonst reine Client-Sache. Diese eine
 * Ausnahme gibt es, weil der Server nur dann Bestandsänderungen schicken soll,
 * wenn auch jemand hinsieht — wer Code liest, braucht keine Paketflut.
 */
public record StorageTabPacket(boolean open) implements CustomPacketPayload {

    public static final Type<StorageTabPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageTabPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, StorageTabPacket::open, StorageTabPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageTabPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu) {
                menu.setStorageTabOpen(player, packet.open());
            }
        });
    }
}
