package dev.devpanda.factorynetwork.platform;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The 26.1 shape of the same promise. */
public final class Names {

    private Names() {
    }

    public static String of(Item item) {
        return item.getName(new ItemStack(item)).getString();
    }
}
