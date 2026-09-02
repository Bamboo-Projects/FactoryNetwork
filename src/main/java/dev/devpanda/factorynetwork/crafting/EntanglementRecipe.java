package dev.devpanda.factorynetwork.crafting;

import dev.devpanda.factorynetwork.item.EntanglementItem;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Builds two entangled halves — both with the same number.
 *
 * <p><b>Why a dedicated recipe.</b> An ordinary recipe always yields the same
 * result; but two halves must get a new, shared number on every build. Two
 * pairs from the same recipe must not know each other, otherwise two bridges
 * in the same world would connect by chance.
 *
 * <p><b>And why the result is only one half:</b> a recipe has exactly one
 * output field. The second half comes back via {@code getRemainingItems} —
 * the same way a bucket comes back from a cake recipe. It thus ends up in the
 * grid and not on the floor.
 */
public class EntanglementRecipe extends CustomRecipe {

    public EntanglementRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * Two network cores and one crystal, in any arrangement.
     *
     * <p>Shapeless, because the arrangement tells nothing here: there is no
     * front and no back to an entanglement.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        int cores = 0;
        int crystals = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(FnItems.CORE_NETWORK.get())) {
                cores++;
            } else if (stack.is(FnItems.CRYSTAL.get())) {
                crystals++;
            } else {
                return false;
            }
        }
        return cores == 2 && crystals == 1;
    }

    /**
     * Both halves at once, as a stack of two.
     *
     * <p><b>And nothing remembered.</b> A recipe exists once, not once per
     * crafting table — the {@code RecipeManager} keeps exactly one object per
     * JSON, shared across all players and every crafter block. A field in it
     * that holds something between assembling and taking out belongs to
     * everyone at once: two builds would cross, and each player would hold a
     * half whose partner lies elsewhere.
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return EntanglementItem.newPair();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return dev.devpanda.factorynetwork.press.FnRecipes.ENTANGLEMENT.get();
    }
}
