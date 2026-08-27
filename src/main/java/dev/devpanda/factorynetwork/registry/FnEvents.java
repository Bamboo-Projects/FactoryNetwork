package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.Wrenches;
import dev.devpanda.factorynetwork.runtime.ItemSelection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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

    /**
     * Ein Schraubenschlüssel nimmt einen Anschluss ab und lässt das Kabel
     * stehen.
     *
     * <p><b>Wozu.</b> Wer heute einen Anschluss loswerden will, muss den
     * Kabelblock abbauen — und damit den Strang, der durch ihn läuft, und
     * die anderen fünf Anschlüsse gleich mit. An einer Maschinenwand
     * bedeutet das, sechs Dinge kaputtzumachen, um eines zu ändern.
     *
     * <p><b>Warum als Ereignis und nicht am Block.</b> Der Klick soll
     * abgefangen werden, bevor der Block ihn sieht: Sonst öffnet ein
     * Rechtsklick erst das Namensfenster und nimmt den Anschluss danach ab.
     * AE2 hängt aus demselben Grund an derselben Stelle.
     */
    @SubscribeEvent
    public static void onWrenchUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack tool = event.getItemStack();
        if (!Wrenches.disassembling(event.getEntity(), tool)) {
            return;
        }
        var level = event.getLevel();
        if (!Wrenches.takePart(level, event.getPos(), event.getHitVec())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private FnEvents() {
    }
}
