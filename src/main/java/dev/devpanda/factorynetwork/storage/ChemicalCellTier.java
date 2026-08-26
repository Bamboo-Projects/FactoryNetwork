package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * Die Größen einer Chemikalienzelle.
 *
 * <p>Dieselbe Rechnung wie bei Gegenständen und Flüssigkeiten — so viele
 * Sorten, so viel Menge —, und dieselben Sortenzahlen wie bei den
 * Flüssigkeiten: Chemikalien gibt es in wenigen Sorten und großen Mengen.
 *
 * <p><b>Die Namen sagen Kilo-Millibucket.</b> Mekanism rechnet in Millibucket
 * wie das Spiel bei Flüssigkeiten, aber in ganz anderen Größenordnungen: Ein
 * Elektrolyseur macht Hunderte je Sekunde. Eine Zelle in Eimern zu beschriften
 * ergäbe vierstellige Zahlen auf jedem Gegenstand.
 *
 * <p><b>Die Zahlen sind gesetzt, nicht hergeleitet</b> — wie die an den
 * Serverbauteilen (5.4). Wie sie sich anfühlen, zeigt eine Runde Spielen; sie
 * lassen sich an dieser einen Stelle ändern.
 */
public enum ChemicalCellTier implements StringRepresentable, CellSize {

    K64("64k", 4, 64_000),
    K256("256k", 8, 256_000),
    K1024("1024k", 16, 1_024_000),
    K4096("4096k", 32, 4_096_000);

    private final String label;
    private final int types;
    private final long amount;

    ChemicalCellTier(String label, int types, long amount) {
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

    @Override
    public String getSerializedName() {
        return label;
    }
}
