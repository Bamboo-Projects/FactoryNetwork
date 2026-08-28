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
 * Baut zwei verschränkte Hälften — beide mit derselben Nummer.
 *
 * <p><b>Warum ein eigenes Rezept.</b> Ein gewöhnliches Rezept liefert immer
 * dasselbe Ergebnis; zwei Hälften müssen aber bei jedem Bau eine neue,
 * gemeinsame Nummer bekommen. Zwei Paare aus demselben Rezept dürfen sich
 * nicht kennen, sonst verbänden sich zwei Brücken in derselben Welt
 * zufällig.
 *
 * <p><b>Und warum das Ergebnis nur eine Hälfte ist:</b> Ein Rezept hat genau
 * ein Ausgabefeld. Die zweite Hälfte kommt über {@code getRemainingItems}
 * zurück — denselben Weg, auf dem ein Eimer aus einem Kuchenrezept
 * zurückkommt. Sie landet damit im Raster und nicht auf dem Boden.
 */
public class EntanglementRecipe extends CustomRecipe {

    public EntanglementRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * Zwei Netzkerne und ein Kristall, in beliebiger Anordnung.
     *
     * <p>Formlos, weil die Anordnung hier nichts erzählt: Es gibt keine
     * Vorder- und keine Rückseite an einer Verschränkung.
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
     * Beide Hälften auf einmal, als ein Stapel zu zweit.
     *
     * <p><b>Und nichts gemerkt.</b> Ein Rezept gibt es einmal, nicht einmal
     * je Werkbank — der {@code RecipeManager} hält je JSON genau ein Objekt,
     * geteilt über alle Spieler und jeden Crafter-Block. Ein Feld darin, das
     * zwischen dem Zusammenbauen und dem Herausnehmen etwas aufhebt, gehört
     * allen gleichzeitig: Zwei Bauten kreuzten sich, und jeder Spieler
     * hielte eine Hälfte, deren Partner woanders liegt.
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
