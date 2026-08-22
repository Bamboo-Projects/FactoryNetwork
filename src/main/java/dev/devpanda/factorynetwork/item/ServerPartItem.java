package dev.devpanda.factorynetwork.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Ein Bauteil für einen Einschub im Serverschrank.
 *
 * <p>Eine Klasse für alle drei Arten, weil sie sich in nichts unterscheiden
 * außer der Zahl und dem Wort davor. Was die Zahl <b>bedeutet</b>, steht
 * nicht hier, sondern dort, wo sie wirkt: {@code ServerBay} rechnet sie
 * zusammen, der Controller setzt sie durch.
 */
public class ServerPartItem extends Item {

    private final ServerPart part;
    private final int value;

    public ServerPartItem(ServerPart part, int value, Properties properties) {
        super(properties.stacksTo(16));
        this.part = part;
        this.value = value;
    }

    public ServerPart part() {
        return part;
    }

    public int value() {
        return value;
    }

    /** Die Art dieses Gegenstands, oder null, wenn er keins von beidem ist. */
    public static ServerPart partOf(ItemStack stack) {
        return stack.getItem() instanceof ServerPartItem item ? item.part() : null;
    }

    /** Der Wert dieses Gegenstands, oder null, wenn er kein Bauteil ist. */
    public static int valueOf(ItemStack stack) {
        return stack.getItem() instanceof ServerPartItem item ? item.value() : 0;
    }

    /** Der Wert, aber nur wenn der Gegenstand von der gesuchten Art ist. */
    public static int valueOf(ItemStack stack, ServerPart wanted) {
        return stack.getItem() instanceof ServerPartItem item && item.part() == wanted
                ? item.value()
                : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable(
                        "item.factorynetwork.part." + part.prefix() + ".hint", value)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.part.bay")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
