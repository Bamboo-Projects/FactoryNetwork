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
 * Eine Flüssigkeitszelle.
 *
 * <p>Sie gehört in dasselbe Laufwerk wie eine Gegenstandszelle. Ein zweites
 * Laufwerk nur für Flüssigkeiten wäre ein Block mehr für dieselbe Handlung —
 * und die Frage „welches nehme ich" hat keine gute Antwort.
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
        Map<Fluid, Long> contents = CellFormat.FLUIDS.read(stack);
        long total = CellFormat.total(contents);
        // Beide Grenzen nennen: Voll ist eine Zelle meist an den Sorten, nicht
        // an der Menge — wer nur die Menge sieht, sucht den Fehler woanders.
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
        return !CellFormat.FLUIDS.read(stack).isEmpty();
    }

    /** Der Balken zeigt die knappere der beiden Grenzen. */
    @Override
    public int getBarWidth(ItemStack stack) {
        Map<Fluid, Long> contents = CellFormat.FLUIDS.read(stack);
        double byTypes = contents.size() / (double) tier.types();
        double byAmount = CellFormat.total(contents) / (double) tier.amount();
        return (int) Math.round(Math.min(1.0, Math.max(byTypes, byAmount)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3C86A8;
    }
}
