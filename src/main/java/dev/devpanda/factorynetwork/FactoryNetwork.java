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
 * Einstiegspunkt der Mod.
 *
 * <p>Die Arbeit liegt in den Unterpaketen: {@code lang} enthält Manifold —
 * Lexer, Parser und Prüfung —, {@code network} das Netzwerk aus Connectoren
 * und den Speicher, {@code runtime} die Ausführung der Worker.
 */
@Mod(FactoryNetwork.MOD_ID)
public final class FactoryNetwork {

    public static final String MOD_ID = "factorynetwork";

    public FactoryNetwork(IEventBus modBus, ModContainer container) {
        FnBlocks.BLOCKS.register(modBus);
        FnItems.ITEMS.register(modBus);
        FnBlockEntities.BLOCK_ENTITIES.register(modBus);
        FnMenus.MENUS.register(modBus);
        FnCreativeTabs.TABS.register(modBus);
        dev.devpanda.factorynetwork.press.FnRecipes.register(modBus);
    }
}
