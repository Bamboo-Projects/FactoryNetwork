package dev.devpanda.factorynetwork.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * The colour of a cable.
 *
 * <p><b>Colour is not just paint: it decides what connects.</b> Two cables of
 * different colours run past each other without seeing each other. That is the
 * whole point — it lets several networks run through the same wall, each with
 * its own channels.
 *
 * <p>{@link #NONE} is the default colour and connects to everything. With it
 * you build the ordinary network and separate only where needed — otherwise
 * you would have to commit to a colour with the very first cable.
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
     * Do these two colours connect?
     *
     * <p>The same colour always, and the default colour with every one.
     * Everything else runs past.
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
