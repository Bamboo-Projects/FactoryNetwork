package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.screen.TerminalScreen;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class FnClient {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FnMenus.TERMINAL.get(), TerminalScreen::new);
        event.register(FnMenus.PRESS.get(),
                dev.devpanda.factorynetwork.client.screen.PressScreen::new);
        event.register(FnMenus.SHELF.get(),
                dev.devpanda.factorynetwork.client.screen.ShelfScreen::new);
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
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Das Kabel ist schmaler als ein Block und braucht deshalb eine
        // Zeichenart, die durchsichtige Ränder verträgt.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.CABLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(FnBlocks.DISPLAY.get(), RenderType.cutout());
        });
    }

    private FnClient() {
    }
}
