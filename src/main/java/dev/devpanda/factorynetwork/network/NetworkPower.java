package dev.devpanda.factorynetwork.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

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
     * Der Puffer.
     *
     * <p>Nimmt an, gibt aber nichts heraus: Ein Netz ist kein Akku, aus dem
     * die Nachbarmaschine zapft.
     *
     * <p><b>Das beschreibt den Stand, nicht mehr die Absicht.</b> Am
     * 2026-08-24 wurde entschieden, dass das Netz Strom verteilt — über
     * Worker mit {@code filter power}, nicht von selbst. Bis das gebaut ist,
     * stimmt der Satz oben; die Begründung dahinter gilt nicht mehr. Siehe
     * {@code docs/entscheidungen.md}, „Strom wird geleitet und gespeichert".
     */
    private final InternalBuffer buffer =
            new InternalBuffer(Power.CAPACITY, Power.MAX_INPUT);

    private State state = State.OFF;
    private int bootTicks;
    private int draw;

    /** Wie viel das Netz gerade zieht, in FE je Tick. */
    public int draw() {
        return draw;
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

    public int stored() {
        return buffer.getEnergyStored();
    }

    public int capacity() {
        return buffer.getMaxEnergyStored();
    }

    /** Der Anschluss für Fremdmods. */
    public InternalBuffer buffer() {
        return buffer;
    }

    /**
     * Ein Tick Strom.
     *
     * <p>Erst zahlen, dann arbeiten: Was hier abgezogen wird, ist die
     * Bereitschaft für diesen Tick. Reicht der Vorrat nicht, geht das Netz
     * aus — und kommt erst wieder, wenn genug beisammen ist, um das
     * Hochfahren zu überstehen und danach noch zu laufen.
     */
    public void tick() {
        if (draw <= 0) {
            // Ein Netz ohne Verbraucher braucht nichts und läuft immer.
            state = State.RUNNING;
            bootTicks = 0;
            return;
        }
        if (!buffer.has(draw)) {
            state = State.OFF;
            bootTicks = 0;
            buffer.drain();
            return;
        }
        buffer.consume(draw);
        switch (state) {
            case RUNNING -> { }
            case BOOTING -> {
                if (--bootTicks <= 0) {
                    state = State.RUNNING;
                    bootTicks = 0;
                }
            }
            case OFF -> {
                if (buffer.getEnergyStored() >= Power.restartThreshold(draw)) {
                    state = State.BOOTING;
                    bootTicks = Power.BOOT_TICKS;
                }
            }
            default -> { }
        }
    }

    /** Füllt den Puffer, ohne den Umweg über einen Anschluss und ohne Rate. */
    public void fill(int amount) {
        buffer.charge(amount);
    }

    /**
     * Leert den Vorrat auf einen Schlag.
     *
     * <p>Für den Fall, dass die Versorgung wegbricht — und für Prüfungen, die
     * genau das nachstellen wollen, ohne zwanzigtausend Ticks zu warten.
     */
    public void empty() {
        buffer.drain();
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
