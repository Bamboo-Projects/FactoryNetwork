package dev.devpanda.factorynetwork;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnCreativeTabs;
import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

/**
 * Entry point of the mod.
 *
 * <p>The real work lives in the subpackages: {@code lang} holds Manifold —
 * lexer, parser and checking —, {@code network} the network of connectors
 * and the storage, {@code runtime} the execution of the workers.
 */
@Mod(FactoryNetwork.MOD_ID)
public final class FactoryNetwork {

    public static final String MOD_ID = "factorynetwork";

    public FactoryNetwork(IEventBus modBus, ModContainer container) {
        // The limits for user code belong to the server operator, not the
        // source. Without this line the file never ends up beside the world.
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                FnConfig.SERVER_SPEC);
        // The resource kinds are open, but only during loading: what a
        // program means must not depend on when someone registers something.
        // The call loads the class and with it the three built-in ones.
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
            // Foreign kinds before the freeze. Even without the mod that
            // brings them: otherwise source:source in a pack without Ars
            // Nouveau would mean "no such resource kind" instead of "this mod
            // is missing".
            dev.devpanda.factorynetwork.compat.ars.ArsSource.register();
            dev.devpanda.factorynetwork.runtime.ResourceKinds.freeze();
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DevCommands::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) ->
                        dev.devpanda.factorynetwork.network.ControllerRegistry.onChunkChange(event));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Unload event) ->
                        dev.devpanda.factorynetwork.network.ControllerRegistry.onChunkChange(event));
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                dev.devpanda.factorynetwork.client.FnClientConfig.SPEC);
        // The storage link's adapters, each only where its mod runs.
        if (dev.devpanda.factorynetwork.compat.ae2.FnAe2.installed()) {
            dev.devpanda.factorynetwork.compat.ae2.Ae2Link.register(modBus);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("refinedstorage")) {
            dev.devpanda.factorynetwork.compat.refinedstorage.RsLink.register(modBus);
        }
        FnBlocks.BLOCKS.register(modBus);
        FnItems.ITEMS.register(modBus);
        dev.devpanda.factorynetwork.registry.FnComponents.COMPONENTS.register(modBus);
        FnBlockEntities.BLOCK_ENTITIES.register(modBus);
        FnMenus.MENUS.register(modBus);
        FnCreativeTabs.TABS.register(modBus);
        dev.devpanda.factorynetwork.press.FnRecipes.register(modBus);
        // The manual only if GuideME is present — otherwise the mod starts
        // without a manual instead of not at all. The same stance as with Jade.
        // And only on a client: since GuideME 26 building a guide loads client
        // classes, and a dedicated server crashed while constructing the mod.
        if (net.neoforged.fml.ModList.get().isLoaded("guideme")
                && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            dev.devpanda.factorynetwork.compat.guide.FnGuide.register();
        }
    }
}
