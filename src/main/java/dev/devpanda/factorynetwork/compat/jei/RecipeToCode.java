package dev.devpanda.factorynetwork.compat.jei;

import dev.devpanda.factorynetwork.client.EditorInsert;
import dev.devpanda.factorynetwork.client.RecipeText;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.registry.FnMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI's "+" with the terminal open: the recipe shown becomes a
 * {@code recipe} declaration in the editor, at the cursor.
 *
 * <p>Any recipe, of any mod: what the slots show is enough — inputs, outputs,
 * their amounts, a tag where JEI shows one. The machine stays to be named.
 */
final class RecipeToCode implements IUniversalRecipeTransferHandler<TerminalMenu> {

    @Override
    public Class<? extends TerminalMenu> getContainerClass() {
        return TerminalMenu.class;
    }

    @Override
    public Optional<MenuType<TerminalMenu>> getMenuType() {
        return Optional.of(FnMenus.TERMINAL.get());
    }

    @Override
    public IRecipeTransferError transferRecipe(TerminalMenu container, Object recipe, IRecipeSlotsView slots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }
        List<RecipeText.Part> inputs = parts(slots.getSlotViews(RecipeIngredientRole.INPUT));
        List<RecipeText.Part> outputs = parts(slots.getSlotViews(RecipeIngredientRole.OUTPUT));
        if (outputs.isEmpty()) {
            return null;
        }
        boolean inEditor = EditorInsert.insert(RecipeText.of(inputs, outputs));
        player.sendOverlayMessage(Component.translatable(inEditor
                ? "message.factorynetwork.jei.recipe_inserted"
                : "message.factorynetwork.jei.recipe_copied"));
        return null;
    }

    private static List<RecipeText.Part> parts(List<IRecipeSlotView> views) {
        List<RecipeText.Part> parts = new ArrayList<>();
        for (IRecipeSlotView view : views) {
            if (view.isEmpty()) {
                continue;
            }
            Optional<net.minecraft.tags.TagKey<?>> tag = view.getTagKey();
            Optional<ItemStack> item = view.getDisplayedItemStack();
            if (item.isPresent()) {
                String selection = tag.isPresent()
                        ? RecipeText.tag(tag.get().location())
                        : RecipeText.item(item.get().getItem());
                merge(parts, new RecipeText.Part(item.get().getCount(), selection));
                continue;
            }
            view.getDisplayedIngredient(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK).ifPresent(fluid ->
                    merge(parts, new RecipeText.Part(fluid.getAmount(),
                            RecipeText.fluid(BuiltInRegistries.FLUID.getKey(fluid.getFluid())))));
        }
        return parts;
    }

    /** The same selection twice — a shapeless recipe with two of one — counts once, summed. */
    private static void merge(List<RecipeText.Part> parts, RecipeText.Part next) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).selection().equals(next.selection())) {
                parts.set(i, new RecipeText.Part(parts.get(i).amount() + next.amount(), next.selection()));
                return;
            }
        }
        parts.add(next);
    }
}
