package dev.devpanda.factorynetwork.press;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/**
 * Was gerade in der Presse liegt.
 *
 * <p>Ein Stempel und mehrere Materialplätze — feste Bedeutungen statt eines
 * Rasters: Der Stempel gehört nach oben, das Material darunter. Ein Raster
 * wäre allgemeiner und gleichzeitig unklarer; man müsste raten, welcher Platz
 * was tut.
 *
 * <p><b>Unter den Materialplätzen gibt es keine Ordnung.</b> Wer Redstone
 * links und Kupfer rechts einlegt, meint dasselbe wie andersherum — welche
 * Zutat aus welchem Platz kommt, sucht {@link Assignment}.
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
     * Liegt überhaupt etwas darin?
     *
     * <p>Minecraft fragt das, bevor es Rezepte sucht. Ohne diese Fassung
     * gälte eine Presse mit leeren Plätzen als beschickt, und der
     * Rezeptmanager liefe bei jedem Tick durch alle Rezepte.
     */
    @Override
    public boolean isEmpty() {
        return stamp.isEmpty() && materials.stream().allMatch(ItemStack::isEmpty);
    }
}
