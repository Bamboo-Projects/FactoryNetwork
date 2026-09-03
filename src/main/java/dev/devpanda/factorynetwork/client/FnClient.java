package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import dev.devpanda.bamboocef.web.api.FnWeb;
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
        WorldPointer.tick();
        OverlayProof.tick();
        WorldSurfaceDemo.tick();
        IdeLauncher.tick();
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
        if (event.isUseItem() && WorldPointer.click()) {
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
            net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        dev.devpanda.factorynetwork.client.render.ConnectorPartModels.all(event::register);
        // The press's ram also belongs to no block state: where it currently
        // stands hangs on the progress in the BlockEntity.
        event.register(dev.devpanda.factorynetwork.client.render.PressRenderer.RAM);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // The editor is central to this mod, so the web runtime is asked for
        // right away: BambooCEF then starts Chromium at the title screen and
        // the first editor open skips the cold start.
        FnWeb.prepare();
        // The cable is narrower than a block and therefore needs a render type
        // that tolerates transparent edges.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.CABLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.DISPLAY.get(), RenderType.cutout());
        });
    }

    private FnClient() {
    }
}
