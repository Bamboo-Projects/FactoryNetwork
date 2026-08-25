package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Was hinter dem Namen einer Filter-Vorlage steht.
 *
 * <p><b>Erst alles zusammen, dann die Ausnahmen weg.</b> Nicht zeilenweise
 * abwechselnd: So ist die Reihenfolge der Zeilen gleichgültig, und wer die
 * Vorlage liest, muss keinen Zwischenstand mitführen.
 *
 * <p>Aufgelöst wird ausschließlich über {@link ItemSelection} und
 * {@link FluidSelection} — dieselbe Stelle wie für jede geschriebene Auswahl,
 * mitsamt ihrem Zwischenspeicher. Eine zweite Fassung daneben liefe
 * auseinander.
 */
public final class FilterTemplates {

    private FilterTemplates() {
    }

    /** Die Vorlagen eines Programms, nach Namen. */
    public static Map<String, Decl.FilterTemplate> of(Program program) {
        Map<String, Decl.FilterTemplate> found = new LinkedHashMap<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.FilterTemplate template) {
                found.put(template.name(), template);
            }
        }
        return found;
    }

    /** Die Gegenstandsarten einer Vorlage. */
    public static List<Item> items(Decl.FilterTemplate template) {
        Set<Item> found = new LinkedHashSet<>();
        for (Expr entry : template.includes()) {
            found.addAll(ItemSelection.resolve(entry));
        }
        for (Expr entry : template.excludes()) {
            ItemSelection.resolve(entry).forEach(found::remove);
        }
        if (found.isEmpty()) {
            throw empty(template);
        }
        return List.copyOf(found);
    }

    /** Die Flüssigkeitssorten einer Vorlage. */
    public static List<Fluid> fluids(Decl.FilterTemplate template) {
        Set<Fluid> found = new LinkedHashSet<>();
        for (Expr entry : template.includes()) {
            found.addAll(FluidSelection.resolve(entry));
        }
        for (Expr entry : template.excludes()) {
            FluidSelection.resolve(entry).forEach(found::remove);
        }
        if (found.isEmpty()) {
            throw empty(template);
        }
        return List.copyOf(found);
    }

    /**
     * Die Meldung nennt die Vorlage beim Namen.
     *
     * <p>Ohne den stünde da „die Auswahl trifft nichts" — und der Spieler
     * sucht sie an der Stelle, an der er den Namen geschrieben hat, statt in
     * der Vorlage.
     */
    private static ScriptError empty(Decl.FilterTemplate template) {
        return new ScriptError("Die Vorlage " + template.name() + " trifft nichts.",
                "Gibt es die Gegenstände in diesem Pack? Und nimmt eine except-Zeile "
                        + "vielleicht alles wieder heraus?");
    }
}
