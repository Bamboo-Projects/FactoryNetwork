package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persists waiting flows and brings them back.
 *
 * <p><b>This is where the mod's promise is kept:</b> a flow that is waiting
 * for an event stands in the same place again after a server restart — with
 * the same variables, in the same loop round, in the same branch.
 *
 * <p>As little as possible is written. What is in the program comes back
 * from the program: the {@code where} clause, the {@code else} branch, the
 * body of a loop. Only what the flow itself has experienced — where it
 * stands, what it holds in its hands, what it is waiting for — has to go to
 * disk.
 */
public final class FlowCodec {

    private static final String KEY_FLOWS = "flows";
    private static final String KEY_NEXT_ID = "nextId";
    private static final String KEY_ID = "id";
    private static final String KEY_ENTRY = "entry";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DETAIL = "detail";
    private static final String KEY_RESULT = "result";
    private static final String KEY_WAKE_AT = "wakeAt";
    private static final String KEY_EVENT = "event";
    private static final String KEY_DEADLINE = "deadline";
    private static final String KEY_RESULT_NAME = "resultName";
    private static final String KEY_HASH = "hash";
    private static final String KEY_STACK = "stack";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_INDEX = "index";
    private static final String KEY_LOOP = "loop";
    private static final String KEY_EXIT = "exit";
    private static final String KEY_LOCALS = "locals";
    private static final String KEY_CALL = "call";
    private static final String KEY_RESULT_INTO = "into";
    private static final String KEY_PREFIX = "prefix";
    private static final String KEY_ITER_VAR = "iterVar";
    private static final String KEY_ITER_VALUES = "iterValues";
    private static final String KEY_ITER_INDEX = "iterIndex";
    private static final String KEY_NAME = "n";
    private static final String KEY_VALUE = "v";

    private FlowCodec() {
    }

    // ---- Writing ----------------------------------------------------------

