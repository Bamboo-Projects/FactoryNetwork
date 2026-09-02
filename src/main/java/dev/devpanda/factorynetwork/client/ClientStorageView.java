package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The stock as the client knows it.
 *
 * <p>Searching happens here, not on the server. That is why the search feels
 * instant — the price is that the display can be a moment out of date. For the
 * display that is enough; what really counts the server verifies on every
 * withdrawal.
 */
public final class ClientStorageView {

    private static final Map<dev.devpanda.factorynetwork.storage.ItemKey, Long> amounts =
            new LinkedHashMap<>();
    private static int totalTypes;
    private static int freeTypes;
    private static int freeFluidTypes;
    private static List<Row> filtered = List.of();
    private static String query = "";
    private static StorageSort sort = StorageSort.AMOUNT;
    private static boolean descending = true;

    /**
     * A row of the display: item, amount and the text to search on.
     *
     * <p>{@code unit} is empty for items and {@code mB} for fluids. More hangs
     * on it than the label: a fluid cannot be picked up onto the mouse cursor,
     * and a click on it must stay without consequence, instead of conjuring a
     * bucket out of nothing.
     */
    public record Row(ItemStack stack, long amount, String searchText, String modId,
                      String unit) {

        public boolean isFluid() {
            return !unit.isEmpty();
        }
    }

    private static final Map<net.minecraft.world.level.material.Fluid, Long> fluidAmounts =
            new LinkedHashMap<>();

    public static StorageSort sort() {
        return sort;
    }

    public static boolean isDescending() {
        return descending;
    }

    /**
     * Switches the sorting.
     *
     * <p>The same button again reverses the direction instead of advancing
     * further — this way one click gets you back, instead of going once around
     * the circle.
     */
    public static void setSort(StorageSort next) {
        if (sort == next) {
            descending = !descending;
        } else {
            sort = next;
            descending = next != StorageSort.NAME;
        }
        refilter();
    }

    public static void accept(StorageSnapshotPacket packet) {
        if (packet.replace()) {
            amounts.clear();
        }
        for (StorageSnapshotPacket.Entry entry : packet.entries()) {
            if (entry.amount() <= 0) {
                amounts.remove(entry.key());
            } else {
                amounts.put(entry.key(), entry.amount());
            }
        }
        if (packet.replace()) {
            fluidAmounts.clear();
        }
        for (StorageSnapshotPacket.FluidEntry entry : packet.fluids()) {
            if (entry.amount() <= 0) {
                fluidAmounts.remove(entry.fluid());
            } else {
                fluidAmounts.put(entry.fluid(), entry.amount());
            }
        }
        totalTypes = packet.totalTypes();
        freeTypes = packet.freeTypes();
        freeFluidTypes = packet.freeFluidTypes();
        refilter();
    }

    public static void clear() {
        amounts.clear();
        fluidAmounts.clear();
        totalTypes = 0;
        freeTypes = 0;
        freeFluidTypes = 0;
        query = "";
        filtered = List.of();
    }

    public static void setQuery(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        refilter();
    }

    public static String query() {
        return query;
    }

    public static List<Row> rows() {
        return filtered;
    }

    public static int knownTypes() {
        return amounts.size();
    }

    public static int totalTypes() {
        return totalTypes;
    }

    /** How many type slots in the cells are still free. */
    public static int freeTypes() {
        return freeTypes;
    }

    /** The same for the fluid cells. */
    public static int freeFluidTypes() {
        return freeFluidTypes;
    }

    /** Was something left out because the stock is too large? */
    public static boolean isTruncated() {
        return totalTypes > amounts.size();
    }

    public static long totalCount() {
        return amounts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static void refilter() {
        List<Row> rows = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> {
            // The real item and not a freshly built one: a named tool should
            // carry its name in the terminal, and one should also be able to
            // search on it.
            ItemStack stack = key.toStack(1);
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String mod = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(key.item()).getNamespace();
            // A shorthand like "@mek" searches by the mod instead of by the
            // name — in a large pack that is the more common question.
            boolean matches = query.isEmpty()
                    || (query.startsWith("@") ? mod.contains(query.substring(1))
                                              : name.contains(query));
            if (matches) {
                rows.add(new Row(stack, amount, name, mod, ""));
            }
        });

        // Fluids stand in the grid too, represented by their bucket. A grid of
        // their own beside it would be a second place to search.
        fluidAmounts.forEach((fluid, amount) -> {
            ItemStack bucket = new ItemStack(fluid.getBucket());
            if (bucket.isEmpty()) {
                // No bucket, no picture — this happens with some mods.
                bucket = new ItemStack(net.minecraft.world.item.Items.BUCKET);
            }
            String name = bucket.getHoverName().getString().toLowerCase(Locale.ROOT);
            String mod = net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(fluid).getNamespace();
            boolean matches = query.isEmpty()
                    || (query.startsWith("@") ? mod.contains(query.substring(1))
                                              : name.contains(query));
            if (matches) {
                rows.add(new Row(bucket, amount, name, mod, "mB"));
            }
        });

        Comparator<Row> order = switch (sort) {
            case AMOUNT -> Comparator.comparingLong(Row::amount);
            case NAME -> Comparator.comparing(Row::searchText);
            case MOD -> Comparator.comparing(Row::modId).thenComparing(Row::searchText);
        };
        rows.sort(descending ? order.reversed() : order);
        filtered = List.copyOf(rows);
    }

    /** Short form of large numbers: 11842 becomes 11.8k. */
    public static String shortAmount(long amount) {
        if (amount < 1000) {
            return String.valueOf(amount);
        }
        if (amount < 1_000_000) {
            return String.format(Locale.GERMAN, "%.1fk", amount / 1000.0);
        }
        return String.format(Locale.GERMAN, "%.1fM", amount / 1_000_000.0);
    }

    private ClientStorageView() {
    }
}
