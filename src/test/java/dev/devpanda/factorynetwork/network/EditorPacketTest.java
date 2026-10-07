package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.DraftSavedPacket;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;
import dev.devpanda.factorynetwork.network.packet.SaveDraftPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorPacketTest {
    @Test void nestedPathsAndSaveSequenceSurviveTheWire() {
        String name = "a".repeat(32) + "/" + "b".repeat(32) + "/main.mf";
        Project project = new Project(Map.of(name, "fn beispiel() { log(\"Grüße 🌿\") }"));
        var save = SaveDraftPacket.of(new BlockPos(10, 20, 30), project, 42);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveDraftPacket.STREAM_CODEC.encode(buffer, save);
            assertEquals(save, SaveDraftPacket.STREAM_CODEC.decode(buffer));
            var state = ProjectStatePacket.of(save.controller(), Project.of("old"), project, Map.of(name, "Alex"));
            var result = new DraftSavedPacket(42, true, "", state);
            DraftSavedPacket.STREAM_CODEC.encode(buffer, result);
            assertEquals(result, DraftSavedPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
