package dev.devpanda.factorynetwork.press;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Was gerade in der Presse liegt.
 *
 * <p>Zwei Plätze mit fester Bedeutung statt eines Rasters: Der Stempel gehört
 * nach oben, das Material in die Mitte. Ein Raster wäre allgemeiner und
 * gleichzeitig unklarer — man müsste raten, welcher Platz was tut.
 */
public record PressInput(ItemStack stamp, ItemStack material) implements RecipeInput {

    @Override
    public ItemStack getItem(int slot) {
        return switch (slot) {
            case 0 -> stamp;
            case 1 -> material;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
