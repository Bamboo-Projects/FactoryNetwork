package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * Eine Chemikalienzelle.
 *
 * <p>Sie gehört in dasselbe Laufwerk wie die anderen beiden. Ein drittes
 * Laufwerk wäre ein Block mehr für dieselbe Handlung.
 *
 * <p><b>Es gibt sie immer, auch ohne Mekanism.</b> Bedingt zu registrieren
 * hieße: Wer die Mod entfernt, verliert seine Zellen aus der Welt — samt
 * Inhalt, denn ein unbekannter Gegenstand verschwindet beim Laden. Ein
 * Gegenstand, den es immer gibt und dessen Tooltip sagt, was ihm fehlt, ist
 * die freundlichere Antwort und erfüllt dieselbe Zusage: Ohne Mekanism läuft
 * alles wie vorher, es geht nur nichts hinein.
 *
 * <p><b>Kein Mekanism-Typ in einer Signatur.</b> Diese Klasse wird beim
 * Registrieren geladen — also immer. Was sie über den Inhalt weiß, holt sie
 * über Texte aus {@code compat/mekanism}.
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

    /** Was in dieser Zelle liegt: Kennung auf Menge. Ohne Mekanism leer. */
    private static Map<String, Long> contents(ItemStack stack) {
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.read(stack);
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
        // Beide Grenzen nennen: Voll ist eine Zelle meist an den Sorten, nicht
        // an der Menge — wer nur die Menge sieht, sucht den Fehler woanders.
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

    /** Der Balken zeigt die knappere der beiden Grenzen. */
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
