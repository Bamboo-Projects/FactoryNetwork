package dev.devpanda.factorynetwork.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A part for a bay in the server rack.
 *
 * <p>One class for all three types, because they differ in nothing but the
 * number and the word before it. What the number <b>means</b> is not here
 * but where it takes effect: {@code ServerBay} adds it up, the controller
 * enforces it.
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

    /** The type of this item, or null if it is neither. */
    public static ServerPart partOf(ItemStack stack) {
        return stack.getItem() instanceof ServerPartItem item ? item.part() : null;
    }

    /** The value of this item, or null if it is not a part. */
    public static int valueOf(ItemStack stack) {
        return stack.getItem() instanceof ServerPartItem item ? item.value() : 0;
    }

    /** The value, but only if the item is of the requested type. */
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
