package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.client.ClientFlowState;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkerStatePacketTest {
    @Test void reasonsSurviveTheWireAndReplaceTheLiveClientSnapshot() {
        var line = new FlowStatePacket.WorkerLine("ofen", "WAITING_BANDWIDTH", "", 128);
        var packet = new FlowStatePacket(List.of(), new FlowStatePacket.Compute(1, 0, 0, 100, 5, 100),
                new FlowStatePacket.Supply(0, 1000, 1000, 1, 0), List.of("modus = an"), List.of(line));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            FlowStatePacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = FlowStatePacket.STREAM_CODEC.decode(buffer);
            assertEquals(packet, decoded);
            ClientFlowState.accept(decoded);
            assertEquals(List.of(line), ClientFlowState.workers());
            ClientFlowState.clear();
            assertTrue(ClientFlowState.workers().isEmpty());
            assertTrue(ClientFlowState.globals().isEmpty());
            assertEquals(0, ClientFlowState.threads());
        } finally { buffer.release(); ClientFlowState.clear(); }
    }

    @Test void measuredControllerPauseOverridesOldRunningStatusWithoutMutatingRuntime() {
        var program = Parser.parse("worker ofen { from storage to machine }").program();
        var state = new WorkerRuntime.WorkerState();
        state.hasRun = true;
        state.status = WorkerRuntime.Status.RUNNING;
        state.detail = "64 moved";
        state.moved = 64;
        var paused = FlowStatePacket.WorkerLine.snapshot(program, Map.of("ofen", state), "WAITING_ENERGY");
        assertEquals("WAITING_ENERGY", paused.getFirst().status());
        assertEquals("", paused.getFirst().detail());
        assertEquals(64, paused.getFirst().moved());
        assertEquals(WorkerRuntime.Status.RUNNING, state.status);
        var resumed = FlowStatePacket.WorkerLine.snapshot(program, Map.of("ofen", state), "");
        assertEquals("RUNNING", resumed.getFirst().status());
    }

    @Test void neverRunWorkersAreVisibleAndDeletedWorkersDoNotLeakFromRuntime() {
        var program = Parser.parse("worker new_worker { from storage to machine }").program();
        var lines = FlowStatePacket.WorkerLine.snapshot(program, Map.of("old_worker", new WorkerRuntime.WorkerState()), "");
        assertEquals(1, lines.size());
        assertEquals("new_worker", lines.getFirst().name());
        assertEquals("NOT_RUN", lines.getFirst().status());
        assertEquals("screen.factorynetwork.terminal.worker.not_run", lines.getFirst().translationKey());
    }

    @Test void longDiagnosticTextIsBoundedBeforeEncoding() {
        var line = new FlowStatePacket.WorkerLine("worker", "HALTED", "detail ".repeat(300), 0);
        assertEquals(512, line.detail().length());
        assertTrue(line.detail().endsWith("…"));
    }
}
