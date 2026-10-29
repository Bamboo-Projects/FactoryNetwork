package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.press.FnRecipes;
import dev.devpanda.factorynetwork.press.PressRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.List;

/**
 * The press recipes, as the client knows them.
 *
 * <p>Since 1.21.2 recipes stay on the server. The server sends the press's
 * along (see {@code FnEvents.sendPressRecipes}), and they are kept here for
 * whoever shows them on the client, today JEI.
 *
 * <p>With high priority, so the list is filled before JEI, which listens to
 * the same event, asks for it.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, value = Dist.CLIENT)
public final class ClientPressRecipes {

    private static List<RecipeHolder<PressRecipe>> recipes = List.of();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void received(RecipesReceivedEvent event) {
        recipes = List.copyOf(event.getRecipeMap().byType(FnRecipes.PRESS.get()));
    }

    public static List<RecipeHolder<PressRecipe>> all() {
        return recipes;
    }

    private ClientPressRecipes() {
    }
}
