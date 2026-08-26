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
        // Die Grenzen für Nutzercode gehören dem Serverbetreiber, nicht dem
        // Quelltext. Ohne diese Zeile liegt die Datei nie neben der Welt.
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                FnConfig.SERVER_SPEC);
        // Die Ressourcenarten sind offen, aber nur beim Laden: Was ein
        // Programm bedeutet, darf nicht davon abhängen, wann jemand etwas
        // anmeldet. Der Aufruf lädt die Klasse und damit die eingebauten drei.
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
            // Fremde Arten vor dem Einfrieren. Auch ohne die Mod, die sie
            // mitbringt: Sonst hieße source:source in einem Pack ohne Ars
            // Nouveau „keine Ressourcenart" statt „diese Mod fehlt".
            dev.devpanda.factorynetwork.compat.ars.ArsSource.register();
            dev.devpanda.factorynetwork.runtime.ResourceKinds.freeze();
        });
        FnBlocks.BLOCKS.register(modBus);
        FnItems.ITEMS.register(modBus);
        FnBlockEntities.BLOCK_ENTITIES.register(modBus);
        FnMenus.MENUS.register(modBus);
        FnCreativeTabs.TABS.register(modBus);
        dev.devpanda.factorynetwork.press.FnRecipes.register(modBus);
        // Das Handbuch nur, wenn GuideME da ist — sonst startet die Mod ohne
        // Handbuch statt gar nicht. Dieselbe Haltung wie bei Jade.
        if (net.neoforged.fml.ModList.get().isLoaded("guideme")) {
            dev.devpanda.factorynetwork.compat.guide.FnGuide.register();
        }
    }
}
