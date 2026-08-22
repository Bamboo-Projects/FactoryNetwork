package dev.devpanda.factorynetwork.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Ein Prozessor für den Serverschrank.
 *
 * <p>Er bringt gleichzeitige Abläufe, nicht schnellere. „Schneller" hieße
 * mehr Schritte je Tick, und das merkt niemand — ein Ablauf, der in einem
 * Tick fertig wird, wird nicht sichtbar fertiger. „Mehr gleichzeitig" liest
 * man: vier Dinge auf einmal statt zwei.
 */
public class ProcessorItem extends Item {

    private final int threads;

    public ProcessorItem(int threads, Properties properties) {
        super(properties.stacksTo(16));
        this.threads = threads;
    }

    /** Wie viele gleichzeitige Abläufe dieser Prozessor trägt. */
    public int threads() {
        return threads;
    }

    /** Null, wenn das gar kein Prozessor ist. */
    public static int threadsOf(ItemStack stack) {
        return stack.getItem() instanceof ProcessorItem processor ? processor.threads() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("item.factorynetwork.processor.threads", threads)
                .withStyle(ChatFormatting.GRAY));
    }
}