    public static CompoundTag write(FlowEngine engine) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_NEXT_ID, engine.nextId());
        ListTag list = new ListTag();
        for (Flow flow : engine.flows().values()) {
            // Finished flows do not come back; persisting them would mean
            // throwing them away immediately on load.
            if (flow.isFinished()) {
                continue;
            }
            CompoundTag written = write(flow, engine.blocks());
            if (written != null) {
                list.add(written);
            }
        }
        tag.put(KEY_FLOWS, list);
        return tag;
    }

    private static CompoundTag write(Flow flow, BlockIndex blocks) {
        ListTag stack = new ListTag();
        // Bottom to top, so that on loading the frames end up stacked in the
        // same order again.
        for (var frames = flow.stack().descendingIterator(); frames.hasNext(); ) {
            Frame frame = frames.next();
            int id = blocks.id(frame.block());
            if (id < 0) {
                // The block does not belong to this program — then the flow
                // cannot be found again, and persisting it halfway would be
                // worse than losing it.
                return null;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_BLOCK, id);
            entry.putInt(KEY_INDEX, frame.index());
            entry.putBoolean(KEY_LOOP, frame.isLoop());
            entry.putBoolean(KEY_EXIT, frame.exitOnLeave());
            entry.put(KEY_LOCALS, writeLocals(frame.locals()));
            if (frame.isCall()) {
                entry.putBoolean(KEY_CALL, true);
                if (frame.resultName() != null) {
                    entry.putString(KEY_RESULT_INTO, frame.resultName());
                }
            }
            if (!frame.devicePrefix().isEmpty()) {
                // Without it, after the restart a flow would no longer know
                // which of the three multiblock instances it is serving.
                entry.putString(KEY_PREFIX, frame.devicePrefix());
            }
            if (frame.hasIteration()) {
                // The position of an iteration over a list lives only here.
                // Without it the loop would start over after a restart — and
                // do everything a second time.
                entry.putString(KEY_ITER_VAR, frame.iterationVariable());
                entry.putInt(KEY_ITER_INDEX, frame.iterationIndex());
                ListTag values = new ListTag();
                frame.iterationValues().forEach(value -> values.add(ValueCodec.write(value)));
                entry.put(KEY_ITER_VALUES, values);
            }
            stack.add(entry);
        }

        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_ID, flow.id());
        tag.putString(KEY_ENTRY, flow.entryPoint());
        tag.putString(KEY_STATUS, flow.status().name());
        tag.putString(KEY_DETAIL, flow.detail());
        tag.put(KEY_RESULT, ValueCodec.write(flow.result()));
        tag.putLong(KEY_WAKE_AT, flow.wakeAt());
        tag.putString(KEY_EVENT, flow.awaitedEvent() == null ? "" : flow.awaitedEvent());
        tag.putLong(KEY_DEADLINE, flow.awaitDeadline());
        tag.putString(KEY_RESULT_NAME,
                flow.awaitResultName() == null ? "" : flow.awaitResultName());
        tag.putInt(KEY_HASH, flow.structureHash());
        tag.put(KEY_STACK, stack);
        return tag;
    }

    private static ListTag writeLocals(Map<String, Value> locals) {
        ListTag list = new ListTag();
        locals.forEach((name, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_NAME, name);
            entry.put(KEY_VALUE, ValueCodec.write(value));
            list.add(entry);
        });
        return list;
    }

    /** Does this tag contain not a single flow? */
    public static boolean isEmpty(CompoundTag tag) {
        return tag == null || tag.getList(KEY_FLOWS, Tag.TAG_COMPOUND).isEmpty();
    }

    // ---- Reading ----------------------------------------------------------

    /**
     * Brings the flows back into an engine.
     *
     * <p>If the shape of the program no longer matches, the flow becomes
     * {@code STALE}. Its stack is kept as far as it can be resolved — the
     * player should be able to see where the flow stood before deciding.
     */
    public static void read(CompoundTag tag, FlowEngine engine) {
        engine.setNextId(Math.max(1, tag.getLong(KEY_NEXT_ID)));
        int currentHash = engine.blocks().structureHash();
        ListTag list = tag.getList(KEY_FLOWS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Flow flow = read(list.getCompound(i), engine.blocks(), currentHash);
            if (flow != null) {
                engine.adopt(flow);
            }
        }
    }

    private static Flow read(CompoundTag tag, BlockIndex blocks, int currentHash) {
        Flow flow = new Flow(tag.getLong(KEY_ID), tag.getString(KEY_ENTRY));
        int savedHash = tag.getInt(KEY_HASH);
        flow.setStructureHash(savedHash);

        try {
            ListTag stack = tag.getList(KEY_STACK, Tag.TAG_COMPOUND);
            for (int i = 0; i < stack.size(); i++) {
                flow.push(readFrame(stack.getCompound(i), blocks));
            }
        } catch (ScriptError error) {
            // A value could not be read back. The flow is done for, but it
            // should say so rather than vanish without a trace.
            flow.fail(error.toString());
            return flow;
        }

        if (flow.stack().isEmpty()) {
            return null;
        }

        Flow.Status status = statusOf(tag.getString(KEY_STATUS));
        String event = tag.getString(KEY_EVENT);
        String resultName = tag.getString(KEY_RESULT_NAME);
        flow.restore(status, tag.getString(KEY_DETAIL), ValueCodec.read(tag.getCompound(KEY_RESULT)),
                tag.getLong(KEY_WAKE_AT), event.isEmpty() ? null : event,
                tag.getLong(KEY_DEADLINE), resultName.isEmpty() ? null : resultName);

        if (savedHash != currentHash) {
            flow.markStale();
        }
        return flow;
    }

    private static Frame readFrame(CompoundTag tag, BlockIndex blocks) {
        int id = tag.getInt(KEY_BLOCK);
        Block block = blocks.block(id);
        if (block == null) {
            throw new ScriptError("Der Ablauf stand in einem Block, den es nicht mehr gibt.",
                    "Das Programm hat sich stärker geändert, als sich nachvollziehen lässt.");
        }
        Frame frame = new Frame(block, tag.getBoolean(KEY_LOOP));
        // The counter may point past the end — then the first thing the flow
        // does is leave the frame. It must not point any further.
        frame.setIndex(Math.max(0, Math.min(tag.getInt(KEY_INDEX), block.statements().size())));
        frame.setExitOnLeave(tag.getBoolean(KEY_EXIT));
        ListTag locals = tag.getList(KEY_LOCALS, Tag.TAG_COMPOUND);
        for (int i = 0; i < locals.size(); i++) {
            CompoundTag entry = locals.getCompound(i);
            frame.locals().put(entry.getString(KEY_NAME),
                    ValueCodec.read(entry.getCompound(KEY_VALUE)));
        }
        if (tag.getBoolean(KEY_CALL)) {
            frame.beginCall(tag.contains(KEY_RESULT_INTO) ? tag.getString(KEY_RESULT_INTO) : null,
                    tag.getString(KEY_PREFIX));
        } else if (tag.contains(KEY_PREFIX)) {
            frame.setDevicePrefix(tag.getString(KEY_PREFIX));
        }
        if (tag.contains(KEY_ITER_VAR)) {
            ListTag values = tag.getList(KEY_ITER_VALUES, Tag.TAG_COMPOUND);
            List<Value> entries = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                entries.add(ValueCodec.read(values.getCompound(i)));
            }
            frame.restoreIteration(tag.getString(KEY_ITER_VAR), entries,
                    tag.getInt(KEY_ITER_INDEX));
        }
        return frame;
    }

    private static Flow.Status statusOf(String name) {
        for (Flow.Status status : Flow.Status.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return Flow.Status.RUNNING;
    }

    /** The frames of a flow, bottom to top, as a list. */
    public static List<Frame> framesBottomUp(Flow flow) {
        List<Frame> frames = new ArrayList<>();
        flow.stack().descendingIterator().forEachRemaining(frames::add);
        return frames;
    }
}
