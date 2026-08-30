package dev.devpanda.factorynetwork.upgrade;

/**
 * Ein Rezept, wie eine bestückte Maschine es ausführt.
 *
 * @param ticks  wie lange ein Durchlauf dauert
 * @param energy was er insgesamt kostet
 * @param batch  wie viele Werkstücke dabei entstehen
 */
public record Tuned(int ticks, int energy, int batch) {
}
