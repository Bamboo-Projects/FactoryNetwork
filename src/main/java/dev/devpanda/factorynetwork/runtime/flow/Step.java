package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.runtime.Value;

/**
 * What should happen after a statement.
 *
 * <p>The trick that allows two execution modes from one logic: what a
 * statement <b>does</b> lives in one place; how execution <b>continues</b>
 * is up to the caller. The ordinary interpreter calls itself recursively,
 * the flow pushes a frame onto its stack — and both move items with the
 * same code.
 *
 * <p>Without this separation every statement would exist twice, and one of
 * the two versions would eventually drift apart.
 */
public sealed interface Step {

    /** Continue with the next statement. */
    record Next() implements Step {
        private static final Next INSTANCE = new Next();

        public static Next get() {
            return INSTANCE;
        }
    }

    /** Enter a block — a loop body, a conditional branch. */
    record Enter(Block block, boolean loop) implements Step {}

    /**
     * Iterate over a list.
     *
     * <p>A step of its own rather than merely an {@link Enter}, because the
     * list is evaluated once and must then be worked through round by round.
     * With {@code while} the condition lives in the program and is re-checked
     * every time; here there would be nothing from which the position could
     * be read off — so it moves into the frame and thereby onto disk.
     */
    record ForEach(Block body, String variable, java.util.List<Value> values)
            implements Step {}

    /** Leave the function. */
    record Return(Value value) implements Step {}

    /**
     * Call a user-defined function.
     *
     * <p>Without this step the called function would run to completion in the
     * ordinary interpreter — and could not wait there. A flow that calls a
     * function containing {@code await} thus gets a second frame on the same
     * stack; both are persisted together.
     *
     * <p>{@code resultName} is the name under which the return value lands in
     * the calling frame, or {@code null} for a call without an assignment.
     */
    record Invoke(java.util.List<String> parameters, java.util.List<Value> arguments,
                  Block body, String resultName, String devicePrefix) implements Step {}

    /** Leave the loop. */
    record Break() implements Step {}

    /** Go to the next round of the loop. */
    record Continue() implements Step {}

    /**
     * Wait until the game time is reached.
     *
     * <p>The time is absolute, not relative. While the server is down, no
     * game time passes — so a wait of thirty seconds does not elapse while
     * nobody is playing. That is the right meaning for Minecraft, but to
     * anyone thinking of a wall clock it looks like a bug.
     */
    record Sleep(long untilGameTime) implements Step {}

    /**
     * Wait for an event.
     *
     * <p>{@code deadline} is again absolute game time, or negative if no
     * deadline was set.
     */
    record Await(String event, Expr where, long deadline, Block elseBody,
                 String resultName) implements Step {}
}
