package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * Die Größen einer Flüssigkeitszelle.
 *
 * <p>Dieselbe Rechnung wie bei den Gegenstandszellen — so viele Sorten, so
 * viel Menge —, aber andere Zahlen: Flüssigkeiten gibt es in weniger Sorten
 * und größeren Mengen. Vier Sorten in der kleinsten reichen für Wasser, Lava
 * und zwei aus dem Pack; vierundsechzig wären ein Platz, den nie jemand füllt.
 *
 * <p><b>Die Namen sagen Eimer, nicht Kilo.</b> Bei den Gegenstandszellen
 * stimmt die Zahl im Namen mit dem Inhalt überein, und der Preis folgt ihr —
 * eine 64k kostet vierundsechzig kleine. Eine Flüssigkeitszelle „1k" mit
 * vierundsechzig Eimern hätte diese Ehrlichkeit nicht.
 *
 * <p>Die Zahlen sind gesetzt, nicht hergeleitet. Sie lassen sich an einer
 * Stelle ändern, wenn sich das Spiel anders anfühlt als gedacht.
 */
public enum FluidCellTier implements StringRepresentable, CellSize {

    B64("64", 4, 64_000),
    B256("256", 8, 256_000),
    B1024("1024", 16, 1_024_000),
    B4096("4096", 32, 4_096_000);

    private final String label;
    private final int types;
    private final long amount;

    FluidCellTier(String label, int types, long amount) {
        this.label = label;
        this.types = types;
        this.amount = amount;
    }

    @Override
    public int types() {
        return types;
    }

    /** Wie viele Millibucket insgesamt hineinpassen. */
    @Override
    public long amount() {
        return amount;
    }

    @Override
    public String label() {
        return label;
    }

    /** Wie viele Eimer das sind — die Zahl, die auf der Zelle steht. */
    public long buckets() {
        return amount / 1000;
    }

    @Override
    public String getSerializedName() {
        return label;
    }
}
