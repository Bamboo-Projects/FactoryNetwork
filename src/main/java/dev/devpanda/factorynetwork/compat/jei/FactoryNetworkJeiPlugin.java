package dev.devpanda.factorynetwork.compat.jei;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.press.FnRecipes;
import dev.devpanda.factorynetwork.press.PressRecipe;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The integration with JEI.
 *
 * <p><b>Why at all.</b> The press has its own recipe type, and it appears in
 * no recipe book: without this class there is no way in-game to find out that
 * an iron ingot becomes a plate under the plate stamp. Five recipes no one
 * finds are five recipes that don't exist.
 *
 * <p><b>Why no switch for "JEI present".</b> Unlike with Mekanism and Ars
 * Nouveau, nothing here asks for the third-party mod: JEI finds its plugins
 * itself via the annotation. Whoever doesn't have JEI never loads this class —
 * it is touched from no other place in the source. That is also why it sits
 * alone in its package.
 *
 * <p><b>The press is its own trigger.</b> Whoever clicks the block in the
 * creative menu or stands in front of it in-game and presses R sees its
 * recipes — the path a player knows from every other machine.
 */
@JeiPlugin
public class FactoryNetworkJeiPlugin implements IModPlugin {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new PressCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // What the server sent and not a separate list: a data pack may add or
        // remove recipes, and JEI should show what actually applies.
        List<RecipeHolder<PressRecipe>> recipes =
                dev.devpanda.factorynetwork.client.ClientPressRecipes.all();
        registration.addRecipes(PressCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(FnBlocks.PRESS.get()), PressCategory.TYPE);
    }

    /** JEI's "+" with the terminal open writes the recipe into the editor. */
    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeToCode());
    }

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        dev.devpanda.factorynetwork.client.ExternalSearch.install(new JeiSearch(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        dev.devpanda.factorynetwork.client.ExternalSearch.install(null);
    }
}
