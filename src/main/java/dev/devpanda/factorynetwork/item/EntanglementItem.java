package dev.devpanda.factorynetwork.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One half of an entanglement — the pair for the quantum bridge.
 *
 * <p><b>The pair comes into being on crafting, not on clicking.</b> Whoever
 * first places two bridges and then connects them has to remember which
 * belongs where — across five thousand blocks, with a map in hand. Whoever
 * inserts two halves of the same item has nothing to remember at all: they
 * belong together, wherever you carry them.
 *
 * <p>That is how AE2 does it with the Quantum-Entangled Singularity, and the
 * idea is the better of the two.
 */
public class EntanglementItem extends Item {

    public EntanglementItem(Properties properties) {
        super(properties.stacksTo(2));
    }

    /**
     * A fresh pair — a stack of two.
     *
     * <p><b>One stack and not two items.</b> A recipe exists once, not once
     * per crafting table; whoever remembers something between assembling and
     * taking out remembers it for all players at once. Two builds would then
     * cross, and each would hold a half whose partner lies elsewhere.
     *
     * <p>A stack comes into being in one go and carries its number itself.
     *
     * <p>The number is random. Two pairs from the same recipe must not know
     * each other, otherwise two bridges in the same world would link up by
     * chance.
     */
    public static ItemStack newPair() {
        ItemStack stack = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.ENTANGLEMENT.get(), 2);
        stack.set(FnComponents.ENTANGLEMENT.get(), UUID.randomUUID());
        return stack;
    }

    /**
     * Which entanglement this stack carries, or {@code null}.
     *
     * <p><b>Only the number, no side.</b> Which of the two halves one is, no
     * one needs to know: what makes a pair is the same number in <b>two
     * different places</b> — and that is decided by the block, not the item.
     * A bridge thus cannot connect to itself, without any rule being needed
     * for it.
     */
    public static @Nullable UUID idOf(ItemStack stack) {
        return stack.get(FnComponents.ENTANGLEMENT.get());
    }

    /**
     * In the inventory one half looks like any other.
     *
     * <p>Across five thousand blocks you want to know before setting off
     * which one you carry — the same reasoning as with the remote device,
     * which names its network in the tooltip. Eight characters of the number
     * are enough to tell two pairs apart.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        UUID id = idOf(stack);
        if (id == null) {
            // The half from the creative tab has no number. Without this
            // line it looks broken rather than unfinished.
            lines.add(net.minecraft.network.chat.Component
                    .translatable("item.factorynetwork.entanglement.loose")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        lines.add(net.minecraft.network.chat.Component.translatable(
                        "item.factorynetwork.entanglement.pair",
                        id.toString().substring(0, 8))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Do these two stacks carry the same entanglement? */
    public static boolean matched(ItemStack one, ItemStack other) {
        UUID first = idOf(one);
        return first != null && first.equals(idOf(other));
    }
}
