package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class FnClient {

    /**
     * Counts the quiet in the editor and saves the draft.
     *
     * <p>On the client's tick and not on the screen: the tick keeps running
     * when someone closes the window in the same second in which they typed
     * the last character.
     *
     * <p><b>Nothing about the web runtime happens here any more.</b> Pumping
     * Chromium, the overlays and the surfaces in the world are the library's
     * own beat and hang in its own client class. What stays are this mod's
     * ticks: the ones that use the library, not the ones that drive it.
     */
    @SubscribeEvent
    public static void tickDraft(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientProjectState.tick();
        // The web side only where BambooCEF is installed: without it, its
        // classes cannot even be loaded.
        if (WebSupport.installed()) {
            WebBridge.tick();
        }
    }

    /**
     * Right-click on a display in the world: click where the crosshair points.
     *
     * <p>If the gaze hits a face, it gets the click, and the player's swing is
     * suppressed — otherwise it would swing at the air next to the display. If
     * it hits none, everything runs as usual.
     */
    @SubscribeEvent
    public static void clickWorldSurface(
            net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && WebSupport.installed() && WebBridge.click()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * When leaving a world nothing is left standing.
     *
     * <p>Otherwise the next controller would find the last one's draft — and
     * write it over its own program at the first keystroke.
     */
    @SubscribeEvent
    public static void forgetDraft(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (WebSupport.installed()) {
            WebBridge.clear();
        }
        ClientProjectState.clear();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FnMenus.TERMINAL.get(), TerminalScreen::new);
        event.register(FnMenus.PRESS.get(),
                dev.devpanda.factorynetwork.client.screen.PressScreen::new);
        event.register(FnMenus.SHELF.get(),
                dev.devpanda.factorynetwork.client.screen.ShelfScreen::new);
        event.register(FnMenus.BURNER.get(),
                dev.devpanda.factorynetwork.client.screen.BurnerScreen::new);
        event.register(FnMenus.ROUTER.get(),
                dev.devpanda.factorynetwork.client.screen.RouterScreen::new);
        event.register(FnMenus.NAME.get(),
                dev.devpanda.factorynetwork.client.screen.NameScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.DISPLAY.get(),
                dev.devpanda.factorynetwork.client.render.DisplayRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.ROUTER.get(),
                dev.devpanda.factorynetwork.client.render.RouterRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.DRIVE.get(),
                dev.devpanda.factorynetwork.client.render.DriveRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.RACK.get(),
                dev.devpanda.factorynetwork.client.render.RackRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.CABLE_BUS.get(),
                dev.devpanda.factorynetwork.client.render.CableBusRenderer::new);
        event.registerBlockEntityRenderer(
                dev.devpanda.factorynetwork.registry.FnBlockEntities.PRESS.get(),
                dev.devpanda.factorynetwork.client.render.PressRenderer::new);
    }

    /**
     * The models of the connectors belong to no block state.
     *
     * <p>Which faces of a cable a part occupies stands in the BlockEntity.
     * Models that no one requests through a blockstate file Minecraft does not
     * load by itself — they are registered here and drawn by the
     * CableBusRenderer.
     */
    @SubscribeEvent
    public static void registerPartModels(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone event) {
        dev.devpanda.factorynetwork.client.render.ConnectorPartModels.register(event);
        // The press's ram also belongs to no block state: where it currently
        // stands hangs on the progress in the BlockEntity.
        event.register(dev.devpanda.factorynetwork.client.render.PressRenderer.RAM,
                net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel
                        .simpleModelWrapper(dev.devpanda.factorynetwork.client.render.PressRenderer.RAM_MODEL));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Where the web runtime is installed it is asked for right away:
        // BambooCEF then starts Chromium at the title screen and the first
        // open of the web editor skips the cold start.
        if (WebSupport.installed()) {
            WebBridge.prepare();
        }
        // The cable used to need a render type for its transparent edges here.
        // Since 1.21.5 Minecraft reads that from the texture itself.
    }

    /**
     * The {@code /fnweb} commands exist only where the web runtime does.
     *
     * <p>Registered from here and not by the command class itself: a class
     * that subscribes on its own is loaded in every game, and this one names
     * BambooCEF.
     */
    @SubscribeEvent
    public static void registerWebCommands(
            net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        if (WebSupport.installed()) {
            WebBridge.registerCommands(event);
        }
        ShareCommands.register(event);
        dev.devpanda.factorynetwork.client.menu.TerminalLayout.fittingRows = () ->
                dev.devpanda.factorynetwork.client.menu.TerminalLayout.rowsFor(
                        net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight());
        dev.devpanda.factorynetwork.client.menu.TerminalLayout.fittingColumns = () ->
                dev.devpanda.factorynetwork.client.menu.TerminalLayout.columnsFor(
                        net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth());
        dev.devpanda.factorynetwork.item.ProgramDiskItem.openShare = () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (!dev.devpanda.factorynetwork.network.packet.ShareAllowedPacket.allowedHere()) {
                if (client.player != null) {
                    client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            "message.factorynetwork.share.disabled"));
                }
                return;
            }
            client.setScreen(new dev.devpanda.factorynetwork.client.screen.ShareScreen());
        };
    }

    private FnClient() {
    }
}
