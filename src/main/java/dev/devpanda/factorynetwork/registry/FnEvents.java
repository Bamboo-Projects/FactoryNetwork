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
    /**
     * The press recipes go to the client too.
     *
     * <p>Since 1.21.2 the server keeps recipes to itself unless asked; JEI
     * needs these to show the press. See {@code ClientPressRecipes}.
     */
    @SubscribeEvent
    public static void sendPressRecipes(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        event.sendRecipes(dev.devpanda.factorynetwork.press.FnRecipes.PRESS.get());
    }

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
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * The wrench's right-click without sneaking: if it carries settings, the
     * connector under the cursor gets them. Without, the click goes on as
     * usual and opens the name window.
     */
    @SubscribeEvent
    public static void onWrenchPaste(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isShiftKeyDown()) {
            return;
        }
        ItemStack tool = event.getItemStack();
        if (!dev.devpanda.factorynetwork.item.SettingsCopy.canCarry(tool)
                || !dev.devpanda.factorynetwork.item.SettingsCopy.carries(tool)) {
            return;
        }
        var part = dev.devpanda.factorynetwork.item.SettingsCopy.partAt(
                event.getLevel(), event.getPos(), event.getHitVec());
        if (part == null) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            dev.devpanda.factorynetwork.item.SettingsCopy.paste(event.getEntity(), tool, part);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * The wrench's left-click on a connector reads its settings — instead
     * of breaking the block, which a wrench in creative would do at once.
     */
    @SubscribeEvent
    public static void onWrenchCopy(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled()
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        ItemStack tool = event.getEntity().getMainHandItem();
        if (!dev.devpanda.factorynetwork.item.SettingsCopy.canCarry(tool)) {
            return;
        }
        var level = event.getLevel();
        var hit = event.getEntity().pick(
                event.getEntity().blockInteractionRange(), 0.0F, false);
        var part = dev.devpanda.factorynetwork.item.SettingsCopy.partAt(level, event.getPos(),
                hit instanceof net.minecraft.world.phys.BlockHitResult block
                        && block.getBlockPos().equals(event.getPos()) ? block : null);
        if (part == null) {
            return;
        }
        if (!level.isClientSide()) {
            dev.devpanda.factorynetwork.item.SettingsCopy.copy(event.getEntity(), tool, part);
        }
        event.setCanceled(true);
    }

    private FnEvents() {
    }
}
