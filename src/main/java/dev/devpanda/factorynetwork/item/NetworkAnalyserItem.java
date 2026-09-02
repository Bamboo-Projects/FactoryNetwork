package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.analyser.AnalyserScan;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.packet.AnalyserDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Shows the network as lines in the world.
 *
 * <p><b>Clicked once, shown continuously.</b> A right-click on any part of
 * the network remembers its location in the tool; as long as you hold the
 * tool afterwards, the network is streamed and drawn continuously. Whoever
 * looks for the bottleneck does not want to target every cable individually
 * — they want to see where the lines turn red.
 *
 * <p>It is streamed only every few ticks. Sending a network with a thousand
 * cables every tick would be noticeable, and no installation changes that
 * fast.
 */
public class NetworkAnalyserItem extends Item {

    /** How often it is streamed, in ticks. */
    private static final int INTERVAL = 10;

    private static final String KEY_POS = "AnalysedPos";

    public NetworkAnalyserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide) {
            return ControllerRegistry.owning(level, pos).isPresent()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Optional<ControllerBlockEntity> controller = ControllerRegistry.owning(level, pos);
        if (controller.isEmpty()) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "message.factorynetwork.analyser.no_network"), true);
            }
            return InteractionResult.CONSUME;
        }
        remember(context.getItemInHand(), controller.get().getBlockPos());
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(deviceLine(level, pos), true);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * What hangs here — or, if it is not a device, that the network is being
     * watched.
     *
     * <p><b>On the right-click and not in the through-the-walls view.</b> The
     * node labelling is not drawn at all; the analyser paints cubes. The
     * question "what can this machine do" comes up anyway where you stand in
     * front of it.
     */
    private static Component deviceLine(Level level, BlockPos pos) {
        // <b>The controller first.</b> Since 30 Aug it is the place through
        // which everything goes — and thus the first to become tight. That
        // is the information you need when expanding: is it the cable or the
        // controller that is the bottleneck?
        if (level.getBlockEntity(pos) instanceof ControllerBlockEntity here) {
            return Component.translatable(
                    "message.factorynetwork.analyser.controller",
                    dev.devpanda.factorynetwork.network.Bandwidth.usage(
                            here.bandwidthUsed(), here.bandwidth()),
                    here.extensionCount());
        }
        // Via Connectors and not via the BlockEntity: at this spot there can
        // be a connector block or a cable with a connector. If several sit
        // on it, there is no information here — which one is meant, a point
        // in space does not say.
        var connector = dev.devpanda.factorynetwork.block.entity.Connectors.at(level, pos);
        if (connector != null) {
            var profile = dev.devpanda.factorynetwork.block.entity.DeviceScan.of(connector);
            String name = connector.label().isEmpty() ? "Ohne Namen" : connector.label();
            String slots = "";
            var items = connector.machineInventoryAll();
            if (items != null && items.getSlots() > 0) {
                slots = " · Fächer 0–" + (items.getSlots() - 1);
            }
            return Component.literal(name + ": " + profile.abilities() + slots);
        }
        return Component.translatable("message.factorynetwork.analyser.watching");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot,
                              boolean selected) {
        if (level.isClientSide || !selected || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        BlockPos pos = remembered(stack);
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ControllerBlockEntity controller)) {
            return;
        }
        var data = AnalyserScan.of(controller);
        PacketDistributor.sendToPlayer(player,
                new AnalyserDataPacket(data.nodes(), data.links(), data.summary()));
    }

    /** The location this tool watches. */
    public static BlockPos remembered(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY_POS)) {
            return null;
        }
        long packed = data.copyTag().getLong(KEY_POS);
        return BlockPos.of(packed);
    }

    private static void remember(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putLong(KEY_POS, pos.asLong()));
    }
}
