package dev.devpanda.factorynetwork.press;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

/**
 * One operation of the press: stamp plus material yields a component.
 *
 * <p><b>The stamp is not consumed.</b> It is a tool, not an ingredient —
 * whoever has one presses with it as often as they like. That separates the
 * one-time effort (getting hold of a stamp) from the ongoing one (procuring
 * material), and it is exactly this separation that makes a production chain
 * interesting instead of merely long.
 */
public record PressRecipe(Ingredient stamp,
                          java.util.List<net.neoforged.neoforge.common.crafting.SizedIngredient>
                                  materials,
                          net.minecraft.world.item.ItemStackTemplate result, int energy, int ticks)
        implements Recipe<PressInput> {

    /**
     * How many ingredients a recipe may demand at most.
     *
     * <p>That is how many material slots the press has. A recipe with more
     * would be one that never comes about — and a bug you notice only while
     * playing instead of while loading.
     */
    public static final int MOST_MATERIALS = 3;

    @Override
    public boolean matches(PressInput input, Level level) {
        if (!stamp.test(input.stamp())) {
            return false;
        }
        // Each ingredient needs its own slot, and the order does not matter:
        // whoever inserts redstone on the left and copper on the right means
        // the same as the other way round.
        return Assignment.fits(materials, input.materials(), PressRecipe::covers);
    }

    /** Does this slot satisfy this ingredient — kind and amount? */
    private static boolean covers(
            net.neoforged.neoforge.common.crafting.SizedIngredient demand, ItemStack slot) {
        return demand.test(slot) && slot.getCount() >= demand.count();
    }

    /**
     * Which slot each ingredient comes from, or {@code null}.
     *
     * <p>For consuming: whoever draws off three ingredients must know which
     * slot each comes from — otherwise they draw twice from the same one.
     */
    public int[] slotsFor(PressInput input) {
        return Assignment.assign(materials, input.materials(), PressRecipe::covers);
    }

    @Override
    public ItemStack assemble(PressInput input) {
        return result.create();
    }

    /** What comes out; for the recipe lookups and JEI. */
    public ItemStack resultItem() {
        // A template since 26.1: recipes load before items have their
        // components, so a recipe may no longer hold a finished stack.
        return result.create();
    }

    /**
     * Special: the recipe book does not know the press. Otherwise Minecraft
     * warns at every load that the recipe cannot be placed.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    /** Not in the recipe book: the press shows its recipes through JEI. */
    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return FnRecipes.PRESS_BOOK.get();
    }

    @Override
    public RecipeSerializer<PressRecipe> getSerializer() {
        return FnRecipes.PRESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<PressRecipe> getType() {
        return FnRecipes.PRESS.get();
    }

    /**
     * The serializer — once for the file, once for the wire.
     *
     * <p>Since 26.1 a serializer is a record of the two; this class only
     * holds them.
     */
    public static final class Serializer {

        static final MapCodec<PressRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC.fieldOf("stamp")
                                .forGetter(PressRecipe::stamp),
                        net.neoforged.neoforge.common.crafting.SizedIngredient.NESTED_CODEC
                                .listOf(1, MOST_MATERIALS).fieldOf("materials")
                                .forGetter(PressRecipe::materials),
                        net.minecraft.world.item.ItemStackTemplate.CODEC.fieldOf("result").forGetter(PressRecipe::result),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("energy", 2_000)
                                .forGetter(PressRecipe::energy),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("ticks", 100)
                                .forGetter(PressRecipe::ticks)
                ).apply(instance, PressRecipe::new));

        static final StreamCodec<RegistryFriendlyByteBuf, PressRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, PressRecipe::stamp,
                        net.neoforged.neoforge.common.crafting.SizedIngredient.STREAM_CODEC
                                .apply(ByteBufCodecs.list()), PressRecipe::materials,
                        net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC, PressRecipe::result,
                        ByteBufCodecs.VAR_INT, PressRecipe::energy,
                        ByteBufCodecs.VAR_INT, PressRecipe::ticks,
                        PressRecipe::new);

        static RecipeSerializer<PressRecipe> create() {
            return new RecipeSerializer<>(CODEC, STREAM_CODEC);
        }

        private Serializer() {
        }
    }
}
