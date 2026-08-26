package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Ein Anschluss an einer Blockfläche — der Connector, ohne den Block darunter.
 *
 * <p><b>Warum das getrennt ist.</b> Ein Connector ist heute ein eigener Block,
 * und für eine Maschine stehen zwei nebeneinander: Kabel und Connector. Bei
 * AE2 sitzt das Gegenstück als <i>Teil</i> an einer Fläche des Kabelblocks —
 * ein Block, bis zu sechs Anschlüsse. Das ist der Weg, den
 * {@code connector-im-kabel.md} als B beschreibt und der gewünscht ist.
 *
 * <p>Diese Klasse ist der erste Schnitt dorthin: <b>alles, was ein Connector
 * ist, ohne alles, was ein Block ist.</b> Wer sie hält — heute die
 * {@link ConnectorBlockEntity} mit genau einem, morgen ein Kabelblock mit bis
 * zu sechs —, steht in {@link Host}.
 *
 * <p>Am Verhalten ändert dieser Schnitt nichts. Er verschiebt nur die Grenze,
 * an der ein Connector aufhört und sein Block anfängt.
 */
public final class ConnectorPart {

    public static final String KEY_LABEL = "Label";
    private static final String KEY_COST = "ChannelCost";
    private static final String KEY_REDSTONE = "Redstone";

    /**
     * Wer dieses Teil hält.
     *
     * <p>Drei Auskünfte und eine Meldung — mehr braucht ein Anschluss nicht
     * von seinem Block zu wissen. Insbesondere weiß er nicht, ob er allein
     * dort sitzt.
     */
    public interface Host {

        @Nullable Level level();

        BlockPos pos();

        /** Wohin dieses Teil zeigt — dort sitzt die Maschine. */
        Direction facing();

        /** Etwas hat sich geändert und muss gespeichert und geschickt werden. */
        void partChanged();

        /**
         * Das Redstone hat sich geändert.
         *
         * <p>Getrennt von {@link #partChanged()}, weil es zusätzlich die
         * Nachbarn anstoßen muss: Ohne das merkt niemand die Änderung.
         */
        void redstoneChanged();
    }

    private final Host host;

    private String label = "";

    /**
     * Wie viele Kanäle dieses Gerät braucht.
     *
     * <p>Heute immer einer. Als Feld statt als feste Eins, damit ein Gerät
     * mit höherem Bedarf später keine Wanderung durch den Pfadcode nach sich
     * zieht — das kostet jetzt nichts und spart sie dann.
     */
    private int channelCost = 1;

    /**
     * Was dieser Connector an Redstone ausgibt.
     *
     * <p>Null heißt: gibt nichts aus. Das ist etwas anderes als „gibt Null
     * aus" — ein Connector ohne Programm soll das Redstone daneben nicht
     * überschreiben.
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

    /** Wohin dieses Teil zeigt. */
    public Direction facing() {
        return host.facing();
    }

    // ---- Die Maschine dahinter --------------------------------------------

    /**
     * Das <b>ganze</b> Inventar der Maschine, ohne Rücksicht auf Seiten.
     *
     * <p>Gebraucht für {@code slots(…)}: Ein Anschluss je Maschine soll
     * reichen, und welches Fach gemeint ist, entscheidet der Code. Die
     * seitenbezogene Fassung darunter bleibt die Vorgabe für alles andere —
     * dort hält die Maschine ihre eigenen Regeln.
     */
    public @Nullable IItemHandler machineInventoryAll() {
        return capability(Capabilities.ItemHandler.BLOCK, null);
    }

    public @Nullable IItemHandler machineInventory() {
        return capability(Capabilities.ItemHandler.BLOCK, facing().getOpposite());
    }

    /**
     * Der Tank der Maschine.
     *
     * <p>Derselbe Nachbar, dieselbe Seite — nur eine andere Fähigkeit. Eine
     * Maschine kann beides haben; welches gemeint ist, entscheidet die
     * Auswahl im Programm, nicht der Connector.
     */
    public @Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler machineTank() {
        return capability(Capabilities.FluidHandler.BLOCK, facing().getOpposite());
    }

    /**
     * Der Stromspeicher der Maschine.
     *
     * <p>Gelesen wird er für das Zeigen im Editor und für {@code energy()}.
     */
    public @Nullable net.neoforged.neoforge.energy.IEnergyStorage machineEnergy() {
        return capability(Capabilities.EnergyStorage.BLOCK, facing().getOpposite());
    }

    /**
     * Die BlockEntity der Maschine, oder {@code null}.
     *
     * <p>Gebraucht, um eine Maschine an ihrer <b>Art</b> zu erkennen und
     * nicht an ihrem Namen: Ein Ofen heißt auf einem englischen Server anders
     * als im deutschen Client, aber er ist überall dieselbe Klasse.
     */
    public @Nullable net.minecraft.world.level.block.entity.BlockEntity machineBlockEntity() {
        BlockPos target = machinePos();
        Level level = host.level();
        return target != null && level.isLoaded(target) ? level.getBlockEntity(target) : null;
    }

    /** Wo die Maschine steht, oder {@code null}, wenn dort nichts geladen ist. */
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

    // ---- Speichern ---------------------------------------------------------

    public void save(CompoundTag tag) {
        tag.putString(KEY_LABEL, label);
        tag.putInt(KEY_COST, channelCost);
        tag.putInt(KEY_REDSTONE, emittedRedstone);
    }

    public void load(CompoundTag tag) {
        label = tag.getString(KEY_LABEL);
        channelCost = Math.max(1, tag.getInt(KEY_COST));
        emittedRedstone = tag.getInt(KEY_REDSTONE);
    }
}
