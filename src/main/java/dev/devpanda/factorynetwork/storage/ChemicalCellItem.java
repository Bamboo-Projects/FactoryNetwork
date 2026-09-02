package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * A chemical cell.
 *
 * <p>It belongs in the same drive as the other two. A third drive would be
 * one more block for the same action.
 *
 * <p><b>It always exists, even without Mekanism.</b> Registering it
 * conditionally would mean: whoever removes the mod loses their cells from
 * the world — contents and all, since an unknown item disappears on load. An
 * item that always exists and whose tooltip says what it is missing is the
 * friendlier answer and keeps the same promise: without Mekanism everything
 * runs as before, only nothing goes in.
 *
 * <p><b>No Mekanism type in a signature.</b> This class is loaded on
 * registration — that is, always. What it knows about the contents, it
 * obtains as strings from {@code compat/mekanism}.
 */
public class ChemicalCellItem extends Item {

    private final ChemicalCellTier tier;

    public ChemicalCellItem(ChemicalCellTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public ChemicalCellTier tier() {
        return tier;
    }

    public static ChemicalCellTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof ChemicalCellItem cell ? cell.tier() : null;
    }

    /** What sits in this cell: id to amount. Empty without Mekanism. */
    private static Map<String, Long> contents(ItemStack stack) {
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.read(stack,
                net.minecraft.core.RegistryAccess.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        if (!dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()) {
            lines.add(Component.translatable("item.factorynetwork.chemical_cell.needs_mekanism")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Map<String, Long> contents = contents(stack);
        long total = 0;
        for (long amount : contents.values()) {
            total += amount;
        }
        // Name both limits: a cell is usually full on types, not on amount —
        // whoever sees only the amount looks for the problem elsewhere.
        lines.add(Component.translatable("item.factorynetwork.chemical_cell.types",
                contents.size(), tier.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.chemical_cell.amount",
                total, tier.amount()).withStyle(ChatFormatting.GRAY));
        if (contents.isEmpty()) {
            lines.add(Component.translatable("item.factorynetwork.chemical_cell.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !contents(stack).isEmpty();
    }

    /** The bar shows the tighter of the two limits. */
    @Override
    public int getBarWidth(ItemStack stack) {
        Map<String, Long> contents = contents(stack);
        long total = 0;
        for (long amount : contents.values()) {
            total += amount;
        }
        double byAmount = tier.amount() == 0 ? 0 : (double) total / tier.amount();
        double byTypes = tier.types() == 0 ? 0 : (double) contents.size() / tier.types();
        return (int) Math.round(Math.min(1, Math.max(byAmount, byTypes)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5CD65C;
    }
}
