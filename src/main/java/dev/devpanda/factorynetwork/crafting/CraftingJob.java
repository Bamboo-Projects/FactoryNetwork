package dev.devpanda.factorynetwork.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * A crafting job.
 *
 * <p>It lives on the controller like a process: the fabricator carries out
 * steps, but what remains to be done belongs to the network. A job tied to
 * the device would be gone the moment someone breaks the device — and that is
 * exactly the moment when one wants to know what was still open.
 *
 * <p>The status deliberately uses the same vocabulary as the workers:
 * {@code RUNNING} when something is happening, {@code WAITING} when something
 * is missing. A second set of words for the same thing would be a second
 * language in the same window.
 */
public final class CraftingJob {

    /** How a job stands. */
    public enum Status {
        /** It is being built. */
        RUNNING,
        /** Something is missing — ingredients or a fabricator. */
        WAITING,
        /** Done. */
        DONE,
        /**
         * Can no longer be finished.
         *
         * <p>The only reason is a recipe that no longer exists — for instance
         * because a mod is out of the pack. Missing ingredients are not one:
         * whoever waits for them waits, and tomorrow they might be there.
         */
        FAILED
    }

    /**
     * A step running at a machine.
     *
     * <p><b>This one is saved</b> — unlike the plan, which the controller
     * recomputes every tick. The difference is not one of convenience: a plan
     * is an intention and may go stale, a running step is a <b>fact about the
     * world</b>. The ingredients are in the furnace. Whoever forgets that has
     * lost them and puts in new ones next time.
     *
     * @param station  the recipe type, for the executor
     * @param device   the connector the machine hangs on
     * @param result   what should come out
     * @param expected how much of it
     * @param done     how much of it has already been collected
     */
    public record Running(String station, String device, Item result,
                          long expected, long done) {

        /** How much is still missing. */
        public long left() {
            return Math.max(0, expected - done);
        }

        public Running withDone(long delivered) {
            return new Running(station, device, result, expected, delivered);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Station", station);
            tag.putString("Device", device);
            tag.putString("Result", BuiltInRegistries.ITEM.getKey(result).toString());
            tag.putLong("Expected", expected);
            tag.putLong("Done", done);
            return tag;
        }

        static Running load(CompoundTag tag) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Result"));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return null;
            }
            return new Running(tag.getString("Station"), tag.getString("Device"),
                    BuiltInRegistries.ITEM.get(id), tag.getLong("Expected"),
                    tag.getLong("Done"));
        }
    }

    private static final String KEY_ID = "Id";
    private static final String KEY_TARGET = "Target";
    private static final String KEY_WANTED = "Wanted";
    private static final String KEY_DONE = "Done";
    private static final String KEY_STATUS = "Status";
    private static final String KEY_DETAIL = "Detail";
    private static final String KEY_RUNNING = "Running";

    private final long id;
    private final Item target;
    private final int wanted;
    private int done;
    private Status status = Status.WAITING;
    private String detail = "";

    /** What currently lies in a machine, or {@code null}. */
    private Running running;

    public CraftingJob(long id, Item target, int wanted) {
        this.id = id;
        this.target = target;
        this.wanted = wanted;
    }

    public long id() {
        return id;
    }

    public Item target() {
        return target;
    }

    public int wanted() {
        return wanted;
    }

    public int done() {
        return done;
    }

    public Status status() {
        return status;
    }

    /** Why it is currently stalled, or empty. */
    public String detail() {
        return detail;
    }

    /** What currently lies in a machine, or {@code null}. */
    public Running running() {
        return running;
    }

    public void setRunning(Running step) {
        this.running = step;
    }

    /** How many are still missing. */
    public int remaining() {
        return Math.max(0, wanted - done);
    }

    public void note(Status next, String why) {
        this.status = next;
        this.detail = why == null ? "" : why;
    }

    /** Records what a step has delivered. */
    public void produced(int amount) {
        done = Math.min(wanted, done + Math.max(0, amount));
        if (done >= wanted) {
            note(Status.DONE, "");
        } else {
            note(Status.RUNNING, "");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_ID, id);
        tag.putString(KEY_TARGET, BuiltInRegistries.ITEM.getKey(target).toString());
        tag.putInt(KEY_WANTED, wanted);
        tag.putInt(KEY_DONE, done);
        tag.putString(KEY_STATUS, status.name());
        tag.putString(KEY_DETAIL, detail);
        if (running != null) {
            tag.put(KEY_RUNNING, running.save());
        }
        return tag;
    }

    /**
     * Reads a job back, or {@code null}.
     *
     * <p>If the target item's mod is out of the pack, the job is gone. That is
     * the same quiet stance as with the stored stock: a job for something that
     * no longer exists can no longer be fulfilled, and a message about it
     * helps no one in cleaning up.
     */
    public static CraftingJob load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_TARGET));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        CraftingJob job = new CraftingJob(tag.getLong(KEY_ID),
                BuiltInRegistries.ITEM.get(id), tag.getInt(KEY_WANTED));
        job.done = tag.getInt(KEY_DONE);
        job.detail = tag.getString(KEY_DETAIL);
        if (tag.contains(KEY_RUNNING)) {
            job.running = Running.load(tag.getCompound(KEY_RUNNING));
        }
        for (Status candidate : Status.values()) {
            if (candidate.name().equals(tag.getString(KEY_STATUS))) {
                job.status = candidate;
            }
        }
        return job;
    }
}
