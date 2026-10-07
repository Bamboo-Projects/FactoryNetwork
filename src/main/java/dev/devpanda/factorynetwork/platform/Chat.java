package dev.devpanda.factorynetwork.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Telling a player something.
 *
 * <p>One of the classes that may differ per Minecraft version: the single
 * call with a flag became two calls later on. Whoever has something to say
 * says it here.
 */
public final class Chat {

    private Chat() {
    }

    /**
     * Shows a message to this player.
     *
     * @param overlay above the hotbar instead of in the chat — for a remark
     *                about what the player just did, which should not stay
     */
    public static void tell(Player player, Component message, boolean overlay) {
        player.displayClientMessage(message, overlay);
    }
}
