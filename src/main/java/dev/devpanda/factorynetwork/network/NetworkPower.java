package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.EnergyCellView;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Strom des Netzwerks: Vorrat, Bedarf und Zustand.
 *
 * <p>Drei Zustände, und der mittlere ist der wichtige: <b>Wer keinen Strom
 * mehr hat, geht aus und muss danach erst wieder hochfahren.</b> Ohne die
 * Hochfahrzeit wäre ein Stromausfall ein Flackern, das niemand bemerkt — und
 * mit ihr merkt man sofort, dass die Versorgung nicht reicht.
 *
 * <p>Der Vorrat liegt im Controller und nimmt Forge Energy an, wie es die
 * Presse schon tut. Ein eigener Energiebegriff wäre näher an Applied
 * Energistics, bräuchte aber eine eigene Erzeugerkette; die Mod setzt ohnehin
 * ein Pack voraus.
 */
public final class NetworkPower {

    /** Wie es um die Versorgung steht. */
    public enum State {
        /** Genug Strom, das Netz arbeitet. */
        RUNNING,
        /** Der Vorrat ist leer. Nichts läuft. */
        OFF,
        /** Strom ist wieder da, das Netz kommt hoch. */
        BOOTING
    }

    private static final String KEY_ENERGY = "Energy";
    private static final String KEY_STATE = "State";
    private static final String KEY_BOOT = "Boot";

    /**
     * Der Puffer im Controller.
     *
     * <p>Er nimmt von außen an und gibt von sich aus nichts heraus — wer
     * Strom aus dem Netz will, schreibt einen Worker dafür. Der Weg von außen
     * herein ist {@link #port()} und nicht dieser Puffer: Seit es
     * Energiezellen gibt, ist er nur noch der erste Topf von mehreren.
     *
     * <p><b>Er wird zuerst gefüllt und zuerst geleert.</b> Damit sind die
     * Zellen die Reserve und nicht der Arbeitsspeicher — was durchläuft,
     * berührt keinen einzigen Gegenstand.
     */
    private final InternalBuffer buffer =
            new InternalBuffer(Power.CAPACITY, Power.MAX_INPUT);

    /**
     * Die Laufwerke des Netzes, wegen der Energiezellen darin.
     *
     * <p>Gehalten wie bei {@code NetworkStorage}: Der Controller setzt sie
     * bei jedem Neuaufbau, gefragt wird bei jedem Zugriff neu. Eine
     * Momentaufnahme der Zellen wäre nach dem ersten Zellentausch falsch.
     */
    private final List<DriveBlockEntity> drives = new ArrayList<>();

    private State state = State.OFF;
    private int bootTicks;
    private int draw;

    /** Was das Netz zuletzt je Tick abgegeben hat, gemittelt über eine Sekunde. */
    private int supplied;

    /** Was in der laufenden Sekunde bisher abgeflossen ist. */
    private int suppliedWindow;

    private int windowTicks;

    /** Wie viel das Netz gerade zieht, in FE je Tick. */
    public int draw() {
        return draw;
    }

    /**
     * Was das Netz an Maschinen abgibt, in FE je Tick.
     *
     * <p><b>Gemittelt über eine Sekunde, nicht der letzte Tick.</b> Ein
     * Worker mit {@code rate 800 per 20t} schiebt einmal und ruht neunzehnmal;
     * die Zahl im Tick wäre neunzehnmal null und einmal achthundert, und
     * niemand liest daraus, dass vierzig fließen.
     *
     * <p>Sie gehört nicht zum {@link #draw()}: Der ist, was das Netz für
     * seine Bereitschaft braucht. Strom, der durchgereicht wird, ist kein
     * Eigenbedarf — ihn mitzuzählen hieße, dass ein Netz sich abschaltet,
     * weil es zu viel liefert.
     */
    public int supplied() {
        return supplied;
    }

    /** Meldet, was ein Worker gerade an eine Maschine gegeben hat. */
    public void noteSupplied(int amount) {
        suppliedWindow += Math.max(0, amount);
    }

    public void setDraw(int perTick) {
        this.draw = Math.max(0, perTick);
    }

    public State state() {
        return state;
    }

    /** Läuft das Netz? Nur dann arbeiten Worker und Abläufe. */
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Wie viele Ticks das Hochfahren noch dauert. */
    public int bootTicksLeft() {
        return bootTicks;
    }

