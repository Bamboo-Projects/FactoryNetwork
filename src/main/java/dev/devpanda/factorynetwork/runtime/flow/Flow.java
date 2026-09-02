package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.runtime.Value;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flow that can wait.
 *
 * <p><b>This is the promise the mod is being built for:</b> a flow that is
 * waiting for an event picks up exactly there after a server restart. That
 * is possible because its state does not live in Java's call stack but in
 * frames that can be persisted.
 *
 * <p>Not every function needs this. One that only computes and moves items
 * runs through the ordinary interpreter — faster and without bookkeeping.
 * Only {@code await} and {@code sleep} turn it into a flow.
 */
public final class Flow {

    public enum Status {
        /** Running and wants to take steps. */
        RUNNING,
        /**
         * Queued: no free processor is available.
         *
         * <p>Not rejected, but postponed. <b>Delay is recoverable, loss is
         * not</b> — a rejected {@code device_changed} is gone for good, and
         * the items sit until the next restart in a machine nobody touches
         * any more.
         */
        QUEUED,
        /** Waiting for a particular game time. */
        SLEEPING,
        /** Waiting for an event. */
        AWAITING,
        /** Finished. */
        DONE,
        /** Aborted, with a reason. */
        FAILED,
        /**
         * The program changed while this flow was waiting.
         *
         * <p>It is neither silently resumed nor silently discarded, but shows
         * up in the terminal with a choice: abort or let it continue. That is
         * how it was decided.
         */
        STALE
    }

    private final long id;
    private final String entryPoint;
    private final Deque<Frame> stack = new ArrayDeque<>();

    private Status status = Status.RUNNING;
    private String detail = "";
    private Value result = Value.Nothing.get();

    /** What is being waited for. */
    private long wakeAt = -1;
    private String awaitedEvent;
    private long awaitDeadline = -1;
    private String awaitResultName;

    public Flow(long id, String entryPoint) {
        this.id = id;
        this.entryPoint = entryPoint;
    }

    public long id() {
        return id;
    }

    /** The function or event block it started with. */
    public String entryPoint() {
        return entryPoint;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public Value result() {
        return result;
    }

    public Deque<Frame> stack() {
        return stack;
    }

    public Frame top() {
        return stack.peek();
    }

    public void push(Frame frame) {
        stack.push(frame);
    }

    public Frame pop() {
        return stack.pop();
    }

    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    // ---- State transitions ------------------------------------------------

    public void sleepUntil(long gameTime) {
        status = Status.SLEEPING;
        wakeAt = gameTime;
        detail = "schläft";
    }

    public void awaitEvent(String event, long deadline, String resultName) {
        status = Status.AWAITING;
        awaitedEvent = event;
        awaitDeadline = deadline;
        awaitResultName = resultName;
        detail = "wartet auf " + event;
    }

    public void finish(Value value) {
        status = Status.DONE;
        result = value;
        detail = "fertig";
    }

    public void fail(String reason) {
        status = Status.FAILED;
        detail = reason;
    }

    /** The flow was waiting when the program was swapped. */
    /** Queues the flow: no slot is available. */
    public void queue(String reason) {
        status = Status.QUEUED;
        detail = reason;
    }

    /** Takes it back out of the queue. */
    public void dequeue() {
        status = Status.RUNNING;
        detail = "";
    }

    public void markStale() {
        status = Status.STALE;
        detail = "Programm geändert, während dieser Ablauf wartete";
    }

    /**
     * Takes a {@code STALE} flow back up.
     *
     * <p>The player's choice: let it continue rather than abort. Where it
     * returns to is recorded in its own fields — what it was waiting for was
     * not lost when it was halted.
     */
    public void unstale(int structureHash) {
        this.structureHash = structureHash;
        if (awaitedEvent != null) {
            status = Status.AWAITING;
            detail = "wartet auf " + awaitedEvent;
        } else if (wakeAt >= 0) {
            status = Status.SLEEPING;
            detail = "schläft";
        } else {
            status = Status.RUNNING;
            detail = "";
        }
    }

    public void resume() {
        status = Status.RUNNING;
        awaitedEvent = null;
        awaitDeadline = -1;
        wakeAt = -1;
        detail = "";
    }

    /**
     * The shape of the program this flow started with.
     *
     * <p>After a restart or a new program, does the flow still point at the
     * same places? This number answers that. If it no longer matches, the
     * flow becomes {@code STALE} — neither silently resumed nor silently
     * discarded.
     */
    private int structureHash;

    public int structureHash() {
        return structureHash;
    }

    public void setStructureHash(int structureHash) {
        this.structureHash = structureHash;
    }

    /** Puts the saved waiting state back into the flow. */
    public void restore(Status status, String detail, Value result, long wakeAt,
            String awaitedEvent, long awaitDeadline, String awaitResultName) {
        this.status = status;
        this.detail = detail;
        this.result = result;
        this.wakeAt = wakeAt;
        this.awaitedEvent = awaitedEvent;
        this.awaitDeadline = awaitDeadline;
        this.awaitResultName = awaitResultName;
    }

    // ---- Waiting ----------------------------------------------------------

    /** Has the time that was waited for arrived? */
    public boolean isDue(long gameTime) {
        return status == Status.SLEEPING && gameTime >= wakeAt;
    }

    /** Is this flow waiting for this event? */
    public boolean waitsFor(String event) {
        return status == Status.AWAITING && event.equals(awaitedEvent);
    }

    /** Has the deadline passed? */
    public boolean hasTimedOut(long gameTime) {
        return status == Status.AWAITING && awaitDeadline >= 0 && gameTime >= awaitDeadline;
    }

    public String awaitedEvent() {
        return awaitedEvent;
    }

    public long awaitDeadline() {
        return awaitDeadline;
    }

    public long wakeAt() {
        return wakeAt;
    }

    /** The name under which the result of the wait is stored. */
    public String awaitResultName() {
        return awaitResultName;
    }

    /** Which multiblock instance the flow currently belongs to, or empty. */
    public String devicePrefix() {
        Frame frame = top();
        return frame == null ? "" : frame.devicePrefix();
    }

    /** Puts a value into the topmost frame — the result of an await. */
    public void bind(String name, Value value) {
        if (name != null && top() != null) {
            top().locals().put(name, value);
        }
    }

    /**
     * Looks up a name from the inside out.
     *
     * <p>The same rule as in the ordinary interpreter: the innermost frame
     * wins. Only here the stack is laid out as a list instead of being buried
     * in Java calls.
     */
    public Value find(String name) {
        for (Frame frame : stack) {
            Value value = frame.locals().get(name);
            if (value != null) {
                return value;
            }
            if (frame.isCall()) {
                // Further down lies the caller. Its names are none of the
                // called function's business — otherwise the behaviour would
                // depend on who happens to be calling it.
                return null;
            }
        }
        return null;
    }

    public boolean assign(String name, Value value) {
        for (Frame frame : stack) {
            if (frame.locals().containsKey(name)) {
                frame.locals().put(name, value);
                return true;
            }
            if (frame.isCall()) {
                return false;
            }
        }
        return false;
    }

    /** All visible names — for persisting and for display. */
    public Map<String, Value> visibleLocals() {
        Map<String, Value> all = new LinkedHashMap<>();
        stack.descendingIterator().forEachRemaining(frame -> all.putAll(frame.locals()));
        return all;
    }

    @Override
    public String toString() {
        return entryPoint + "#" + id + " " + status + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
