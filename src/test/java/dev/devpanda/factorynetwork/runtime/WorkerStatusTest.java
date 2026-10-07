package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerStatusTest {
    private Decl.Worker worker(String entries) {
        return Parser.parse("worker test {\n" + entries + "\n}").program().workers().getFirst();
    }

    @Test void falseConditionReplacesThePreviousReasonWithoutRunningATransfer() {
        var state = new WorkerRuntime.WorkerState();
        state.status = WorkerRuntime.Status.WAITING_TARGET;
        state.detail = "old target failure";
        new WorkerRuntime().runWorker(null, worker("from storage\nto machine\nwhen false"), state, null, null);
        assertEquals(WorkerRuntime.Status.WAITING_CONDITION, state.status);
        assertEquals("", state.detail);
        assertTrue(state.hasRun);
        assertEquals(0, state.moved);
    }

    @Test void aRecoveredWorkerIsNotPermanentlyLatchedByAMissingDevice() {
        var state = new WorkerRuntime.WorkerState();
        state.status = WorkerRuntime.Status.HALTED;
        state.detail = "Unbekannter Connector";
        new WorkerRuntime().runWorker(null, worker("from storage\nto storage"), state, null, null);
        assertEquals(WorkerRuntime.Status.IDLE, state.status);
        assertEquals("Quelle und Ziel sind beide der Speicher", state.detail);
    }

    @Test void evaluationErrorsAreNotRelabeledAsAnUnmetCondition() {
        var state = new WorkerRuntime.WorkerState();
        state.status = WorkerRuntime.Status.HALTED;
        state.detail = "division by zero";
        state.conditionRejected();
        assertEquals(WorkerRuntime.Status.HALTED, state.status);
        assertEquals("division by zero", state.detail);
    }

    @Test void successfulNextAttemptClearsTargetWaitAndUpdatesMovedCount() {
        var state = new WorkerRuntime.WorkerState();
        state.status = WorkerRuntime.Status.WAITING_TARGET;
        state.detail = "target rejected stack";
        state.beginAttempt();
        state.recordTransfer(8, "items");
        assertEquals(WorkerRuntime.Status.RUNNING, state.status);
        assertEquals("8 items", state.detail);
        assertEquals(8, state.moved);
    }

    @Test void measuredWaitsSurviveTheCommonTransferFinalizer() {
        for (var reason : new WorkerRuntime.Status[] { WorkerRuntime.Status.WAITING_SOURCE,
                WorkerRuntime.Status.WAITING_BANDWIDTH, WorkerRuntime.Status.WAITING_ENERGY,
                WorkerRuntime.Status.WAITING_TARGET }) {
            var state = new WorkerRuntime.WorkerState();
            state.status = reason;
            state.detail = "measured";
            state.recordTransfer(0, "items");
            assertEquals(reason, state.status);
            assertEquals("measured", state.detail);
        }
    }

    @Test void strandedResourcesDoNotTriggerRepeatedUnsafeTransfers() {
        var state = new WorkerRuntime.WorkerState();
        state.status = WorkerRuntime.Status.HALTED;
        state.detail = "3 blieben im Ziel";
        state.faultLatched = true;
        new WorkerRuntime().runWorker(null, worker("from storage\nto storage"), state, null, null);
        assertEquals(WorkerRuntime.Status.HALTED, state.status);
        assertEquals("3 blieben im Ziel", state.detail);
    }
}
