package dev.devpanda.factorynetwork.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Ein Fertigungsauftrag.
 *
 * <p>Er lebt am Controller wie ein Ablauf: Der Fabricator führt Schritte aus,
 * aber was noch zu tun ist, gehört dem Netz. Ein Auftrag, der am Gerät hinge,
 * wäre weg, sobald jemand das Gerät abbaut — und das ist genau der Moment, in
 * dem man wissen will, was noch offen war.
 *
 * <p>Der Zustand ist absichtlich derselbe Wortschatz wie bei den Workern:
 * {@code RUNNING}, wenn etwas geschieht, {@code WAITING}, wenn etwas fehlt.
 * Ein zweiter Satz Wörter für dieselbe Sache wäre eine zweite Sprache im
 * selben Fenster.
 */
public final class CraftingJob {

    /** Wie es um einen Auftrag steht. */
    public enum Status {
        /** Es wird gebaut. */
        RUNNING,
        /** Es fehlt etwas — Zutaten oder ein Fabricator. */
        WAITING,
        /** Fertig. */
        DONE,
        /**
         * Kann nicht mehr fertig werden.
         *
         * <p>Der einzige Grund ist ein Rezept, das es nicht mehr gibt — etwa
         * weil eine Mod aus dem Pack ist. Fehlende Zutaten sind keiner: Wer
         * darauf wartet, wartet, und morgen liegen sie vielleicht da.
         */
        FAILED
    }

    /**
     * Ein Schritt, der an einer Maschine läuft.
     *
     * <p><b>Der wird gespeichert</b> — im Gegensatz zum Plan, den der
     * Controller bei jedem Takt neu rechnet. Der Unterschied ist keiner der
     * Bequemlichkeit: Ein Plan ist eine Absicht und darf veralten, ein
     * laufender Schritt ist eine <b>Tatsache über die Welt</b>. Die Zutaten
     * liegen im Ofen. Wer das vergisst, hat sie verloren und legt beim
     * nächsten Mal neue nach.
     *
     * @param station  die Rezeptart, für den Ausführenden
     * @param device   der Connector, an dem die Maschine hängt
     * @param result   was herauskommen soll
     * @param expected wie viel davon
     * @param done     wie viel davon schon abgeholt ist
     */
    public record Running(String station, String device, Item result,
                          long expected, long done) {

        /** Wie viel noch fehlt. */
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

    /** Was gerade in einer Maschine liegt, oder {@code null}. */
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

    /** Warum er gerade steht, oder leer. */
    public String detail() {
        return detail;
    }

    /** Was gerade in einer Maschine liegt, oder {@code null}. */
    public Running running() {
        return running;
    }

    public void setRunning(Running step) {
        this.running = step;
    }

    /** Wie viele noch fehlen. */
    public int remaining() {
        return Math.max(0, wanted - done);
    }

    public void note(Status next, String why) {
        this.status = next;
        this.detail = why == null ? "" : why;
    }

    /** Trägt ein, was ein Schritt geliefert hat. */
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
     * Liest einen Auftrag zurück, oder {@code null}.
     *
     * <p>Ist die Mod des Zielgegenstands aus dem Pack, ist der Auftrag weg.
     * Das ist dieselbe stille Haltung wie beim Lagerbestand: Ein Auftrag über
     * etwas, das es nicht mehr gibt, lässt sich nicht mehr erfüllen, und eine
     * Meldung darüber hilft niemandem beim Aufräumen.
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
