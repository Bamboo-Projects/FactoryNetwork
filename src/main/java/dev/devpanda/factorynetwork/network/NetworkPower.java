package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.EnergyCellView;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * The network's energy: reserve, demand and state.
 *
 * <p>Three states, and the middle one is the important one: <b>whoever runs
 * out of energy goes out and must boot up again first.</b> Without the boot-up
 * time a power outage would be a flicker that no one notices — and with it you
 * notice at once that the supply is not enough.
 *
 * <p>The reserve sits in the controller and accepts Forge Energy, as the press
 * already does. A dedicated notion of energy would be closer to Applied
 * Energistics, but would need its own generation chain; the mod presupposes a
 * pack anyway.
 */
public final class NetworkPower {

    /** How the supply stands. */
    public enum State {
        /** Enough energy, the network is working. */
        RUNNING,
        /** The reserve is empty. Nothing runs. */
        OFF,
        /** Energy is back, the network is coming up. */
        BOOTING
    }

    private static final String KEY_ENERGY = "Energy";
    private static final String KEY_STATE = "State";
    private static final String KEY_BOOT = "Boot";

    /**
     * The buffer in the controller.
     *
     * <p>It accepts from outside and gives nothing out on its own — whoever
     * wants energy out of the network writes a worker for it. The way in from
     * outside is {@link #port()} and not this buffer: ever since there are
     * energy cells, it is only the first pot of several.
     *
     * <p><b>It is filled first and emptied first.</b> This makes the cells the
     * reserve and not the working memory — what passes straight through touches
     * not a single item.
     */
    private final InternalBuffer buffer =
            new InternalBuffer(Power.CAPACITY, Power.MAX_INPUT);

    /**
     * The network's drives, for the sake of the energy cells inside them.
     *
     * <p>Held as in {@code NetworkStorage}: the controller sets them on every
     * rebuild, and they are queried afresh on every access. A snapshot of the
     * cells would be wrong after the first cell swap.
     */
    private final List<DriveBlockEntity> drives = new ArrayList<>();

    private State state = State.OFF;
    private int bootTicks;
    private int draw;

    /** What the network last output per tick, averaged over a second. */
    private int supplied;

    /** What has flowed out so far in the current second. */
    private int suppliedWindow;

    private int windowTicks;

    /** How much the network is currently drawing, in FE per tick. */
    public int draw() {
        return draw;
    }

    /**
     * What the network outputs to machines, in FE per tick.
     *
     * <p><b>Averaged over a second, not the last tick.</b> A worker with
     * {@code rate 800 per 20t} pushes once and rests nineteen times; the
     * per-tick number would be zero nineteen times and eight hundred once, and
     * no one reads from that that forty are flowing.
     *
     * <p>It does not belong to {@link #draw()}: that is what the network needs
     * for its readiness. Energy that is passed through is not own draw —
     * counting it in would mean that a network shuts itself off because it
     * delivers too much.
     */
    public int supplied() {
        return supplied;
    }

    /** Reports what a worker just gave to a machine. */
    public void noteSupplied(int amount) {
        suppliedWindow += Math.max(0, amount);
    }

    public void setDraw(int perTick) {
        this.draw = Math.max(0, perTick);
    }

    public State state() {
        return state;
    }

    /** Is the network running? Only then do workers and processes work. */
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** How many ticks the boot-up still takes. */
    public int bootTicksLeft() {
        return bootTicks;
    }

