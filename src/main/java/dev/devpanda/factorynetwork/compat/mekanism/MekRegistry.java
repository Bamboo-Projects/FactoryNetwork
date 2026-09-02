package dev.devpanda.factorynetwork.compat.mekanism;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The only place that touches Mekanism types.
 *
 * <p><b>Why it stands apart:</b> Java resolves the classes of a signature at
 * load time. If {@link Chemicals} carried a {@code Registry<Chemical>}
 * anywhere in a method signature, the class could no longer be loaded in a
 * pack without Mekanism — and with it would fall everything it calls. So this
 * is where what references Mekanism lives, and everything else speaks in
 * {@code String} and {@code long}.
 *
 * <p>This class is entered only when {@link FnMekanism#installed()} is true.
 * Whoever touches it without Mekanism gets a {@code NoClassDefFound} — and
 * rightly so: it would be a bug in the caller, not a condition one should
 * catch.
 */
final class MekRegistry {

    private MekRegistry() {
    }

    /** Whether a chemical with this identifier exists. */
    static boolean has(ResourceLocation id) {
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(id);
    }

    /** All identifiers, as text. */
    static List<ResourceLocation> keys() {
        return new ArrayList<>(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.keySet());
    }

    /** The plain-text name of a chemical. */
    static String name(ResourceLocation id) {
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(id).getTextComponent().getString();
    }
}
