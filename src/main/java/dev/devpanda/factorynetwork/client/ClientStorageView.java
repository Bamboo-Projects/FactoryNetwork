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
 * Der Bestand, wie der Client ihn kennt.
 *
 * <p>Gesucht wird hier, nicht auf dem Server. Deshalb fühlt sich die Suche
 * sofort an — der Preis ist, dass die Anzeige einen Augenblick alt sein kann.
 * Für die Anzeige reicht das; was wirklich zählt, prüft der Server bei jeder
 * Entnahme nach.
 */
public final class ClientStorageView {

    private static final Map<Item, Long> amounts = new LinkedHashMap<>();
    private static int totalTypes;
    private static List<Row> filtered = List.of();
    private static String query = "";
    private static StorageSort sort = StorageSort.AMOUNT;
    private static boolean descending = true;

    /**
     * Ein Eintrag der Anzeige: Gegenstand, Menge und der Text zum Suchen.
     *
     * <p>{@code unit} ist leer für Gegenstände und {@code mB} für
     * Flüssigkeiten. Daran hängt mehr als die Beschriftung: Eine Flüssigkeit
     * lässt sich nicht auf den Mauszeiger nehmen, und ein Klick darauf muss
     * folgenlos bleiben, statt einen Eimer aus dem Nichts zu holen.
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
     * Wechselt die Sortierung.
     *
     * <p>Derselbe Knopf noch einmal dreht die Richtung um, statt weiter zu
     * schalten — so kommt man mit einem Klick zurück, statt einmal im Kreis
     * zu gehen.
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
                amounts.remove(entry.item());
            } else {
                amounts.put(entry.item(), entry.amount());
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
        refilter();
    }

    public static void clear() {
        amounts.clear();
        fluidAmounts.clear();
        totalTypes = 0;
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

    /** Wurde etwas weggelassen, weil der Bestand zu groß ist? */
    public static boolean isTruncated() {
        return totalTypes > amounts.size();
    }

    public static long totalCount() {
        return amounts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static void refilter() {
        List<Row> rows = new ArrayList<>(amounts.size());
        amounts.forEach((item, amount) -> {
            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String mod = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(item).getNamespace();
            // Ein Kürzel wie "@mek" sucht nach der Mod statt nach dem Namen —
            // in einem großen Pack ist das die häufigere Frage.
            boolean matches = query.isEmpty()
                    || (query.startsWith("@") ? mod.contains(query.substring(1))
                                              : name.contains(query));
            if (matches) {
                rows.add(new Row(stack, amount, name, mod, ""));
            }
        });

        // Flüssigkeiten stehen mit im Raster, dargestellt durch ihren Eimer.
        // Ein eigenes Raster daneben wäre ein zweiter Ort zum Suchen.
        fluidAmounts.forEach((fluid, amount) -> {
            ItemStack bucket = new ItemStack(fluid.getBucket());
            if (bucket.isEmpty()) {
                // Ohne Eimer kein Bild — das gibt es bei einigen Mods.
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

    /** Kurzform großer Zahlen: 11842 wird zu 11.8k. */
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
