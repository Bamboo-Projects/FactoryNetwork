package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientDeployState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What came of the apply.
 *
 * <p><b>The editor must learn of it.</b> Previously the window closed on apply
 * and the outcome appeared as a line in the chat — so on a rejection you were
 * left outside and had to open the terminal again to look for what went wrong.
 *
 * <p>And two of the reasons only the server knows: "No server rack in the
 * network" and "The program is too large". These appear in no compile run the
 * client does itself — without this message they stay invisible.
 *
 * @param accepted whether the program was applied
 * @param message  a line for the editor's status bar
 */
public record DeployResultPacket(boolean accepted, String message)
        implements CustomPacketPayload {

    public static final Type<DeployResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "deploy_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeployResultPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DeployResultPacket::accepted,
                    ByteBufCodecs.stringUtf8(512), DeployResultPacket::message,
                    DeployResultPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeployResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeployState.accept(packet));
    }
}
