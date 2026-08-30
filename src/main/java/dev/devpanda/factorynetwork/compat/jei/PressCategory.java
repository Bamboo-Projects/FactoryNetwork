package dev.devpanda.factorynetwork.compat.jei;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.press.PressRecipe;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Locale;

/**
 * Die Rezepte der Presse in JEI.
 *
 * <p><b>Ohne diese Klasse ist der Stempel unauffindbar.</b> Die Presse
 * braucht zwei Zutaten, und die eine davon ist ein Werkzeug, das nirgends
 * sonst vorkommt: Wer einen Plattenstempel im Kreativmenü sieht, hat keinen
 * Weg herauszufinden, wozu er gut ist. In der Werkbank steht das Rezept, in
 * der Presse stand es bisher nirgends.
 *
 * <p><b>Der Stempel wird nicht verbraucht</b>, und das muss man sehen. Er
 * steht deshalb links als eigener Platz und trägt darunter das Wort
 * „bleibt" — eine Zutat, die man einmal legt, ist etwas anderes als eine,
 * die jedes Mal draufgeht.
 *
 * <p>Energie und Dauer stehen als Text und nicht als Balken: Ein Balken
 * zeigt einen Anteil von etwas, und hier gibt es kein Ganzes, gegen das man
 * ihn lesen könnte.
 */
public class PressCategory implements IRecipeCategory<RecipeHolder<PressRecipe>> {

    public static final RecipeType<RecipeHolder<PressRecipe>> TYPE = RecipeType.create(
            FactoryNetwork.MOD_ID, "press",
            (Class<RecipeHolder<PressRecipe>>) (Class<?>) RecipeHolder.class);

    /** Die Maße der Fläche, auf der ein Rezept steht. */
    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    public PressCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(
                new ItemStack(FnBlocks.PRESS.get()));
        // Vanillas Pfeil aus dem Ofenfenster: Er zeigt dieselbe Sache, und
        // ein eigener wäre ein zweiter Pfeil für dieselbe Bedeutung.
        this.arrow = helper.drawableBuilder(
                        ResourceLocation.withDefaultNamespace(
                                "textures/gui/container/furnace.png"),
                        79, 35, 24, 17)
                .setTextureSize(256, 256).build();
    }

    @Override
    public RecipeType<RecipeHolder<PressRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.factorynetwork.jei.press");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<PressRecipe> holder,
                          IFocusGroup focuses) {
        PressRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 5)
                .addIngredients(recipe.stamp());
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 27)
                .addIngredients(recipe.material());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 109, 16)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<PressRecipe> holder, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        PressRecipe recipe = holder.value();
        arrow.draw(graphics, 74, 16);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        // Der Stempel bleibt liegen — die wichtigste Auskunft dieser Fläche.
        graphics.drawString(font,
                Component.translatable("gui.factorynetwork.jei.press.stamp"),
                22, 9, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui.factorynetwork.jei.press.energy",
                        String.format(Locale.GERMANY, "%,d", recipe.energy())),
                22, 31, 0x808080, false);
        graphics.drawString(font,
                Component.translatable("gui.factorynetwork.jei.press.time",
                        String.format(Locale.GERMANY, "%.1f", recipe.ticks() / 20.0F)),
                22, 41, 0x808080, false);
    }
}
