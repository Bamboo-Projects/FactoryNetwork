package dev.devpanda.factorynetwork.storage;

/**
 * Die beiden Grenzen einer Zelle.
 *
 * <p>Gegenstände und Flüssigkeiten haben verschiedene Größen, aber dieselbe
 * Rechnung: so viele Arten, so viel Menge. Die Grenze ist immer die Art —
 * wer alles in eine Zelle wirft, hat sie voll, lange bevor die Menge erreicht
 * ist.
 */
public interface CellSize {

    /** Wie viele verschiedene Arten hineinpassen. */
    int types();

    /** Wie viel insgesamt hineinpasst — Gegenstände oder Millibucket. */
    long amount();

    /** Was auf der Zelle steht. */
    String label();
}
