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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<net.minecraft.network.chat.Component> adder,
                                net.minecraft.world.item.TooltipFlag flag) {
        java.util.List<net.minecraft.network.chat.Component> lines =
                dev.devpanda.factorynetwork.platform.Tooltips.lines(adder);
        CellFormat.Loaded<ItemKey> loaded = CellFormat.ITEMS.load(stack, context.registries());
        Map<ItemKey, Long> contents = loaded.contents();
        int unreadable = loaded.unreadable().size();
        // Unreadable entries count on both lines: they occupy the cell, and a
        // cell that reads "3 of 8" while refusing a fourth type is a riddle.
        long total = CellFormat.total(contents) + CellFormat.ITEMS.amountOf(loaded.unreadable());
        // Name both limits: a cell is usually full on types, not on amount —
        // whoever sees only the amount looks for the problem elsewhere.
        lines.add(Component.translatable("item.factorynetwork.cell.types",
                contents.size() + unreadable, tier.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.cell.amount",
                total, tier.amount()).withStyle(ChatFormatting.GRAY));
        if (unreadable > 0) {
            lines.add(Component.translatable("item.factorynetwork.cell.unreadable", unreadable)
                    .withStyle(ChatFormatting.YELLOW));
        } else if (contents.isEmpty()) {
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
