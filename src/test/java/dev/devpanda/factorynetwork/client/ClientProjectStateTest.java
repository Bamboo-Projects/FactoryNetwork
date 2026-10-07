package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.packet.ProjectStatePacket;
import dev.devpanda.factorynetwork.network.packet.DraftSavedPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientProjectStateTest {
    @AfterEach void clear() { ClientProjectState.clear(); }

    private static ProjectStatePacket state(BlockPos pos, String text) {
        return ProjectStatePacket.of(pos, Project.of(text), Project.of(text), Map.of());
    }

    @Test void anotherControllerNeverInheritsAnUnsavedDraft() {
        ClientProjectState.accept(state(BlockPos.ZERO, "first"));
        ClientProjectState.setDraft(Project.of("unsaved first"));
        ClientProjectState.accept(state(new BlockPos(10, 0, 0), "second"));
        assertEquals("second", ClientProjectState.draft().source(Project.MAIN));
        assertFalse(ClientProjectState.isDirty());
    }

    @Test void sameControllerUpdateDoesNotReplaceTyping() {
        ClientProjectState.accept(state(BlockPos.ZERO, "first"));
        ClientProjectState.setDraft(Project.of("typing"));
        ClientProjectState.accept(state(BlockPos.ZERO, "server copy"));
        assertEquals("typing", ClientProjectState.draft().source(Project.MAIN));
        assertTrue(ClientProjectState.isDirty());
    }

    @Test void lateBroadcastCannotOverwriteAnInFlightSave() {
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        ClientProjectState.setDraft(Project.of("new"));
        var save = ClientProjectState.prepareSave();
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        assertEquals("new", ClientProjectState.draft().source(Project.MAIN));
        assertTrue(ClientProjectState.awaitingSave());
        ClientProjectState.acknowledge(new DraftSavedPacket(save.sequence(), true, "", state(BlockPos.ZERO, "new")));
        assertFalse(ClientProjectState.awaitingSave());
        assertEquals("new", ClientProjectState.draft().source(Project.MAIN));
    }

    @Test void acknowledgementNeverReplacesNewerTyping() {
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        ClientProjectState.setDraft(Project.of("sent"));
        var save = ClientProjectState.prepareSave();
        ClientProjectState.setDraft(Project.of("still typing"));
        ClientProjectState.acknowledge(new DraftSavedPacket(save.sequence(), true, "", state(BlockPos.ZERO, "sent")));
        assertEquals("still typing", ClientProjectState.draft().source(Project.MAIN));
        assertTrue(ClientProjectState.isDirty());
    }

    @Test void onlyTheNewestSaveAcknowledgementCounts() {
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        ClientProjectState.setDraft(Project.of("first"));
        var first = ClientProjectState.prepareSave();
        ClientProjectState.setDraft(Project.of("second"));
        var second = ClientProjectState.prepareSave();
        ClientProjectState.acknowledge(new DraftSavedPacket(first.sequence(), true, "", state(BlockPos.ZERO, "first")));
        assertTrue(ClientProjectState.awaitingSave());
        assertEquals("second", ClientProjectState.draft().source(Project.MAIN));
        ClientProjectState.acknowledge(new DraftSavedPacket(second.sequence(), true, "", state(BlockPos.ZERO, "second")));
        assertFalse(ClientProjectState.awaitingSave());
    }

    @Test void rejectedSaveKeepsTheLocalWorkAndReportsFailure() {
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        ClientProjectState.setDraft(Project.of("valuable work"));
        var save = ClientProjectState.prepareSave();
        ClientProjectState.acknowledge(new DraftSavedPacket(save.sequence(), false,
                "Keine Energie", state(BlockPos.ZERO, "old")));
        ClientProjectState.accept(state(BlockPos.ZERO, "old"));
        assertEquals("valuable work", ClientProjectState.draft().source(Project.MAIN));
        assertEquals("Keine Energie", ClientProjectState.saveError());
        assertTrue(ClientProjectState.isDirty());
        assertFalse(ClientProjectState.awaitingSave());
    }
}
