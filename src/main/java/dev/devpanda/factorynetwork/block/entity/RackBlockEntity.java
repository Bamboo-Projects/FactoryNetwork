package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.item.ProcessorItem;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Serverschrank mit acht Plätzen für Prozessoren.
 *
 * <p>Dasselbe Bild wie beim Laufwerk: ein Regal, in das man Bauteile steckt.
 * Was der Schrank kann, steht in den Bauteilen, nicht in ihm — er allein
 * trägt nichts.
 */
public class RackBlockEntity extends ShelfBlockEntity {

    public static final int SLOTS = 8;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.RACK.get(), pos, state, SLOTS);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof ProcessorItem;
    }

    @Override
    public dev.devpanda.factorynetwork.client.menu.ShelfMenu.Layout layout() {
        return dev.devpanda.factorynetwork.client.menu.ShelfMenu.RACK;
    }

    public ItemStack processor(int slot) {
        return getItem(slot);
    }

    public void setProcessor(int slot, ItemStack stack) {
        setItem(slot, stack);
    }

    public NonNullList<ItemStack> contents() {
        return parts();
    }

    /** Wie viele gleichzeitige Abläufe dieser Schrank trägt. */
    public int threads() {
        int total = 0;
        for (ItemStack stack : parts()) {
            total += ProcessorItem.threadsOf(stack) * stack.getCount();
        }
        return total;
    }
}
