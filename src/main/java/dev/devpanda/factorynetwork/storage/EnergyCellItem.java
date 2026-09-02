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
 * An energy cell.
 *
 * <p>It belongs in the same drive as an item or fluid cell and enlarges the
 * network's store. A dedicated battery block would be one more block for the
 * same action — and the question "which one do I take" has no good answer.
 *
 * <p>Its contents are a single number, held as {@code Charge} in the item. No
 * {@link CellFormat}: that exists to send ids through a registry, and a
 * number has no id.
 *
 * <p><b>An inserted cell costs power continuously</b> ({@code
 * Power.PER_CELL}, like every other). That is not a side effect here but
 * fitting: a battery that costs power to hold power is a battery with
 * self-discharge.
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

    /** How much FE this item holds. */
    public static int chargeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        CompoundTag tag = data.copyTag();
        EnergyCellTier tier = tierOf(stack);
        int charge = tag.getInt(KEY_CHARGE);
        // A cell that has been downgraded from a larger tier — or comes from
        // a world where the numbers were different —, gives out no more than
        // fits into it.
        return tier == null ? 0 : Math.max(0, Math.min(charge, tier.capacity()));
    }

    /** Writes the charge into the item. */
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
        // The sentence you want to read when you get your first cell: it
        // counts toward the network, not toward the machine it sits next to.
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
