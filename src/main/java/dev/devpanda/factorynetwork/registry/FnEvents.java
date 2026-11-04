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
     * A program disk on a network block: right-click writes the running
     * program onto it, sneak and right-click reads it into the draft.
     * Before the block sees the click, so that a terminal does not open.
     */
    @SubscribeEvent
    public static void onProgramDisk(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getItemStack().getItem() instanceof dev.devpanda.factorynetwork.item.ProgramDiskItem)) {
            return;
        }
        var level = event.getLevel();
        var pos = event.getPos();
        dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity controller =
                level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity own
                        ? own
                        : dev.devpanda.factorynetwork.network.ControllerRegistry.owning(level, pos).orElse(null);
        if (controller == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        ItemStack disk = event.getItemStack();
        if (!player.isShiftKeyDown()) {
            dev.devpanda.factorynetwork.item.ProgramDiskItem.write(disk, controller.project());
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.factorynetwork.disk.written", controller.project().files().size()));
            return;
        }
        var program = dev.devpanda.factorynetwork.item.ProgramDiskItem.read(disk);
        if (program == null) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.factorynetwork.disk.blank"));
            return;
        }
        if (!dev.devpanda.factorynetwork.network.packet.DeployProgramPacket.mayEdit(player, controller)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.factorynetwork.disk.not_yours"));
            return;
        }
        controller.acceptDraft(program, player.getUUID(), player.getGameProfile().name());
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                "message.factorynetwork.disk.loaded", program.files().size()));
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
