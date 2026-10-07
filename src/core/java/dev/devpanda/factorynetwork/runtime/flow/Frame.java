package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.runtime.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A frame on a flow's stack.
 *
 * <p>It knows which block it is in and at which statement. That is the whole
 * difference from Java's ordinary call stack: this information is data and
 * can be persisted. A waiting flow is thus nothing more than a list of such
 * frames — and that survives a server restart.
 *
 * <p>Which block it is becomes, when persisted, the number from the
 * {@link BlockIndex}. The frame itself carries no number; a number is only
 * valid for a particular program, whereas the frame lives in memory.
 */
public final class Frame {

    private final Block block;
    private final Map<String, Value> locals = new LinkedHashMap<>();
    private final boolean loop;
    private int index;
    private boolean exitOnLeave;

    public Frame(Block block, boolean loop) {
        this.block = block;
        this.loop = loop;
    }

    public Block block() {
        return block;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void advance() {
        index++;
    }

    /** Is this frame a loop body? */
    public boolean isLoop() {
        return loop;
    }

    /**
     * Does the whole flow end as soon as this frame is finished?
     *
     * <p>Set for the {@code else} branch of an {@code await} with a deadline.
     * The language requires that branch to leave the flow — if there is no
     * {@code return} in it, this flag makes sure the flow ends anyway instead
     * of continuing after the {@code await} with a value that never existed.
     */
    public boolean exitOnLeave() {
        return exitOnLeave;
    }

    public void setExitOnLeave(boolean exitOnLeave) {
        this.exitOnLeave = exitOnLeave;
    }

    // ---- Calling a user-defined function ----------------------------------

    private boolean call;
    private String resultName;
    private String devicePrefix = "";

    /**
     * Turns this frame into the body of a call.
     *
     * <p>A {@code return} inside it does not end the whole flow, only this
     * frame — the value lands in the calling frame under {@code resultName}.
     */
    public void beginCall(String resultName, String devicePrefix) {
        this.call = true;
        this.resultName = resultName;
        this.devicePrefix = devicePrefix == null ? "" : devicePrefix;
    }

    public boolean isCall() {
        return call;
    }

    public String resultName() {
        return resultName;
    }

    /**
     * What this frame prepends to device names.
     *
     * <p>In a template a device is called {@code crusher}; in the world it
     * carries the name of the multiblock instance in front. The frame knows
     * which instance it belongs to — otherwise, after a restart, a flow would
     * no longer know which of the three ore plants it is serving.
     */
    public String devicePrefix() {
        return devicePrefix;
    }

    public void setDevicePrefix(String devicePrefix) {
        this.devicePrefix = devicePrefix == null ? "" : devicePrefix;
    }

    // ---- Iterating over a list --------------------------------------------

    private String iterationVariable;
    private java.util.List<Value> iterationValues = java.util.List.of();
    private int iterationIndex;

    /**
     * Turns this frame into the body of a {@code for}.
     *
     * <p>The position thereby lives in the frame and not in the program —
     * unlike {@code while}, where the condition is re-checked every round.
     * Only this way can a {@code for} that waits for an event in the middle
     * of the list be persisted and resumed.
     */
    public void beginIteration(String variable, java.util.List<Value> values) {
        this.iterationVariable = variable;
        this.iterationValues = java.util.List.copyOf(values);
        this.iterationIndex = 0;
        bindCurrent();
    }

    /** Only for reading back: a position in the middle of the list. */
    public void restoreIteration(String variable, java.util.List<Value> values, int index) {
        this.iterationVariable = variable;
        this.iterationValues = java.util.List.copyOf(values);
        this.iterationIndex = index;
    }

    public boolean hasIteration() {
        return iterationVariable != null;
    }

    public String iterationVariable() {
        return iterationVariable;
    }

    public java.util.List<Value> iterationValues() {
        return iterationValues;
    }

    public int iterationIndex() {
        return iterationIndex;
    }

    /**
     * Advances to the next entry.
     *
     * @return whether there was another one
     */
    public boolean nextIteration() {
        if (!hasIteration() || iterationIndex + 1 >= iterationValues.size()) {
            return false;
        }
        iterationIndex++;
        bindCurrent();
        return true;
    }

    private void bindCurrent() {
        if (iterationIndex < iterationValues.size()) {
            locals.put(iterationVariable, iterationValues.get(iterationIndex));
        }
    }

    public boolean atEnd() {
        return index >= block.statements().size();
    }

    public Map<String, Value> locals() {
        return locals;
    }

    /** Resets the counter — for the next round of a loop. */
    public void restart() {
        index = 0;
    }
}
