package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * Die Größen einer Energiezelle.
 *
 * <p><b>Eine Zahl, nicht zwei.</b> Gegenstands- und Flüssigkeitszellen tragen
 * zwei Grenzen — wie viele Sorten und wie viel Menge —, und die Sorten sind
 * das Knappe, das zum Sortieren treibt. Bei Strom gibt es keine Sorten. Eine
 * Energiezelle ist damit schlichter als ihre Geschwister, und der Reiz, der
 * bei den anderen im Sortieren liegt, fehlt hier ganz.
 *
 * <p>Das ist kein Mangel, sondern die Sache selbst: Ein Akku ist eine Zahl.
 * Wer mehr will, steckt eine größere Zelle ein oder eine zweite dazu.
 * Deshalb erfüllt diese Aufzählung auch nicht {@link CellSize} — es gäbe eine
 * der beiden Zahlen zu erfinden.
 *
 * <p>Die Leiter ist die der Flüssigkeitszellen, viermal je Stufe. Die
 * kleinste trägt gut das Dreifache des Controllerpuffers
 * ({@code Power.CAPACITY}) — genug, dass sich das Einsetzen sofort bemerkbar
 * macht, und wenig genug, dass die große noch etwas bedeutet.
 */
public enum EnergyCellTier implements StringRepresentable {

    FE64K("64k", 64_000),
    FE256K("256k", 256_000),
    FE1024K("1024k", 1_024_000),
    FE4096K("4096k", 4_096_000);

    private final String label;
    private final int capacity;

    EnergyCellTier(String label, int capacity) {
        this.label = label;
        this.capacity = capacity;
    }

    /** Wie viel FE hineinpassen. */
    public int capacity() {
        return capacity;
    }

    /** Was auf der Zelle steht. */
    public String label() {
        return label;
    }

    @Override
    public String getSerializedName() {
        return label;
    }
}
