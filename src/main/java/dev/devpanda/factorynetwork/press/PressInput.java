package dev.devpanda.factorynetwork.press;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/**
 * What currently lies in the press.
 *
 * <p>A stamp and several material slots — fixed meanings instead of a grid:
 * the stamp belongs on top, the material below it. A grid would be more
 * general and at the same time less clear; one would have to guess which slot
 * does what.
 *
 * <p><b>Among the material slots there is no order.</b> Whoever inserts
 * redstone on the left and copper on the right means the same as the other way
 * round — which ingredient comes from which slot is worked out by
 * {@link Assignment}.
 */
public record PressInput(ItemStack stamp, List<ItemStack> materials) implements RecipeInput {

    public PressInput {
        materials = List.copyOf(materials);
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == 0) {
            return stamp;
        }
        int index = slot - 1;
        return index >= 0 && index < materials.size() ? materials.get(index) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1 + materials.size();
    }

    /**
     * Is there anything in it at all?
     *
     * <p>Minecraft asks this before it searches for recipes. Without this
     * version a press with empty slots would count as loaded, and the recipe
     * manager would run through all recipes on every tick.
     */
    @Override
    public boolean isEmpty() {
        return stamp.isEmpty() && materials.stream().allMatch(ItemStack::isEmpty);
    }
}
