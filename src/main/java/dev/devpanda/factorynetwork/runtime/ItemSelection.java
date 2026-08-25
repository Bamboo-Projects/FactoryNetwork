package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Löst einen Auswahlausdruck zu Gegenstandsarten auf.
 *
 * <p>Hier landet der Fall, für den die Auswahlregeln überhaupt entworfen
 * wurden. AllTheOres erzeugt aus 31 Materialsätzen mehrere hundert Einträge,
 * und ihre Namen sind nicht einheitlich gebaut: Die Form steht hinten
 * ({@code aluminum_ingot}), Steinart und Zustand vorne
 * ({@code deepslate_aluminum_ore}, {@code raw_aluminum}), Zwischenprodukte
 * tragen beides ({@code dirty_aluminum_dust}). Deshalb darf der Platzhalter an
 * jeder Stelle stehen.
 *
 * <p><b>Aufgelöst wird einmal und dann gemerkt.</b> Ein Muster über
 * zwanzigtausend Einträge darf den Server nicht in jedem Tick beschäftigen —
 * das ist dieselbe Rechnung wie beim Speicher.
 */
public final class ItemSelection {

    private static final Map<String, List<Item>> CACHE = new HashMap<>();

    /** Der Zwischenspeicher gilt nur, solange die Registry unverändert ist. */
    public static void invalidate() {
        CACHE.clear();
    }

    public static List<Item> resolve(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> cached(selector);
            case Expr.Except except -> {
                Set<Item> items = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(items::remove);
                }
                yield List.copyOf(items);
            }
            case Expr.Amount amount -> resolve(amount.selection());
            default -> List.of();
        };
    }

    private static List<Item> cached(Expr.Selector selector) {
        String key = key(selector);
        List<Item> known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        List<Item> resolved = compute(selector);
        CACHE.put(key, resolved);
        return resolved;
    }

    private static String key(Expr.Selector selector) {
        return selector.kind() + "|" + selector.namespace() + "|" + selector.path();
    }

    private static List<Item> compute(Expr.Selector selector) {
        if (selector.kind() == Expr.Selector.Kind.TAG) {
            return fromTag(selector);
        }
        // „all" ist jeder Gegenstand — dasselbe wie item:*, und über
        // denselben Zwischenspeicher.
        //
        // <p><b>Nur hier, wo aufgelöst werden muss.</b> Ein schlichtes
        // move all fragt gar nicht erst: Dort heißt eine leere Liste „kein
        // Filter", und die Kiste wird abgeräumt, ohne dass jemand die
        // Registry durchgeht. Gebraucht wird diese Auflösung erst bei
        // all except …, und da kostet sie so viel wie item:* except …,
        // das die Sprache ohnehin erlaubt.
        if (selector.kind() == Expr.Selector.Kind.ALL) {
            return List.copyOf(BuiltInRegistries.ITEM.stream().toList());
        }
        if (selector.kind() != Expr.Selector.Kind.ITEM) {
            // Flüssigkeiten und Chemikalien sind geschrieben, aber noch nicht
            // angebunden. Sie hier still als leer zu behandeln wäre schlimmer
            // als der Hinweis in der Laufzeit.
            return List.of();
        }
        if (!selector.hasPattern()) {
            return single(selector);
        }
        return byPattern(selector);
    }

    private static List<Item> single(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return List.of();
        }
        return List.of(BuiltInRegistries.ITEM.get(id));
    }

    /**
     * Muster über Namen.
     *
     * <p><b>Ohne Namensraum wird über alle gesucht</b>, anders als bei einem
     * literalen Namen. Ein Muster ist eine Suche, und eine Suche, die
     * stillschweigend bei Vanilla haltmacht, findet in einem großen Pack fast
     * nichts. Steht ein Namensraum da, gilt nur dieser.
     */
    private static List<Item> byPattern(Expr.Selector selector) {
        Pattern pattern = toPattern(selector.path());
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;

        List<Item> found = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (pattern.matcher(id.getPath()).matches()) {
                found.add(BuiltInRegistries.ITEM.get(id));
            }
        }
        return List.copyOf(found);
    }

    /** Übersetzt ein Muster in einen regulären Ausdruck; nur {@code *} zählt. */
    private static Pattern toPattern(String path) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static List<Item> fromTag(Expr.Selector selector) {
        String namespace = selector.hasNamespace() ? selector.namespace() : "minecraft";
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace.toLowerCase(Locale.ROOT), selector.path());
        if (id == null) {
            return List.of();
        }
        TagKey<Item> tag = ItemTags.create(id);
        List<Item> found = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders ->
                holders.forEach(holder -> found.add(holder.value())));
        return List.copyOf(found);
    }

    private ItemSelection() {
    }
}
