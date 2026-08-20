package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Welche Anlagen in der Welt stehen.
 *
 * <p>Eine Vorlage beschreibt Rollen, keine Maschine. Gebaut wird sie so oft man
 * will — und getrennt werden die Instanzen über den Namen ihrer Connectoren:
 * {@code ore_plant_1/crusher} gehört zur Anlage {@code ore_plant_1} und spielt
 * dort die Rolle {@code crusher}.
 *
 * <p>Das kostet keinen neuen Block und keine Zuordnungsmaske: Wer drei
 * Connectoren mit der Beschriftungspistole benennt, hat eine Anlage. Die
 * Geräte dürfen dabei quer durchs Gebäude verteilt sein, was in großen Packs
 * der Normalfall ist.
 *
 * <p>Zu welcher Vorlage eine Anlage gehört, wird aus ihren Rollen erschlossen.
 * Ein Vorlagenname im Label wäre die Alternative gewesen — er würde jeden
 * Namen länger machen und bei jedem Umbenennen einer Vorlage brechen.
 */
public final class MultiblockInstances {

    /** Der Trenner zwischen Anlage und Rolle. */
    public static final char SEPARATOR = '/';

    /**
     * Eine Anlage in der Welt.
     *
     * @param missing   fehlende Rollen — dann nimmt sie keine Aufrufe an
     * @param ambiguous ob mehrere Vorlagen passen
     */
    public record Instance(String name, Decl.Multiblock template, Set<String> roles,
                           List<String> missing, boolean ambiguous) {

        public boolean isComplete() {
            return missing.isEmpty() && !ambiguous;
        }

        /** Der volle Name eines ihrer Geräte. */
        public String device(String role) {
            return name + SEPARATOR + role;
        }
    }

    private MultiblockInstances() {
    }

    /** Der Name der Anlage in einem Gerätenamen, oder {@code null}. */
    public static String instanceOf(String device) {
        int cut = device.indexOf(SEPARATOR);
        return cut <= 0 ? null : device.substring(0, cut);
    }

    /**
     * Sucht alle Anlagen zusammen.
     *
     * @param devices alle bekannten Gerätenamen des Netzes
     */
    public static Map<String, Instance> resolve(Program program, Collection<String> devices) {
        List<Decl.Multiblock> templates = program.declarations().stream()
                .filter(Decl.Multiblock.class::isInstance)
                .map(Decl.Multiblock.class::cast)
                .toList();
        if (templates.isEmpty()) {
            return Map.of();
        }

        // Erst nach Anlagennamen sammeln, welche Rollen dastehen.
        Map<String, Set<String>> byInstance = new LinkedHashMap<>();
        for (String device : devices) {
            int cut = device.indexOf(SEPARATOR);
            if (cut <= 0 || cut == device.length() - 1) {
                continue;
            }
            byInstance.computeIfAbsent(device.substring(0, cut), key -> new LinkedHashSet<>())
                    .add(device.substring(cut + 1));
        }

        Map<String, Instance> instances = new LinkedHashMap<>();
        byInstance.forEach((name, roles) -> {
            Instance instance = match(name, roles, templates);
            if (instance != null) {
                instances.put(name, instance);
            }
        });
        return instances;
    }

    /**
     * Sucht die Vorlage zu einer Anlage.
     *
     * <p>Vollständige Treffer zuerst: Passt genau eine Vorlage ganz, gehört
     * die Anlage zu ihr. Passen mehrere, ist sie mehrdeutig — beides zu raten
     * wäre schlimmer als zu fragen.
     *
     * <p>Passt keine ganz, gilt die mit der größten Überschneidung als
     * gemeint und die Anlage als unvollständig. Das ist der Fall, den die
     * Sprache vorsieht: Wer ein Gerät vergessen hat, soll es im Terminal
     * lesen und nicht rätseln, warum nichts geschieht.
     */
    private static Instance match(String name, Set<String> roles,
            List<Decl.Multiblock> templates) {
        List<Decl.Multiblock> complete = templates.stream()
                .filter(template -> roles.containsAll(template.devices()))
                .toList();
        if (complete.size() == 1) {
            return new Instance(name, complete.get(0), roles, List.of(), false);
        }
        if (complete.size() > 1) {
            return new Instance(name, complete.get(0), roles, List.of(), true);
        }

        Decl.Multiblock closest = null;
        int best = 0;
        for (Decl.Multiblock template : templates) {
            int shared = (int) template.devices().stream().filter(roles::contains).count();
            if (shared > best) {
                best = shared;
                closest = template;
            }
        }
        if (closest == null) {
            // Ein Schrägstrich im Namen allein macht noch keine Anlage.
            return null;
        }
        List<String> missing = new ArrayList<>(closest.devices());
        missing.removeAll(roles);
        return new Instance(name, closest, roles, List.copyOf(missing), false);
    }
}
