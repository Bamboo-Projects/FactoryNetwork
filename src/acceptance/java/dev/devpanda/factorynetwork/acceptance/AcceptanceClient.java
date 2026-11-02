package dev.devpanda.factorynetwork.acceptance;

import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.client.EditorApp;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import dev.devpanda.factorynetwork.network.packet.StorageActionPacket;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Optional test companion on the client.
 *
 * <p>With {@code -Dfn.acceptance.editor=true} it opens the real editor once
 * the server opens a terminal, as before. Otherwise it checks the storage
 * view end to end: once the server's stock arrives in an open terminal, it
 * logs how many rows came over, shift-clicks the first one, and logs how many
 * of that item then sit in the inventory.
 */
@Mod(value = "factorynetwork_acceptance", dist = Dist.CLIENT)
public final class AcceptanceClient {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final boolean EDITOR = Boolean.getBoolean("fn.acceptance.editor");

    private TerminalMenu opened;
    private int ticks;
    private ItemStack requested = ItemStack.EMPTY;
    private int countBefore;
    private Object lastScreen;

    public AcceptanceClient() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null) {
            opened = null;
            ticks = 0;
            return;
        }
        if (client.screen != lastScreen) {
            lastScreen = client.screen;
            LOGGER.info("FN_ACCEPTANCE_CLIENT_SCREEN {} rows={}",
                    client.screen == null ? "none" : client.screen.getClass().getSimpleName(),
                    ClientStorageView.rows().size());
        }
        if (client.screen instanceof TerminalScreen parent
                && client.player.containerMenu instanceof TerminalMenu menu && menu != opened) {
            if (!EDITOR) {
                if (!ClientStorageView.rows().isEmpty()) {
                    opened = menu;
                    ticks = 0;
                    var row = ClientStorageView.rows().getFirst();
                    requested = row.stack().copy();
                    countBefore = count(client);
                    LOGGER.info("FN_ACCEPTANCE_CLIENT_ROWS rows={} knownTypes={}",
                            ClientStorageView.rows().size(), ClientStorageView.knownTypes());
                    PacketDistributor.sendToServer(new StorageActionPacket(
                            StorageActionPacket.Kind.EXTRACT_TO_INVENTORY, ItemKey.of(requested),
                            requested.getMaxStackSize()));
                }
            } else if (EditorApp.open(client, parent, menu)) {
                opened = menu;
                ticks = 0;
            }
        }
        if (opened == null) {
            return;
        }
        ticks++;
        if (!EDITOR && ticks == 20) {
            LOGGER.info("FN_ACCEPTANCE_CLIENT_SHIFT item={} before={} after={}",
                    requested.getHoverName().getString(), countBefore, count(client));
        }
        if (EDITOR && ticks == 120) {
            Screenshot.grab(client.gameDirectory, "acceptance-native-editor.png", client.getMainRenderTarget(),
                    message -> LOGGER.info("Acceptance screenshot: {}", message.getString()));
        }
    }

    private int count(Minecraft client) {
        int total = 0;
        for (ItemStack stack : client.player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, requested)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
