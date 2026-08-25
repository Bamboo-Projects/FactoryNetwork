package dev.devpanda.factorynetwork.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Eine Energiezelle.
 *
 * <p>Sie gehört in dasselbe Laufwerk wie eine Gegenstands- oder
 * Flüssigkeitszelle und vergrößert den Vorrat des Netzes. Ein eigener
 * Akkublock wäre ein Block mehr für dieselbe Handlung — und die Frage
 * „welches nehme ich" hat keine gute Antwort.
 *
 * <p>Ihr Inhalt ist eine einzige Zahl, und die steht als {@code Charge} im
 * Gegenstand. Kein {@link CellFormat}: Der ist dafür da, Kennungen durch eine
 * Registry zu schicken, und eine Zahl hat keine Kennung.
 *
 * <p><b>Eine eingesetzte Zelle kostet laufend Strom</b> ({@code
 * Power.PER_CELL}, wie jede andere). Das ist hier kein Nebeneffekt, sondern
 * passt: Ein Akku, der Strom kostet, um Strom zu halten, ist ein Akku mit
 * Selbstentladung.
 */
public class EnergyCellItem extends Item {

    private static final String KEY_CHARGE = "Charge";

    private final EnergyCellTier tier;

    public EnergyCellItem(EnergyCellTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public EnergyCellTier tier() {
        return tier;
    }

    public static EnergyCellTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof EnergyCellItem cell ? cell.tier() : null;
    }

    /** Wie viel FE in diesem Gegenstand stehen. */
    public static int chargeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        CompoundTag tag = data.copyTag();
        EnergyCellTier tier = tierOf(stack);
        int charge = tag.getInt(KEY_CHARGE);
        // Eine Zelle, die von einer größeren Stufe herabgestuft wurde — oder
        // aus einer Welt kommt, in der die Zahlen anders standen —, gibt
        // nicht mehr her, als in sie hineinpasst.
        return tier == null ? 0 : Math.max(0, Math.min(charge, tier.capacity()));
    }

    /** Schreibt die Ladung in den Gegenstand. */
    public static void setCharge(ItemStack stack, int charge) {
        EnergyCellTier tier = tierOf(stack);
        int kept = tier == null ? 0 : Math.max(0, Math.min(charge, tier.capacity()));
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(KEY_CHARGE, kept));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("item.factorynetwork.energy_cell.charge",
                chargeOf(stack), tier.capacity()).withStyle(ChatFormatting.GRAY));
        // Der Satz, den man beim Kauf der ersten Zelle lesen will: Sie zählt
        // zum Netz, nicht zu der Maschine, neben der sie liegt.
        lines.add(Component.translatable("item.factorynetwork.energy_cell.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return chargeOf(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(chargeOf(stack) / (double) tier.capacity() * 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0A030;
    }
}
