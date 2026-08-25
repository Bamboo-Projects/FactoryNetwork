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
 * Zeigt das Netz als Linien in der Welt.
 *
 * <p><b>Angeklickt wird einmal, gezeigt wird dauerhaft.</b> Ein Rechtsklick
 * auf irgendeinen Teil des Netzes merkt sich dessen Stelle im Werkzeug;
 * solange man es danach in der Hand hält, wird das Netz laufend nachgereicht
 * und gezeichnet. Wer den Engpass sucht, will nicht jedes Kabel einzeln
 * anvisieren — er will sehen, wo die Linien rot werden.
 *
 * <p>Nachgereicht wird nur alle paar Ticks. Ein Netz mit tausend Kabeln bei
 * jedem Tick zu verschicken wäre spürbar, und so schnell ändert sich keine
 * Anlage.
 */
public class NetworkAnalyserItem extends Item {

    /** Wie oft nachgereicht wird, in Ticks. */
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
     * Was hier hängt — oder, wenn es kein Gerät ist, dass das Netz beobachtet
     * wird.
     *
     * <p><b>Am Rechtsklick und nicht in der Sicht durch Wände.</b> Die
     * Knotenbeschriftung wird gar nicht gezeichnet; der Analysator malt
     * Würfel. Die Frage „was kann diese Maschine" stellt sich ohnehin dort,
     * wo man davorsteht.
     */
    private static Component deviceLine(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity
                        connector) {
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

    /** Die Stelle, die dieses Werkzeug beobachtet. */
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
