package dev.devpanda.factorynetwork.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * Die Farbe eines Kabels.
 *
 * <p><b>Die Farbe ist nicht nur Anstrich: Sie entscheidet, was sich
 * verbindet.</b> Zwei Kabel verschiedener Farbe laufen aneinander vorbei,
 * ohne sich zu sehen. Genau darum geht es — so lassen sich mehrere Netze
 * durch dieselbe Wand führen, jedes mit eigenen Kanälen.
 *
 * <p>{@link #NONE} ist die Standardfarbe und verbindet sich mit allem. Damit
 * baut man das gewöhnliche Netz und trennt nur dort, wo es nötig ist —
 * andernfalls müsste man sich schon beim ersten Kabel für eine Farbe
 * entscheiden.
 */
public enum CableColour implements StringRepresentable {

    NONE("none", null),
    WHITE("white", DyeColor.WHITE),
    ORANGE("orange", DyeColor.ORANGE),
    MAGENTA("magenta", DyeColor.MAGENTA),
    LIGHT_BLUE("light_blue", DyeColor.LIGHT_BLUE),
    YELLOW("yellow", DyeColor.YELLOW),
    LIME("lime", DyeColor.LIME),
    PINK("pink", DyeColor.PINK),
    GRAY("gray", DyeColor.GRAY),
    LIGHT_GRAY("light_gray", DyeColor.LIGHT_GRAY),
    CYAN("cyan", DyeColor.CYAN),
    PURPLE("purple", DyeColor.PURPLE),
    BLUE("blue", DyeColor.BLUE),
    BROWN("brown", DyeColor.BROWN),
    GREEN("green", DyeColor.GREEN),
    RED("red", DyeColor.RED),
    BLACK("black", DyeColor.BLACK);

    private final String name;
    private final DyeColor dye;

    CableColour(String name, DyeColor dye) {
        this.name = name;
        this.dye = dye;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public DyeColor dye() {
        return dye;
    }

    /**
     * Verbinden sich diese beiden Farben?
     *
     * <p>Gleiche Farbe immer, und die Standardfarbe mit jeder. Alles andere
     * läuft aneinander vorbei.
     */
    public boolean connectsTo(CableColour other) {
        return this == other || this == NONE || other == NONE;
    }

    public static CableColour of(DyeColor dye) {
        for (CableColour colour : values()) {
            if (colour.dye == dye) {
                return colour;
            }
        }
        return NONE;
    }
}
