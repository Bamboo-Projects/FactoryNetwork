package dev.devpanda.factorynetwork.compat.mekanism;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Die einzige Stelle, die Mekanism-Typen anfasst.
 *
 * <p><b>Warum sie getrennt steht:</b> Java löst die Klassen einer Signatur
 * beim Laden auf. Trüge {@link Chemicals} irgendwo ein
 * {@code Registry<Chemical>} in einer Methodensignatur, könnte die Klasse in
 * einem Pack ohne Mekanism nicht mehr geladen werden — und mit ihr fiele
 * alles, was sie ruft. Hier steht deshalb, was Mekanism kennt, und alles
 * andere spricht in {@code String} und {@code long}.
 *
 * <p>Diese Klasse wird nur betreten, wenn {@link FnMekanism#installed()} wahr
 * ist. Wer sie ohne Mekanism anfasst, bekommt einen {@code NoClassDefFound} —
 * und das ist richtig so: Es wäre ein Fehler im Aufrufer, kein Zustand, den
 * man abfangen sollte.
 */
final class MekRegistry {

    private MekRegistry() {
    }

    /** Ob es eine Chemikalie mit dieser Kennung gibt. */
    static boolean has(ResourceLocation id) {
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(id);
    }

    /** Alle Kennungen, als Text. */
    static List<ResourceLocation> keys() {
        return new ArrayList<>(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.keySet());
    }

    /** Der Klartextname einer Chemikalie. */
    static String name(ResourceLocation id) {
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(id).getTextComponent().getString();
    }
}
