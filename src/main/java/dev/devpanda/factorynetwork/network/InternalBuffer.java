package dev.devpanda.factorynetwork.network;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * Ein Stromspeicher, der von außen annimmt und von innen verbraucht.
 *
 * <p><b>Warum nicht einfach {@code EnergyStorage}:</b> Ein
 * {@code EnergyStorage} mit Entnahmerate null gibt nach außen nichts ab —
 * richtig so, eine Maschine ist kein Akku. Aber {@code extractEnergy} prüft
 * dieselbe Rate, und damit kommt auch die Maschine selbst nicht an ihren
 * Vorrat. Sie füllt sich, verbraucht nie etwas und läuft trotzdem nicht.
 *
 * <p>Genau das war bei der Presse der Fall, und beim Netz wäre es dasselbe
 * geworden. Deshalb hier ein eigener Weg nach innen, der die Rate nicht
 * fragt.
 */
public class InternalBuffer extends EnergyStorage {

    public InternalBuffer(int capacity, int maxInput) {
        super(capacity, maxInput, 0);
    }

    /** Nimmt von innen, ohne die Entnahmerate zu fragen. */
    public void consume(int amount) {
        energy = Math.max(0, energy - Math.max(0, amount));
    }

    /**
     * Füllt von innen, ohne die Annahmerate zu fragen.
     *
     * <p>{@code receiveEnergy} deckelt auf das, was in einem Tick von außen
     * hineinpasst — richtig für ein Kabel, falsch für „hier ist ein voller
     * Vorrat".
     */
    public void charge(int amount) {
        energy = Math.min(getMaxEnergyStored(), energy + Math.max(0, amount));
    }

    /** Reicht der Vorrat für so viel? */
    public boolean has(int amount) {
        return energy >= amount;
    }

    /** Leert den Vorrat auf einen Schlag. */
    public void drain() {
        energy = 0;
    }
}
