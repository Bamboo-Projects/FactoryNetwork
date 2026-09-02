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
        // <b>The earliest Java code this mod has.</b> Chromium can start as
        // early as the very first screen switch — and that comes during
        // loading, so before every setup event. Anyone who wants to add to
        // Chromium's command line has to do it here or not at all.
        //
        // Only does anything when fn.devtools is set; on a server the class
        // does not even exist in the first place.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            dev.devpanda.factorynetwork.web.runtime.WebDebug.requestIfEnabled();
        }
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
        FnBlocks.BLOCKS.register(modBus);
        FnItems.ITEMS.register(modBus);
        dev.devpanda.factorynetwork.registry.FnComponents.COMPONENTS.register(modBus);
        FnBlockEntities.BLOCK_ENTITIES.register(modBus);
        FnMenus.MENUS.register(modBus);
        FnCreativeTabs.TABS.register(modBus);
        dev.devpanda.factorynetwork.press.FnRecipes.register(modBus);
        // The manual only if GuideME is present — otherwise the mod starts
        // without a manual instead of not at all. The same stance as with Jade.
        if (net.neoforged.fml.ModList.get().isLoaded("guideme")) {
            dev.devpanda.factorynetwork.compat.guide.FnGuide.register();
        }
    }
}
