package dev.devpanda.factorynetwork.network;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * An energy store that accepts from outside and consumes from within.
 *
 * <p><b>Why not simply {@code EnergyStorage}:</b> An
 * {@code EnergyStorage} with an extraction rate of zero gives nothing to the
 * outside — rightly so, a machine is not a battery. But {@code extractEnergy}
 * checks the same rate, and so the machine itself cannot reach its own reserve
 * either. It fills up, never consumes anything, and still does not run.
 *
 * <p>That is exactly what happened with the press, and for the network it
 * would have turned out the same. Hence a separate way inward here, one that
 * does not ask the rate.
 */
public class InternalBuffer extends EnergyStorage {

    public InternalBuffer(int capacity, int maxInput) {
        super(capacity, maxInput, 0);
    }

    /** Takes from within, without asking the extraction rate. */
    public void consume(int amount) {
        energy = Math.max(0, energy - Math.max(0, amount));
    }

    /**
     * Fills from within, without asking the intake rate.
     *
     * <p>{@code receiveEnergy} caps at what fits in from outside within one
     * tick — right for a cable, wrong for "here is a full reserve".
     */
    public void charge(int amount) {
        energy = Math.min(getMaxEnergyStored(), energy + Math.max(0, amount));
    }

    /** Is the reserve enough for that much? */
    public boolean has(int amount) {
        return energy >= amount;
    }

    /** Empties the reserve in one go. */
    public void drain() {
        energy = 0;
    }
}
