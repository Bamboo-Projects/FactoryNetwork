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

    /** Ein Eintrag der Anzeige: Gegenstand, Menge und der Text zum Suchen. */
    public record Row(ItemStack stack, long amount, String searchText) {
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
        totalTypes = packet.totalTypes();
        refilter();
    }

    public static void clear() {
        amounts.clear();
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
            if (query.isEmpty() || name.contains(query)) {
                rows.add(new Row(stack, amount, name));
            }
        });
        // Viel zuerst — beim Suchen will man den Hauptbestand oben sehen.
        rows.sort(Comparator.comparingLong(Row::amount).reversed());
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
