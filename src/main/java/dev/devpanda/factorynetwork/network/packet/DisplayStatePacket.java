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
 * Die Anzeigen des Programms, fertig ausgewertet.
 *
 * <p>Gerechnet wird auf dem Server — dieselbe Regel wie beim Display an der
 * Wand. Der Client bekommt Zeilen, die schon dastehen, und muss die Sprache
 * nicht kennen.
 *
 * <p>Welche Zeilen Knöpfe sind, steht daneben. Der Client schickt beim Klick
 * nur die Nummer zurück; welche Funktion dahintersteht, entscheidet der
 * Server. Einen Funktionsnamen vom Client zu glauben hieße, jedem Spieler
 * jeden Aufruf zu erlauben.
 */
public record DisplayStatePacket(List<Panel> panels) implements CustomPacketPayload {

    /** Eine Anzeige mit ihren Zeilen. */
    public record Panel(String name, List<String> lines, List<Button> buttons) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(128), Panel::name,
                        ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(64)), Panel::lines,
                        Button.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Panel::buttons,
                        Panel::new);
    }

    /**
     * Ein Knopf: die Zeile, auf die man klickt, und der Eintrag, den das
     * meint.
     *
     * <p><b>Zwei Nummern, weil es zwei Dinge sind.</b> Der Reiter trifft eine
     * <i>Zeile</i>, der Controller führt einen <i>Eintrag</i> aus. Solange
     * jeder Eintrag genau eine Zeile war, waren beide dieselbe Zahl — bis
     * {@code list} kam und eine Aufzählung mehrere Zeilen belegte. Ab da zeigt
     * eine Zeilennummer auf den falschen Eintrag, und der Knopf löst aus, was
     * darüber steht.
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
