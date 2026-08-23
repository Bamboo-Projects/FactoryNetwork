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
 * Ein Servergehäuse.
 *
 * <p>Der Server selbst: ein Blech mit drei Steckplätzen. Für sich genommen
 * kann es nichts — <b>und das ist der Punkt</b>. Wer zwölf davon in einen
 * Schrank stellt, hat zwölf Server, aber erst die Hardware darin sagt, was
 * sie leisten.
 *
 * <p>Bestückt wird im Schrank. Man kann ein fertiges Gehäuse herausziehen,
 * wegtragen und in einen anderen Schrank stecken — die Hardware kommt mit.
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
        // Jede Art einzeln, auch die fehlende: Ein Gehäuse, dem der
        // Datenträger fehlt, sieht in einer Truhe sonst aus wie ein fertiger
        // Server, und im Schrank läuft es dann nicht.
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

    /** Ein bestücktes Gehäuse trägt seinen Balken, wie eine volle Zelle. */
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
