package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.client.screen.LabelGunScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Öffnet das Namensfenster der Label-Gun.
 *
 * <p>Eigene Klasse, weil {@code LabelGunItem} auf beiden Seiten läuft und
 * Client-Klassen dort nicht auftauchen dürfen: Java löst beim Laden einer
 * Methode alle Typen ihrer Signatur auf, und ein Server, der
 * {@code Minecraft} zu laden versucht, stürzt ab.
 */
public final class LabelGunScreenOpener {

    public static void open(ItemStack gun) {
        Minecraft.getInstance().setScreen(new LabelGunScreen(gun));
    }

    private LabelGunScreenOpener() {
    }
}
