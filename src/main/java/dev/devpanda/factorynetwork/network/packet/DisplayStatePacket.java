package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientDisplayState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * The program's displays, fully evaluated.
 *
 * <p>The computation happens on the server — the same rule as for the display
 * on the wall. The client receives lines that are already there, and need not
 * know the language.
 *
 * <p>Which lines are buttons is noted alongside. On a click the client only
 * sends the number back; which function is behind it, the server decides.
 * Trusting a function name from the client would mean allowing every player
 * every call.
 */
public record DisplayStatePacket(List<Panel> panels) implements CustomPacketPayload {

    /** A display with its lines. */
    public record Panel(String name, List<String> lines, List<Button> buttons) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(128), Panel::name,
                        ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(64)), Panel::lines,
                        Button.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Panel::buttons,
                        Panel::new);
    }

    /**
     * A button: the line you click on, and the entry it refers to.
     *
     * <p><b>Two numbers, because they are two things.</b> The tab hits a
     * <i>line</i>, the controller runs an <i>entry</i>. As long as each entry
     * was exactly one line, both were the same number — until {@code list}
     * came along and one listing occupied several lines. From then on a line
     * number points to the wrong entry, and the button triggers whatever is
     * above it.
     */
    public record Button(int line, int entry) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Button> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Button::line,
                        ByteBufCodecs.VAR_INT, Button::entry,
                        Button::new);
    }

    public static final Type<DisplayStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "display_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    Panel.STREAM_CODEC.apply(ByteBufCodecs.list(32)), DisplayStatePacket::panels,
                    DisplayStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisplayStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDisplayState.accept(packet));
    }
}
