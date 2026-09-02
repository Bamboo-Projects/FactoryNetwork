package dev.devpanda.factorynetwork.upgrade;

/**
 * A recipe as an equipped machine runs it.
 *
 * @param ticks  how long a run takes
 * @param energy what it costs in total
 * @param batch  how many workpieces come out of it
 */
public record Tuned(int ticks, int energy, int batch) {
}
