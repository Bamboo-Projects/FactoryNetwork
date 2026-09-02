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
 * Which multiblocks exist in the world.
 *
 * <p>A template describes roles, not a machine. It can be built as often as
 * you like — and the instances are told apart by the names of their
 * connectors: {@code ore_plant_1/crusher} belongs to the multiblock
 * {@code ore_plant_1} and plays the role {@code crusher} there.
 *
 * <p>That costs no new block and no assignment screen: whoever names three
 * connectors with the label gun has a multiblock. The devices may be spread
 * all over the building, which is the normal case in large packs.
 *
 * <p>Which template a multiblock belongs to is inferred from its roles. A
 * template name in the label would have been the alternative — it would make
 * every name longer and break every time a template is renamed.
 */
public final class MultiblockInstances {

    /** The separator between multiblock and role. */
    public static final char SEPARATOR = '/';

    /**
     * A multiblock in the world.
     *
     * @param missing   missing roles — if any, it accepts no calls
     * @param ambiguous whether several templates match
     */
    public record Instance(String name, Decl.Multiblock template, Set<String> roles,
                           List<String> missing, boolean ambiguous) {

        public boolean isComplete() {
            return missing.isEmpty() && !ambiguous;
        }

        /** The full name of one of its devices. */
        public String device(String role) {
            return name + SEPARATOR + role;
        }
    }

    private MultiblockInstances() {
    }

    /** The multiblock name within a device name, or {@code null}. */
    public static String instanceOf(String device) {
        int cut = device.indexOf(SEPARATOR);
        return cut <= 0 ? null : device.substring(0, cut);
    }

    /**
     * Gathers all multiblocks.
     *
     * @param devices all known device names of the network
     */
    public static Map<String, Instance> resolve(Program program, Collection<String> devices) {
        List<Decl.Multiblock> templates = program.declarations().stream()
                .filter(Decl.Multiblock.class::isInstance)
                .map(Decl.Multiblock.class::cast)
                .toList();
        if (templates.isEmpty()) {
            return Map.of();
        }

        // First collect, per multiblock name, which roles are present.
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
     * Finds the template for a multiblock.
     *
     * <p>Complete matches first: if exactly one template matches in full, the
     * multiblock belongs to it. If several match, it is ambiguous — guessing
     * either would be worse than asking.
     *
     * <p>If none matches in full, the one with the largest overlap counts as
     * intended and the multiblock as incomplete. That is the case the
     * language provides for: whoever forgot a device should read it in the
     * terminal, not puzzle over why nothing happens.
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
            // A slash in the name alone does not make a multiblock.
            return null;
        }
        List<String> missing = new ArrayList<>(closest.devices());
        missing.removeAll(roles);
        return new Instance(name, closest, roles, List.copyOf(missing), false);
    }
}
