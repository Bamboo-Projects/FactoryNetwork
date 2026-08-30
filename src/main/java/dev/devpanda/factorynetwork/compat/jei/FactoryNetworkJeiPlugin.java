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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Die Anbindung an JEI.
 *
 * <p><b>Wozu überhaupt.</b> Die Presse hat einen eigenen Rezepttyp, und der
 * steht in keinem Rezeptbuch: Ohne diese Klasse gibt es im Spiel keinen Weg
 * herauszufinden, dass ein Eisenbarren unter dem Plattenstempel zu einer
 * Platte wird. Fünf Rezepte, die niemand findet, sind fünf Rezepte, die es
 * nicht gibt.
 *
 * <p><b>Warum kein Schalter für „JEI vorhanden".</b> Anders als bei Mekanism
 * und Ars Nouveau fragt hier nichts nach der fremden Mod: JEI sucht sich
 * seine Plugins selbst über die Annotation. Wer JEI nicht hat, lädt diese
 * Klasse nie — sie wird von keiner anderen Stelle im Quelltext angefasst.
 * Deshalb steht sie auch allein in ihrem Paket.
 *
 * <p><b>Die Presse ist ihr eigener Auslöser.</b> Wer den Block im
 * Kreativmenü anklickt oder im Spiel davorsteht und R drückt, sieht ihre
 * Rezepte — das ist der Weg, den ein Spieler von jeder anderen Maschine
 * kennt.
 */
@JeiPlugin
public class FactoryNetworkJeiPlugin implements IModPlugin {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new PressCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Aus der Welt und nicht aus einer eigenen Liste: Ein Datenpaket darf
        // Rezepte hinzufügen oder wegnehmen, und JEI soll zeigen, was
        // wirklich gilt.
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<RecipeHolder<PressRecipe>> recipes =
                level.getRecipeManager().getAllRecipesFor(FnRecipes.PRESS.get());
        registration.addRecipes(PressCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(FnBlocks.PRESS.get()), PressCategory.TYPE);
    }
}
