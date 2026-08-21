package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * Eine Speicherzelle.
 *
 * <p>Sie gehört in ein Laufwerk. Was darin liegt, steht im Gegenstand selbst
 * — eine volle Zelle in der Truhe ist voller Speicher, kein leeres Gehäuse.
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
        Map<net.minecraft.world.item.Item, Long> contents = CellContents.read(stack);
        long total = CellContents.total(contents);
        // Beide Grenzen nennen: Voll ist eine Zelle meist an den Arten, nicht
        // an der Menge — wer nur die Menge sieht, sucht den Fehler woanders.
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
        return !CellContents.read(stack).isEmpty();
    }

    /**
     * Der Balken zeigt die knappere der beiden Grenzen.
     *
     * <p>Sonst stünde eine Zelle mit allen Arten belegt als halb leer da,
     * obwohl nichts Neues mehr hineingeht.
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        Map<net.minecraft.world.item.Item, Long> contents = CellContents.read(stack);
        double byTypes = contents.size() / (double) tier.types();
        double byAmount = CellContents.total(contents) / (double) tier.amount();
        return (int) Math.round(Math.min(1.0, Math.max(byTypes, byAmount)) * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5FA83C;
    }
}
