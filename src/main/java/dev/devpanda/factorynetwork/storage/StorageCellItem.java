package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * A storage cell.
 *
 * <p>It belongs in a drive. What sits inside it is held in the item itself
 * — a full cell in the chest is full storage, not an empty casing.
 */
public class StorageCellItem extends Item {

    private final CellTier tier;

    public StorageCellItem(CellTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public CellTier tier() {
        return tier;
    }

    public static CellTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof StorageCellItem cell ? cell.tier() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        Map<ItemKey, Long> contents = CellContents.read(stack, context.registries());
        long total = dev.devpanda.factorynetwork.storage.CellFormat.total(contents);
        // Name both limits: a cell is usually full on types, not on amount —
        // whoever sees only the amount looks for the problem elsewhere.
        lines.add(Component.translatable("item.factorynetwork.cell.types",
                contents.size(), tier.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.cell.amount",
                total, tier.amount()).withStyle(ChatFormatting.GRAY));
        if (contents.isEmpty()) {
            lines.add(Component.translatable("item.factorynetwork.cell.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CellFormat.ITEMS.summarize(stack).types() > 0;
    }

    /**
     * The bar shows the tighter of the two limits.
     *
     * <p>Otherwise a cell with all types occupied would show as half empty,
     * although nothing new goes in any more.
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        CellFormat.Summary summary = CellFormat.ITEMS.summarize(stack);
        double byTypes = summary.types() / (double) tier.types();
        double byAmount = summary.amount() / (double) tier.amount();
        return (int) Math.round(Math.min(1.0, Math.max(byTypes, byAmount)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5FA83C;
    }
}
