package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.network.ServerBay;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A server chassis.
 *
 * <p>The server itself: a metal frame with three slots. On its own it can do
 * nothing — <b>and that is the point</b>. Whoever puts twelve of them in a
 * rack has twelve servers, but only the hardware inside says what they
 * achieve.
 *
 * <p>It is equipped in the rack. You can pull a finished chassis out, carry
 * it away and plug it into another rack — the hardware comes along.
 */
public class ServerChassisItem extends Item {

    public ServerChassisItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        NonNullList<ItemStack> parts = ServerChassis.read(stack);
        ServerBay bay = ServerBay.of(parts.get(ServerPart.CPU.ordinal()),
                parts.get(ServerPart.RAM.ordinal()),
                parts.get(ServerPart.DISK.ordinal()));
        if (!bay.occupied()) {
            lines.add(Component.translatable("item.factorynetwork.server_chassis.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // Each type on its own, including the missing one: a chassis that
        // lacks the disk otherwise looks in a chest like a finished server,
        // and then it does not run in the rack.
        line(lines, ServerPart.CPU, bay.cpu());
        line(lines, ServerPart.RAM, bay.ram());
        line(lines, ServerPart.DISK, bay.disk());
        if (!bay.complete()) {
            lines.add(Component.translatable("item.factorynetwork.server_chassis.incomplete")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void line(List<Component> lines, ServerPart part, int value) {
        Component name = Component.translatable(
                "screen.factorynetwork.rack.part." + part.prefix());
        lines.add(Component.translatable(value > 0
                                ? "item.factorynetwork.server_chassis.part"
                                : "item.factorynetwork.server_chassis.missing",
                        name, value)
                .withStyle(value > 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
    }

    /** An equipped chassis carries its bar, like a full cell. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !ServerChassis.isEmpty(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        NonNullList<ItemStack> parts = ServerChassis.read(stack);
        long filled = parts.stream().filter(part -> !part.isEmpty()).count();
        return (int) Math.round(filled / (double) ServerChassis.SLOTS * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ServerChassis.isEmpty(stack) ? 0x5FA83C
                : ServerChassis.read(stack).stream().allMatch(part -> !part.isEmpty())
                        ? 0x5FA83C : 0xE8AC3E;
    }
}
