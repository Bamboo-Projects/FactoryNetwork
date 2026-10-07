package dev.devpanda.factorynetwork.platform;

import net.minecraft.world.item.Item;

/**
 * What a thing is called, in the player's language.
 *
 * <p>One of the classes that may differ per Minecraft version: the item's own
 * description is gone in later versions, where the name is asked of a stack.
 * Messages and status lines ask here and do not care.
 */
public final class Names {

    private Names() {
    }

    /** The name of an item kind, as text. */
    public static String of(Item item) {
        return item.getDescription().getString();
    }
}
