package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Das ganze Projekt zum Client.
 *
 * <p>Der Netzzustand trug bisher den Quelltext als eine Zeichenkette mit.
 * Ein Projekt aus mehreren Dateien passt dort nicht hinein, und es gehört
 * auch nicht dazu: Wer den Netzreiter ansieht, braucht keinen Code, und wer
 * im Editor sitzt, braucht keine Connectorliste.
 *
 * <p>Geschickt wird es beim Öffnen des Terminals und nach jedem Übernehmen —
 * nicht laufend. Ein Projekt ändert sich, wenn jemand es ändert.
 *
 * <p>{@code locks} sagt, welche Datei gerade jemand anders bearbeitet, und
 * wer. Die eigenen stehen nicht darin — dass man selbst schreibt, ist keine
 * Nachricht.
 *
 * <p><b>Zwei Stände, weil es zwei Wahrheiten gibt.</b> {@code files} ist das
 * Programm, das läuft; {@code draft} ist das, was zuletzt im Editor stand.
 * Solange beide gleich sind, ist nichts offen. Der Entwurf darf kaputt sein,
 * der laufende Stand nicht — ein Tippfehler hält die Fabrik nicht an.
 */
public record ProjectStatePacket(BlockPos controller, Map<String, String> files,
                                 Map<String, String> draft, Map<String, String> locks)
        implements CustomPacketPayload {

    /** So lang darf eine einzelne Datei sein. */
    private static final int MAX_FILE = 64 * 1024;

    public static final Type<ProjectStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "project_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ProjectStatePacket::controller,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(64),
                            ByteBufCodecs.stringUtf8(MAX_FILE)),
                    ProjectStatePacket::files,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(64),
                            ByteBufCodecs.stringUtf8(MAX_FILE)),
                    ProjectStatePacket::draft,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(64),
                            ByteBufCodecs.stringUtf8(64)),
                    ProjectStatePacket::locks,
                    ProjectStatePacket::new);

    public static ProjectStatePacket of(BlockPos controller, Project project, Project draft,
                                        Map<String, String> locks) {
        return new ProjectStatePacket(controller, project.files(), draft.files(), locks);
    }

    /** Was im Controller läuft. */
    public Project project() {
        return new Project(files);
    }

    /** Was im Editor stand, als zuletzt gesichert wurde. */
    public Project draftProject() {
        return new Project(draft);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProjectStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProjectState.accept(packet));
    }
}