    /**
     * Welche Laufwerke im Netz hängen.
     *
     * <p>Setzt der Controller bei jedem Neuaufbau — wie beim Speicher und bei
     * den Flüssigkeiten. Hängt keines im Netz, ist der Vorrat der Puffer, und
     * mehr nicht.
     */
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
    }

    /**
     * Wie viel Strom das Netz insgesamt hat.
     *
     * <p>Puffer plus Energiezellen. <b>An dieser Zahl hängt alles</b> — der
     * Eigenbedarf, das Hochfahren, die Abgabe an Maschinen. Nur den Puffer zu
     * zählen hieße, dass ein Netz mit vollen Zellen ausgeht, und zwar
     * mitten im Betrieb, ohne dass irgendwo eine Null steht.
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

    /** Wie viel noch hineinpasst. */
    public int room() {
        long total = (long) buffer.getMaxEnergyStored() - buffer.getEnergyStored();
        for (EnergyCellView cell : cells()) {
            total += cell.room();
        }
        return saturated(total);
    }

    /**
     * Eine Summe, die nicht überläuft.
     *
     * <p>Zweiundfünfzig volle Laufwerke mit den größten Zellen sprengen einen
     * int. Das ist eine Anlage, die kaum jemand baut — aber wer sie baut,
     * soll einen zu kleinen Vorrat sehen und keinen negativen.
     */
    private static int saturated(long total) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    /**
     * Meldet, dass sich in den Zellen etwas geändert hat.
     *
     * <p><b>Die Laufwerke müssen mit.</b> Die Ladung liegt im Arbeitsspeicher
     * und geht erst beim Sichern in den Gegenstand; ohne diese Meldung weiß
     * Minecraft nicht, dass der Chunk gesichert werden muss. Ein Laufwerk in
     * einem anderen Chunk als der Controller hätte nach einem Neustart die
     * Ladung von vorhin — derselbe Grund wie beim Lagerbestand, siehe
     * {@code NetworkStorage.markChanged}.
     *
     * <p>Nur wenn wirklich eine Zelle berührt wurde. Der Puffer wird zuerst
     * gefüllt und zuerst geleert, und ein Netz, das nur durchreicht, würde
     * sonst jeden Tick alle seine Laufwerke zum Sichern anmelden.
     */
    private void markCellsChanged() {
        for (DriveBlockEntity drive : drives) {
            if (!drive.isRemoved()) {
                drive.setChanged();
            }
        }
    }

    /** Alle Energiezellen in allen Laufwerken des Netzes. */
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

    /** Der Puffer allein — für die Anzeige, die beides getrennt nennt. */
    public InternalBuffer buffer() {
        return buffer;
    }

    /**
     * Der Anschluss für Fremdmods.
     *
     * <p><b>Nicht der Puffer selbst.</b> Wer sein Kabel an den Controller
     * legt, füllt damit den ganzen Vorrat und nicht nur dessen erste
     * zwanzigtausend — sonst bliebe jede Energiezelle für immer leer, denn
     * einen anderen Weg hinein gibt es nicht.
     *
     * <p>Die Rate bleibt {@link Power#MAX_INPUT}: Das ist die einzige
     * Stromgrenze im ganzen System, und sie gilt für die Aufnahme des
     * Netzes, nicht für einen einzelnen Topf darin.
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

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
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
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    /**
     * Ein Tick Strom.
     *
     * <p>Erst zahlen, dann arbeiten: Was hier abgezogen wird, ist die
     * Bereitschaft für diesen Tick. Reicht der Vorrat nicht, geht das Netz
     * aus — und kommt erst wieder, wenn genug beisammen ist, um das
     * Hochfahren zu überstehen und danach noch zu laufen.
     */
    public void tick() {
        // Zuerst das Fenster, denn darunter wird an mehreren Stellen
        // ausgestiegen — und eine Abgabe, die beim Hochfahren stehenbleibt,
        // sähe aus wie eine, die noch fließt.
        if (++windowTicks >= 20) {
            supplied = (suppliedWindow + windowTicks / 2) / windowTicks;
            suppliedWindow = 0;
            windowTicks = 0;
        }
        if (draw <= 0) {
            // Ein Netz ohne Verbraucher braucht nichts und läuft immer.
            state = State.RUNNING;
            bootTicks = 0;
            return;
        }
        if (stored() < draw) {
            state = State.OFF;
            bootTicks = 0;
            // Nur der Puffer. Die Zellen behalten ihren Rest: Ein Gegenstand,
            // dem beim Ausgehen des Netzes still der Inhalt gelöscht wird,
            // ist ein Verlust, den niemand kommen sieht.
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
     * Füllt den Vorrat, ohne den Umweg über einen Anschluss und ohne Rate.
     *
     * <p>Erst den Puffer, dann die Zellen. Was auch dort nicht mehr
     * hineinpasst, fällt weg — wie bei jedem Speicher.
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

    /** Nimmt von innen: erst aus dem Puffer, dann aus den Zellen. */
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
     * Nimmt bis zu {@code amount} aus dem Vorrat und sagt, wie viel es wurde.
     *
     * <p>Für die Abgabe an eine Maschine. <b>Bis zu</b>, weil ein leeres Netz
     * kein Fehler ist: Wer nichts hat, gibt nichts ab, und der Worker steht
     * danach als {@code WAITING_TARGET} da wie vor einer vollen Kiste.
     */
    public int take(int amount) {
        int taken = Math.min(Math.max(amount, 0), stored());
        consume(taken);
        return taken;
    }

    /**
     * Leert den Vorrat auf einen Schlag — <b>samt der Zellen</b>.
     *
     * <p>Für den Fall, dass die Versorgung wegbricht, und für Prüfungen, die
     * genau das nachstellen wollen, ohne zwanzigtausend Ticks zu warten.
     *
     * <p>Die Zellen gehören dazu, obwohl das Ausgehen im Tick sie verschont:
     * Wer den Vorrat auf null setzt, meint den ganzen. Bliebe hier etwas
     * übrig, wäre der Ausgangspunkt jeder Prüfung eine andere Zahl als null.
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
        // Der Zustand kommt mit zurück. Ein entladener Chunk ist kein
        // Stromausfall — wer eine Anlage verlässt und wiederkommt, soll sie
        // laufen sehen und nicht drei Sekunden warten.
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
