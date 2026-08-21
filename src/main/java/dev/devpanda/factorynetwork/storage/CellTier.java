package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * Die Größen einer Speicherzelle.
 *
 * <p><b>Zwei Grenzen statt einer Byte-Rechnung.</b> Applied Energistics zählt
 * Bytes: Jede Art kostet vorab acht, dann fasst ein Byte acht Gegenstände.
 * Das ist gewachsen und wird von kaum jemandem verstanden — man merkt nur,
 * dass eine Zelle früher voll ist als die Zahl im Namen vermuten lässt.
 *
 * <p>Hier stehen beide Grenzen offen da: wie viele <b>Arten</b> und wie viele
 * <b>Gegenstände</b>. Der Reiz bleibt derselbe, denn die Arten sind das
 * Knappe — wer alles in eine Zelle wirft, hat sie voll, lange bevor die Menge
 * erreicht ist. Genau das treibt zum Sortieren.
 */
public enum CellTier implements StringRepresentable, CellSize {

    K1("1k", 8, 8_000),
    K4("4k", 16, 32_000),
    K16("16k", 32, 128_000),
    K64("64k", 64, 512_000);

    private final String label;
    private final int types;
    private final long amount;

    CellTier(String label, int types, long amount) {
        this.label = label;
        this.types = types;
        this.amount = amount;
    }

    /** Wie viele verschiedene Arten hineinpassen. */
    @Override
    public int types() {
        return types;
    }

    /** Wie viele Gegenstände insgesamt hineinpassen. */
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
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
