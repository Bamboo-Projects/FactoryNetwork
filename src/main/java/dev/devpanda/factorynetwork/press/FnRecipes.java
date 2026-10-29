package dev.devpanda.factorynetwork.press;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The recipe type of the press. */
public final class FnRecipes {

    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, FactoryNetwork.MOD_ID);

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, FactoryNetwork.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<PressRecipe>> PRESS =
            TYPES.register("press", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return FactoryNetwork.MOD_ID + ":press";
                }
            });

    /**
     * Two entangled halves with a shared number.
     *
     * <p>Its own recipe, because the result is a different one on every craft:
     * two pairs must not know each other.
     */
    public static final DeferredHolder<RecipeSerializer<?>,
            RecipeSerializer<dev.devpanda.factorynetwork.crafting.EntanglementRecipe>>
            ENTANGLEMENT = SERIALIZERS.register("entanglement",
                    () -> dev.devpanda.factorynetwork.crafting.EntanglementRecipe.SERIALIZER);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PressRecipe>>
            PRESS_SERIALIZER = SERIALIZERS.register("press", PressRecipe.Serializer::create);

    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeBookCategory>
            BOOK = DeferredRegister.create(
                    net.minecraft.core.registries.Registries.RECIPE_BOOK_CATEGORY, FactoryNetwork.MOD_ID);

    /**
     * The press's page in the recipe book.
     *
     * <p>Since 1.21.2 every recipe names one. The press puts nothing in the
     * book, but the field has to point somewhere.
     */
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeBookCategory,
            net.minecraft.world.item.crafting.RecipeBookCategory>
            PRESS_BOOK = BOOK.register("press", net.minecraft.world.item.crafting.RecipeBookCategory::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
        BOOK.register(bus);
    }

    private FnRecipes() {
    }
}
