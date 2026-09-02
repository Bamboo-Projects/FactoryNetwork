package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import dev.devpanda.factorynetwork.network.DeviceState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A connector on a block face — the connector, without the block beneath it.
 *
 * <p><b>Why this is separated.</b> A connector is today its own block, and for
 * one machine two stand side by side: cable and connector. In AE2 the
 * counterpart sits as a <i>part</i> on a face of the cable block — one block,
 * up to six connectors. That is the path {@code connector-im-kabel.md}
 * describes as B, and the one that is wanted.
 *
 * <p>This class is the first cut toward it: <b>everything a connector is,
 * without everything a block is.</b> Who holds it — today the
 * {@link ConnectorBlockEntity} with exactly one, tomorrow a cable block with
 * up to six — lives in {@link Host}.
 *
 * <p>This cut changes nothing about behaviour. It only shifts the boundary
 * where a connector ends and its block begins.
 */
public final class ConnectorPart {

    public static final String KEY_LABEL = "Label";
    private static final String KEY_STATE = "State";
    private static final String KEY_COST = "ChannelCost";
    private static final String KEY_REDSTONE = "Redstone";

    /**
     * Who holds this part.
     *
     * <p>Three pieces of information and one notification — a connector needs
     * to know no more than that about its block. In particular it does not
     * know whether it sits there alone.
     */
    public interface Host {

        @Nullable Level level();

        BlockPos pos();

        /** Which way this part faces — the machine sits there. */
        Direction facing();

        /** Something changed and must be saved and sent. */
        void partChanged();

        /**
         * The redstone has changed.
         *
         * <p>Separate from {@link #partChanged()}, because it must also nudge
         * the neighbours: without that, no one notices the change.
         */
        void redstoneChanged();
    }

    private final Host host;
    private DeviceState state = DeviceState.OFFLINE;

    private String label = "";

    /**
     * How many channels this device needs.
     *
     * <p>Always one today. As a field rather than a fixed one, so that a
     * device with a higher demand later does not entail a trek through the
     * pathfinding code — it costs nothing now and saves it then.
     */
    /**
     * What this connector used to cost in channels.
     *
     * <p><b>Without effect since 29.08.</b> Channels no longer exist; the
     * limit at the cable is the throughput per tick, and that does not depend
     * on how many devices lie behind it.
     *
     * <p>The field is still read and written: it is in the save format of
     * every existing world, and dropping it here would mean touching each of
     * them on first load. A dead field is cheaper than a migration for
     * nothing.
     */
    private int channelCost = 1;

    /**
     * What redstone this connector emits.
     *
     * <p>Zero means: emits nothing. That is different from "emits a zero" —
     * a connector without a program should not overwrite the redstone next
     * to it.
     */
    private int emittedRedstone;

    public ConnectorPart(Host host) {
        this.host = host;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label.trim();
        host.partChanged();
    }

    public int channelCost() {
        return channelCost;
    }

    public void setChannelCost(int cost) {
        this.channelCost = Math.max(1, cost);
        host.partChanged();
    }

    /**
     * How this connector stands within the network.
     *
     * <p>Stamped by the controller on rebuild. Without a network it stays
     * {@link DeviceState#OFFLINE} — which is also the initial value, since a
     * freshly placed connector hangs on nothing until a scan finds it.
     */
    public DeviceState state() {
        return state;
    }

    /**
     * Sets the state — and reports it only when it has changed.
     *
     * <p>The rebuild runs every hundred ticks and stamps every device each
     * time. Without this check, every network would send a packet per
     * connector every five seconds to everyone tracking the chunk.
     */
    public void setState(DeviceState wanted) {
        if (wanted == state) {
            return;
        }
        state = wanted;
        host.partChanged();
    }

    public int emittedRedstone() {
        return emittedRedstone;
    }

    public void setEmittedRedstone(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped == emittedRedstone) {
            return;
        }
        emittedRedstone = clamped;
        host.redstoneChanged();
    }

    /** The world this part sits in. */
    public @Nullable Level level() {
        return host.level();
    }

    /**
     * The block this part sits on.
     *
     * <p>Not the machine in front of it — that is in {@link #machinePos()}.
     * The difference matters more at the cable bus than at its own block:
     * there, up to six parts share this one spot.
     */
    public BlockPos pos() {
        return host.pos();
    }

    /** Which way this part faces. */
    public Direction facing() {
        return host.facing();
    }

    // ---- The machine behind it --------------------------------------------

    /**
     * The <b>whole</b> inventory of the machine, regardless of sides.
     *
     * <p>Needed for {@code slots(…)}: one connector per machine should be
     * enough, and which slot is meant is decided by the code. The
     * side-specific version below stays the default for everything else —
     * there the machine keeps its own rules.
     */
    public @Nullable IItemHandler machineInventoryAll() {
        return capability(Capabilities.ItemHandler.BLOCK, null);
    }

    public @Nullable IItemHandler machineInventory() {
        return capability(Capabilities.ItemHandler.BLOCK, facing().getOpposite());
    }

    /**
     * The machine's tank.
     *
     * <p>The same neighbour, the same side — only a different capability. A
     * machine can have both; which one is meant is decided by the selection
     * in the program, not by the connector.
     */
    public @Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler machineTank() {
        return capability(Capabilities.FluidHandler.BLOCK, facing().getOpposite());
    }

    /**
     * The machine's energy store.
     *
     * <p>Read for showing in the editor and for {@code energy()}.
     */
    public @Nullable net.neoforged.neoforge.energy.IEnergyStorage machineEnergy() {
        return capability(Capabilities.EnergyStorage.BLOCK, facing().getOpposite());
    }

    /**
     * The machine's BlockEntity, or {@code null}.
     *
     * <p>Needed to recognise a machine by its <b>type</b> and not by its
     * name: a furnace is named differently on an English server than in a
     * German client, but it is the same class everywhere.
     */
    public @Nullable net.minecraft.world.level.block.entity.BlockEntity machineBlockEntity() {
        BlockPos target = machinePos();
        Level level = host.level();
        return target != null && level.isLoaded(target) ? level.getBlockEntity(target) : null;
    }

    /** Where the machine stands, or {@code null} if nothing is loaded there. */
    public @Nullable BlockPos machinePos() {
        Level level = host.level();
        if (level == null) {
            return null;
        }
        BlockPos target = host.pos().relative(facing());
        return level.isLoaded(target) ? target : null;
    }

    private <T> @Nullable T capability(
            net.neoforged.neoforge.capabilities.BlockCapability<T, @Nullable Direction> which,
            @Nullable Direction side) {
        BlockPos target = machinePos();
        return target == null ? null : host.level().getCapability(which, target, side);
    }

    // ---- Saving -------------------------------------------------------------

    public void save(CompoundTag tag) {
        tag.putString(KEY_LABEL, label);
        tag.putInt(KEY_COST, channelCost);
        tag.putInt(KEY_REDSTONE, emittedRedstone);
        // To disk as well: otherwise, whoever loads a world sees nothing but
        // connectors reported offline for five seconds, until the first
        // rebuild is through.
        tag.putByte(KEY_STATE, state.id());
    }

    public void load(CompoundTag tag) {
        label = tag.getString(KEY_LABEL);
        channelCost = Math.max(1, tag.getInt(KEY_COST));
        emittedRedstone = tag.getInt(KEY_REDSTONE);
        state = DeviceState.byId(tag.getByte(KEY_STATE));
    }
}
