package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Map;

/**
 * A fluid cell.
 *
 * <p>It belongs in the same drive as an item cell. A second drive just for
 * fluids would be one more block for the same action — and the question
 * "which one do I take" has no good answer.
 */
public class FluidCellItem extends Item {

    private final FluidCellTier tier;

    public FluidCellItem(FluidCellTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public FluidCellTier tier() {
        return tier;
    }

    public static FluidCellTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof FluidCellItem cell ? cell.tier() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        Map<Fluid, Long> contents = CellFormat.FLUIDS.read(stack, context.registries());
        long total = CellFormat.total(contents);
        // Name both limits: a cell is usually full on types, not on amount —
        // whoever sees only the amount looks for the problem elsewhere.
        lines.add(Component.translatable("item.factorynetwork.fluid_cell.types",
                contents.size(), tier.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.factorynetwork.fluid_cell.amount",
                total, tier.amount()).withStyle(ChatFormatting.GRAY));
        if (contents.isEmpty()) {
            lines.add(Component.translatable("item.factorynetwork.fluid_cell.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CellFormat.FLUIDS.summarize(stack).types() > 0;
    }

    /** The bar shows the tighter of the two limits. */
    @Override
    public int getBarWidth(ItemStack stack) {
        CellFormat.Summary summary = CellFormat.FLUIDS.summarize(stack);
        double byTypes = summary.types() / (double) tier.types();
        double byAmount = summary.amount() / (double) tier.amount();
        return (int) Math.round(Math.min(1.0, Math.max(byTypes, byAmount)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3C86A8;
    }
}
