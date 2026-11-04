package dev.devpanda.factorynetwork.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * A {@code recipe} declaration written from what a recipe viewer shows —
 * so nobody types the ids of a foreign machine's recipe by hand.
 *
 * <p>The machine is left as {@code maschine}: which connector runs it only
 * the player knows, and the terminal says so on deploy until it is named.
 */
public final class RecipeText {

    /** One side of a recipe: an amount and a selection as the language writes it. */
    public record Part(long amount, String selection) {
    }

    private RecipeText() {
    }

    public static String of(List<Part> inputs, List<Part> outputs) {
        String name = outputs.isEmpty() ? "rezept" : nameOf(outputs.get(0).selection());
        StringBuilder out = new StringBuilder("recipe ").append(name).append(" at maschine {\n");
        for (Part part : inputs) {
            out.append("    in ").append(part.amount()).append(' ').append(part.selection()).append('\n');
        }
        for (Part part : outputs) {
            out.append("    out ").append(part.amount()).append(' ').append(part.selection()).append('\n');
        }
        return out.append("}\n").toString();
    }

    /** {@code item:mekanism/dust_iron} -> {@code dust_iron}. */
    private static String nameOf(String selection) {
        int cut = Math.max(selection.lastIndexOf('/'), selection.lastIndexOf(':'));
        String base = cut < 0 ? selection : selection.substring(cut + 1);
        String name = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return name.isEmpty() || Character.isDigit(name.charAt(0)) ? "rezept_" + name : name;
    }

    /** How the language writes an item id: the minecraft namespace short. */
    public static String item(Identifier id) {
        return "item:" + shortId(id);
    }

    public static String fluid(Identifier id) {
        return "fluid:" + shortId(id);
    }

    public static String tag(Identifier id) {
        return "tag:" + id.getNamespace() + "/" + id.getPath();
    }

    public static String item(net.minecraft.world.item.Item item) {
        return item(BuiltInRegistries.ITEM.getKey(item));
    }

    private static String shortId(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.getNamespace() + "/" + id.getPath();
    }
}
