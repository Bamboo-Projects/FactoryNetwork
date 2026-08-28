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

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // Die erste Hälfte; die zweite wartet in getRemainingItems auf
        // denselben Bau. Beide teilen sich die Nummer, die hier entsteht.
        var pair = EntanglementItem.newPair();
        pending = pair.getSecond();
        return pair.getFirst();
    }

    /**
     * Die zweite Hälfte des zuletzt gebauten Paares.
     *
     * <p><b>Ein Feld und keine Rechnung:</b> Vanilla ruft {@code assemble}
     * und {@code getRemainingItems} nacheinander für denselben Bau. Eine
     * zweite Nummer hier wäre eine zweite Verschränkung — und die Hälften
     * fänden einander nie.
     */
    private ItemStack pending = ItemStack.EMPTY;

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> rest = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        if (pending.isEmpty()) {
            return rest;
        }
        // In den Platz des Kristalls: Er ist verbraucht, dort ist Raum.
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(FnItems.CRYSTAL.get())) {
                rest.set(slot, pending);
                break;
            }
        }
        pending = ItemStack.EMPTY;
        return rest;
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
