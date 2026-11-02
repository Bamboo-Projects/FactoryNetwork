package dev.devpanda.factorynetwork.hall;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** The hall block as an item: says which hall it opens. */
public class HallItem extends BlockItem {

    private final HallTier tier;

    public HallItem(HallBlock block, Properties properties) {
        super(block, properties);
        this.tier = block.tier();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> adder,
                                net.minecraft.world.item.TooltipFlag flag) {
        HallBlock.describe(stack, tier, dev.devpanda.factorynetwork.platform.Tooltips.lines(adder));
    }
}
