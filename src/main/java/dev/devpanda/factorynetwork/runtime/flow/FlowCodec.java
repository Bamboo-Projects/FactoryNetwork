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
 * Schreibt wartende Abläufe auf und holt sie zurück.
 *
 * <p><b>Hier wird die Zusage der Mod eingelöst:</b> Ein Ablauf, der auf ein
 * Ereignis wartet, steht nach einem Serverneustart wieder an derselben Stelle
 * — mit denselben Variablen, in derselben Schleifenrunde, im selben Zweig.
 *
 * <p>Aufgeschrieben wird dabei möglichst wenig. Was im Programm steht, kommt
 * aus dem Programm zurück: die {@code where}-Klausel, der {@code else}-Zweig,
 * der Rumpf einer Schleife. Nur was der Ablauf selbst erlebt hat — wo er
 * steht, was er in Händen hält, worauf er wartet — muss auf die Platte.
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
    private static final String KEY_NAME = "n";
    private static final String KEY_VALUE = "v";

    private FlowCodec() {
    }

    // ---- Schreiben --------------------------------------------------------

    public static CompoundTag write(FlowEngine engine) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_NEXT_ID, engine.nextId());
        ListTag list = new ListTag();
        for (Flow flow : engine.flows().values()) {
            // Fertige Abläufe kommen nicht wieder; sie aufzuschreiben hieße,
            // sie beim Laden sofort wegzuwerfen.
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
        // Von unten nach oben, damit die Rahmen beim Laden in derselben
        // Reihenfolge wieder aufeinander liegen.
        for (var frames = flow.stack().descendingIterator(); frames.hasNext(); ) {
            Frame frame = frames.next();
            int id = blocks.id(frame.block());
            if (id < 0) {
                // Der Block gehört nicht zu diesem Programm — dann lässt sich
                // der Ablauf nicht wiederfinden, und ihn halb aufzuschreiben
                // wäre schlimmer als ihn zu verlieren.
                return null;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_BLOCK, id);
            entry.putInt(KEY_INDEX, frame.index());
            entry.putBoolean(KEY_LOOP, frame.isLoop());
            entry.putBoolean(KEY_EXIT, frame.exitOnLeave());
            entry.put(KEY_LOCALS, writeLocals(frame.locals()));
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

    /** Steht in diesem Tag kein einziger Ablauf? */
    public static boolean isEmpty(CompoundTag tag) {
        return tag == null || tag.getList(KEY_FLOWS, Tag.TAG_COMPOUND).isEmpty();
    }

    // ---- Lesen ------------------------------------------------------------

    /**
     * Holt die Abläufe zurück in eine Maschine.
     *
     * <p>Stimmt die Gestalt des Programms nicht mehr, wird der Ablauf
     * {@code STALE}. Sein Stapel bleibt dabei erhalten, soweit er sich
     * auflösen lässt — der Spieler soll sehen können, wo der Ablauf stand,
     * bevor er entscheidet.
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
            // Ein Wert ließ sich nicht zurücklesen. Der Ablauf ist damit
            // hinüber, aber er soll es sagen und nicht spurlos verschwinden.
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
        // Der Zähler darf über das Ende zeigen — dann verlässt der Ablauf den
        // Rahmen als Erstes. Weiter darf er nicht zeigen.
        frame.setIndex(Math.max(0, Math.min(tag.getInt(KEY_INDEX), block.statements().size())));
        frame.setExitOnLeave(tag.getBoolean(KEY_EXIT));
        ListTag locals = tag.getList(KEY_LOCALS, Tag.TAG_COMPOUND);
        for (int i = 0; i < locals.size(); i++) {
            CompoundTag entry = locals.getCompound(i);
            frame.locals().put(entry.getString(KEY_NAME),
                    ValueCodec.read(entry.getCompound(KEY_VALUE)));
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

    /** Die Rahmen eines Ablaufs, von unten nach oben, als Liste. */
    public static List<Frame> framesBottomUp(Flow flow) {
        List<Frame> frames = new ArrayList<>();
        flow.stack().descendingIterator().forEachRemaining(frames::add);
        return frames;
    }
}
