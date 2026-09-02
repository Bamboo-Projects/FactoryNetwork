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
 * Server-side events that the mod itself needs.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class FnEvents {

    /**
     * Resolved selections are cached, so that a pattern over twenty thousand
     * entries does not keep the server busy every tick. On reloading the data
     * packs, tags change — then the cache must go, otherwise the factory keeps
     * working with a stock from before.
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemSelection.invalidate();
    }

    /**
     * A wrench removes a connector and leaves the cable standing.
     *
     * <p><b>Why.</b> Whoever wants to get rid of a connector today has to break
     * the cable block — and with it the strand running through it, and the
     * other five connectors along with it. At a machine wall that means
     * breaking six things to change one.
     *
     * <p><b>Why as an event and not on the block.</b> The click should be
     * intercepted before the block sees it: otherwise a right-click first
     * opens the name window and removes the connector afterwards. AE2 hooks in
     * at the same spot for the same reason.
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
