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
 * The whole project to the client.
 *
 * <p>The network state used to carry the source text along as a single string.
 * A project of several files does not fit in there, and it does not belong
 * there either: whoever looks at the network tab needs no code, and whoever
 * sits in the editor needs no connector list.
 *
 * <p>It is sent on opening the terminal and after every apply — not
 * continuously. A project changes when someone changes it.
 *
 * <p>{@code locks} says which file someone else is currently editing, and who.
 * One's own are not in it — that you are writing yourself is no news.
 *
 * <p><b>Two states, because there are two truths.</b> {@code files} is the
 * program that runs; {@code draft} is what last stood in the editor. As long
 * as both are equal, nothing is pending. The draft may be broken, the running
 * state may not — a typo does not stop the factory.
 */
public record ProjectStatePacket(BlockPos controller, Map<String, String> files,
                                 Map<String, String> draft, Map<String, String> locks)
        implements CustomPacketPayload {

    /** This is how long a single file may be. */
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

    /** What runs in the controller. */
    public Project project() {
        return new Project(files);
    }

    /** What stood in the editor when it was last saved. */
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
