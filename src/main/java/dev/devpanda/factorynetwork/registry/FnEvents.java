package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.runtime.ItemSelection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Serverseitige Ereignisse, die die Mod selbst braucht.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class FnEvents {

    /**
     * Aufgelöste Auswahlen werden gemerkt, damit ein Muster über
     * zwanzigtausend Einträge den Server nicht in jedem Tick beschäftigt. Beim
     * Neuladen der Datenpakete ändern sich Tags — dann muss der
     * Zwischenspeicher weg, sonst arbeitet die Fabrik mit einem Bestand von
     * vorhin weiter.
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemSelection.invalidate();
    }

    private FnEvents() {
    }
}
