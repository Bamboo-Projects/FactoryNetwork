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
 * The press recipes in JEI.
 *
 * <p><b>Without this class the stamp is impossible to find.</b> The press
 * needs two ingredients, and one of them is a tool that appears nowhere else:
 * whoever sees a plate stamp in the creative menu has no way to find out what
 * it is good for. In the crafting table the recipe is shown, in the press it
 * was shown nowhere until now.
 *
 * <p><b>The stamp is not consumed</b>, and that has to be visible. It
 * therefore sits on the left as its own slot and carries the word "stays"
 * beneath it — an ingredient you place once is a different thing from one that
 * is spent every time.
 *
 * <p>Energy and duration are given as text, not as a bar: a bar shows a
 * fraction of something, and here there is no whole to read it against.
 */
public class PressCategory implements IRecipeCategory<RecipeHolder<PressRecipe>> {

    public static final RecipeType<RecipeHolder<PressRecipe>> TYPE = RecipeType.create(
            FactoryNetwork.MOD_ID, "press",
            (Class<RecipeHolder<PressRecipe>>) (Class<?>) RecipeHolder.class);

    /** The dimensions of the area a recipe sits on. */
    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    public PressCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(
                new ItemStack(FnBlocks.PRESS.get()));
        // Vanilla's arrow from the furnace screen: it shows the same thing,
        // and a custom one would be a second arrow for the same meaning.
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
        // The ingredients side by side, in the recipe's order. That the press
        // accepts them in any slots need not be shown here — whoever sees
        // three fields puts three things in.
        var materials = recipe.materials();
        for (int i = 0; i < materials.size(); i++) {
            var sized = materials.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 19, 27)
                    .addIngredients(sized.ingredient())
                    .addRichTooltipCallback((view, tooltip) -> {
                        if (sized.count() > 1) {
                            tooltip.add(Component.translatable(
                                    "gui.factorynetwork.jei.press.count", sized.count()));
                        }
                    });
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 109, 16)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<PressRecipe> holder, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        PressRecipe recipe = holder.value();
        arrow.draw(graphics, 74, 16);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        // The stamp stays put — the most important piece of information on
        // this area.
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
