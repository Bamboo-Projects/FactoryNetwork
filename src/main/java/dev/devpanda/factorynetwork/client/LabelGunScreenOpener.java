package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.client.screen.LabelGunScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Opens the naming window of the label gun.
 *
 * <p>A class of its own, because {@code LabelGunItem} runs on both sides and
 * client classes must not appear there: when loading a method Java resolves
 * all types in its signature, and a server that tries to load
 * {@code Minecraft} crashes.
 */
public final class LabelGunScreenOpener {

    public static void open(ItemStack gun) {
        Minecraft.getInstance().setScreen(new LabelGunScreen(gun));
    }

    private LabelGunScreenOpener() {
    }
}
