package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * A storage cell for Thaumaturge essentia, one entry per aspect.
 *
 * <p>Keys are aspect identifiers as strings ({@link CellFormat#ESSENTIA}), so
 * the cell needs nothing from Thaumaturge to be read, written or shown; only
 * moving essentia in and out of the world needs the mod.
 */
public class EssentiaCellItem extends Item {

    private final EssentiaCellTier tier;

    public EssentiaCellItem(EssentiaCellTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public EssentiaCellTier tier() {
        return tier;
    }

    public static EssentiaCellTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof EssentiaCellItem cell ? cell.tier() : null;
    }

    private static Map<String, Long> contents(ItemStack stack) {
        return CellInventory.of(stack, tierOf(stack), CellFormat.ESSENTIA,
                net.minecraft.core.RegistryAccess.EMPTY).contentsView();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> adder,
                                net.minecraft.world.item.TooltipFlag flag) {
        java.util.List<Component> lines = dev.devpanda.factorynetwork.platform.Tooltips.lines(adder);
        if (!net.neoforged.fml.ModList.get().isLoaded("thaumaturge")) {
            lines.add(Component.translatable("item.factorynetwork.essentia_cell.needs_thaumaturge")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        Map<String, Long> contents = contents(stack);
        long total = CellFormat.total(contents);
        lines.add(Component.translatable("item.factorynetwork.essentia_cell.types",
                contents.size(), tier.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.essentia_cell.amount",
                total, tier.amount()).withStyle(ChatFormatting.GRAY));
        if (contents.isEmpty()) {
            lines.add(Component.translatable("item.factorynetwork.essentia_cell.empty")
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
        double byAmount = (double) CellFormat.total(contents) / tier.amount();
        double byTypes = (double) contents.size() / tier.types();
        return (int) Math.round(Math.min(1, Math.max(byAmount, byTypes)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB15CD6;
    }
}
