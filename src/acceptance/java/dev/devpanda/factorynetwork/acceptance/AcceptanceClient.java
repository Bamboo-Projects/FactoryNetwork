package dev.devpanda.factorynetwork.acceptance;

import dev.devpanda.factorynetwork.client.EditorApp;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Optional test companion: open the real editor after the server opens its real terminal menu. */
@Mod(value = "factorynetwork_acceptance", dist = Dist.CLIENT)
public final class AcceptanceClient {
    private TerminalMenu opened;
    private int ticks;

    public AcceptanceClient() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null) { opened = null; ticks = 0; return; }
        if (client.screen instanceof TerminalScreen parent
                && client.player.containerMenu instanceof TerminalMenu menu && menu != opened) {
            if (EditorApp.open(client, parent, menu)) { opened = menu; ticks = 0; }
        }
        if (opened != null && ++ticks == 120) {
            Screenshot.grab(client.gameDirectory, "acceptance-native-editor.png", client.getMainRenderTarget(),
                    message -> com.mojang.logging.LogUtils.getLogger().info("Acceptance screenshot: {}", message.getString()));
        }
    }
}
