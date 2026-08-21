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
 * Ein Vorgang der Presse: Stempel plus Material ergibt ein Bauteil.
 *
 * <p><b>Der Stempel wird nicht verbraucht.</b> Er ist Werkzeug, nicht Zutat —
 * wer einen hat, presst damit beliebig oft. Das trennt den einmaligen Aufwand
 * (an einen Stempel kommen) vom laufenden (Material besorgen), und genau diese
 * Trennung macht eine Fertigungskette interessant statt nur lang.
 */
public record PressRecipe(Ingredient stamp, Ingredient material, ItemStack result,
                          int energy, int ticks) implements Recipe<PressInput> {

    @Override
    public boolean matches(PressInput input, Level level) {
        return stamp.test(input.stamp()) && material.test(input.material());
    }

    @Override
    public ItemStack assemble(PressInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FnRecipes.PRESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FnRecipes.PRESS.get();
    }

    /** Der Serializer — einmal für die Datei, einmal für die Leitung. */
    public static class Serializer implements RecipeSerializer<PressRecipe> {

        private static final MapCodec<PressRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("stamp")
                                .forGetter(PressRecipe::stamp),
                        Ingredient.CODEC_NONEMPTY.fieldOf("material")
                                .forGetter(PressRecipe::material),
                        ItemStack.CODEC.fieldOf("result").forGetter(PressRecipe::result),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("energy", 2_000)
                                .forGetter(PressRecipe::energy),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("ticks", 100)
                                .forGetter(PressRecipe::ticks)
                ).apply(instance, PressRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, PressRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, PressRecipe::stamp,
                        Ingredient.CONTENTS_STREAM_CODEC, PressRecipe::material,
                        ItemStack.STREAM_CODEC, PressRecipe::result,
                        ByteBufCodecs.VAR_INT, PressRecipe::energy,
                        ByteBufCodecs.VAR_INT, PressRecipe::ticks,
                        PressRecipe::new);

        @Override
        public MapCodec<PressRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PressRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
