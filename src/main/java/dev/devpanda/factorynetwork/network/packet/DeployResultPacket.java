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
 * Was aus dem Übernehmen geworden ist.
 *
 * <p><b>Der Editor muss es erfahren.</b> Vorher schloss sich das Fenster beim
 * Übernehmen und der Ausgang stand als Zeile im Chat — bei einer Ablehnung
 * stand man also draußen und musste das Terminal wieder aufmachen, um zu
 * suchen, was nicht ging.
 *
 * <p>Und zwei der Gründe kennt nur der Server: „Kein Serverschrank im Netz"
 * und „Das Programm ist zu groß". Die stehen in keinem Übersetzungslauf, den
 * der Client selbst macht — ohne diese Nachricht bleiben sie unsichtbar.
 *
 * @param accepted ob das Programm übernommen wurde
 * @param message  eine Zeile für die Fußleiste des Editors
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