    /**
     * Which drives hang on the network.
     *
     * <p>Set by the controller on every rebuild — as with the storage and the
     * fluids. If none hang on the network, the reserve is the buffer, and
     * nothing more.
     */
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
    }

    /**
     * How much energy the network has in total.
     *
     * <p>Buffer plus energy cells. <b>Everything hangs on this number</b> — the
     * own draw, the boot-up, the output to machines. Counting only the buffer
     * would mean that a network with full cells goes out, and right in the
     * middle of operation, without a zero standing anywhere.
     */
    public int stored() {
        long total = buffer.getEnergyStored();
        for (EnergyCellView cell : cells()) {
            total += cell.stored();
        }
        return saturated(total);
    }

    public int capacity() {
        long total = buffer.getMaxEnergyStored();
        for (EnergyCellView cell : cells()) {
            total += cell.capacity();
        }
        return saturated(total);
    }

    /** How much still fits in. */
    public int room() {
        long total = (long) buffer.getMaxEnergyStored() - buffer.getEnergyStored();
        for (EnergyCellView cell : cells()) {
            total += cell.room();
        }
        return saturated(total);
    }

    /**
     * A sum that does not overflow.
     *
     * <p>Fifty-two full drives with the largest cells blow past an int. That
     * is an installation almost no one builds — but whoever does build it
     * should see a reserve that is too small, not a negative one.
     */
    private static int saturated(long total) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    /**
     * Reports that something in the cells has changed.
     *
     * <p><b>The drives have to come along.</b> The charge sits in working
     * memory and only goes into the item on save; without this notification
     * Minecraft does not know that the chunk must be saved. A drive in a
     * different chunk than the controller would have the charge from before
     * after a restart — the same reason as with the stored stock, see
     * {@code NetworkStorage.markChanged}.
     *
     * <p>Only when a cell was actually touched. The buffer is filled first and
     * emptied first, and a network that only passes energy through would
     * otherwise register all its drives for saving every tick.
     */
    private void markCellsChanged() {
        for (DriveBlockEntity drive : drives) {
            if (!drive.isRemoved()) {
                drive.setChanged();
            }
        }
    }

    /** All energy cells in all drives of the network. */
    private List<EnergyCellView> cells() {
        if (drives.isEmpty()) {
            return List.of();
        }
        List<EnergyCellView> found = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            if (!drive.isRemoved()) {
                found.addAll(drive.energyCells());
            }
        }
        return found;
    }

    /** The buffer alone — for the display, which names both separately. */
    public InternalBuffer buffer() {
        return buffer;
    }

    /**
     * The connection point for foreign mods.
     *
     * <p><b>Not the buffer itself.</b> Whoever lays their cable to the
     * controller fills the whole reserve with it and not just its first twenty
     * thousand — otherwise every energy cell would stay empty forever, since
     * there is no other way in.
     *
     * <p>The rate stays {@link Power#MAX_INPUT}: that is the only energy limit
     * in the whole system, and it applies to the network's intake, not to a
     * single pot within it.
     */
    public IEnergyStorage port() {
        return port;
    }

    private final IEnergyStorage port = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.min(Math.max(0, Math.min(toReceive, Power.MAX_INPUT)), room());
            if (!simulate) {
                fill(accepted);
            }
            return accepted;
        }

        /**
         * And out, without a rate limit.
         *
         * <p><b>The limit was for intake</b>, and for output it would be
         * exactly what gets in the way: whoever routes energy from the network
         * into another system used to go via an energy cube, and its own rate
         * was the bottleneck. That detour is the reason this way exists.
         *
         * <p><b>Down to the floor and not down to zero.</b> The one limit that
         * remains is not a rate: if a foreign line drew below the restart
         * threshold, the network would go out, boot up for three seconds and
         * go out again — a flicker that looks like a bug and is none. Whoever
         * wants the rest as well switches the network off.
         */
        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int floor = Power.restartThreshold(draw());
            int free = Math.max(0, stored() - floor);
            int taken = Math.min(Math.max(0, toExtract), free);
            if (!simulate && taken > 0) {
                consume(taken);
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return stored();
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    /**
     * One tick of energy.
     *
     * <p>Pay first, then work: what is deducted here is the readiness for this
     * tick. If the reserve is not enough, the network goes out — and comes
     * back only once enough is gathered to survive the boot-up and still run
     * afterward.
     */
    public void tick() {
        // The window first, because below this we bail out in several places
        // — and an output that stalled during boot-up would look like one that
        // is still flowing.
        if (++windowTicks >= 20) {
            supplied = (suppliedWindow + windowTicks / 2) / windowTicks;
            suppliedWindow = 0;
            windowTicks = 0;
        }
        if (draw <= 0) {
            // A network with no consumers needs nothing and always runs.
            state = State.RUNNING;
            bootTicks = 0;
            return;
        }
        if (stored() < draw) {
            state = State.OFF;
            bootTicks = 0;
            // Only the buffer. The cells keep their remainder: an item whose
            // contents are silently deleted when the network goes out is a
            // loss no one sees coming.
            buffer.drain();
            return;
        }
        consume(draw);
        switch (state) {
            case RUNNING -> { }
            case BOOTING -> {
                if (--bootTicks <= 0) {
                    state = State.RUNNING;
                    bootTicks = 0;
                }
            }
            case OFF -> {
                if (stored() >= Power.restartThreshold(draw)) {
                    state = State.BOOTING;
                    bootTicks = Power.BOOT_TICKS;
                }
            }
            default -> { }
        }
    }

    /**
     * Fills the reserve, without the detour via a connection point and without
     * a rate.
     *
     * <p>The buffer first, then the cells. Whatever no longer fits there
     * either falls away — as with every store.
     */
    public void fill(int amount) {
        int rest = Math.max(0, amount);
        int intoBuffer = Math.min(rest, buffer.getMaxEnergyStored() - buffer.getEnergyStored());
        buffer.charge(intoBuffer);
        rest -= intoBuffer;
        boolean touchedCells = false;
        for (EnergyCellView cell : cells()) {
            if (rest <= 0) {
                break;
            }
            int taken = cell.fill(rest);
            touchedCells |= taken > 0;
            rest -= taken;
        }
        if (touchedCells) {
            markCellsChanged();
        }
    }

    /** Takes from within: first from the buffer, then from the cells. */
    private void consume(int amount) {
        int rest = Math.max(0, amount);
        int fromBuffer = Math.min(rest, buffer.getEnergyStored());
        buffer.consume(fromBuffer);
        rest -= fromBuffer;
        boolean touchedCells = false;
        for (EnergyCellView cell : cells()) {
            if (rest <= 0) {
                break;
            }
            int given = cell.take(rest);
            touchedCells |= given > 0;
            rest -= given;
        }
        if (touchedCells) {
            markCellsChanged();
        }
    }

    /**
     * Takes up to {@code amount} from the reserve and says how much it was.
     *
     * <p>For output to a machine. <b>Up to</b>, because an empty network is not
     * an error: whoever has nothing gives nothing out, and the worker then
     * stands there as {@code WAITING_TARGET} as if before a full chest.
     */
    public int take(int amount) {
        int taken = Math.min(Math.max(amount, 0), stored());
        consume(taken);
        return taken;
    }

    /**
     * Empties the reserve in one go — <b>cells and all</b>.
     *
     * <p>For the case that the supply breaks down, and for tests that want to
     * reproduce exactly that without waiting twenty thousand ticks.
     *
     * <p>The cells belong to it, even though going out during the tick spares
     * them: whoever sets the reserve to zero means the whole of it. If
     * something were left here, the starting point of every test would be a
     * number other than zero.
     */
    public void empty() {
        buffer.drain();
        boolean touchedCells = false;
        for (EnergyCellView cell : cells()) {
            touchedCells |= cell.take(cell.stored()) > 0;
        }
        if (touchedCells) {
            markCellsChanged();
        }
        state = State.OFF;
        bootTicks = 0;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENERGY, buffer.serializeNBT(registries));
        tag.putString(KEY_STATE, state.name());
        tag.putInt(KEY_BOOT, bootTicks);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(KEY_ENERGY)) {
            buffer.deserializeNBT(registries, tag.get(KEY_ENERGY));
        }
        // The state comes back with it. An unloaded chunk is not a power
        // outage — whoever leaves an installation and returns should see it
        // running and not wait three seconds.
        state = State.OFF;
        bootTicks = 0;
        if (tag.contains(KEY_STATE)) {
            for (State candidate : State.values()) {
                if (candidate.name().equals(tag.getString(KEY_STATE))) {
                    state = candidate;
                }
            }
            bootTicks = tag.getInt(KEY_BOOT);
        }
    }
}
