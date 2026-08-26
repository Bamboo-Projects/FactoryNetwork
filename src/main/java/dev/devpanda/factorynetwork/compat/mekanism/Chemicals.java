package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Löst Chemikalien-Auswahlen auf — wenn Mekanism da ist.
 *
 * <p>Dieselben Regeln wie bei Gegenständen und Flüssigkeiten: Ohne
 * Namensraum ist {@code mekanism} gemeint, ein Muster sucht über alle
 * Namensräume. Der Namensraum ist hier <b>nicht</b> {@code minecraft} —
 * Chemikalien gibt es in Minecraft nicht, und wer {@code chemical:hydrogen}
 * schreibt, meint das von Mekanism.
 *
 * <p><b>Die Kennungen sind Texte und keine Chemikalien.</b> Das ist die
 * Naht, an der diese Anbindung hängt: Alles außerhalb von
 * {@code compat/mekanism} spricht über {@code "mekanism:hydrogen"} und nie
 * über einen Mekanism-Typ. Sonst lüde die Klasse, die eine solche Signatur
 * trägt, beim Initialisieren fremde Klassen — und die Mod startete ohne
 * Mekanism nicht mehr.
 *
 * <p>Ohne Mekanism ist jede Antwort leer. Das ist kein Fehler, sondern die
 * Wahrheit über ein Pack ohne die Mod; die Meldung dazu steht in
 * {@link FnMekanism}.
 *
 * <p><b>Kein Mekanism-Typ in einer Signatur dieser Klasse.</b> Java löst die
 * Klassen einer Signatur beim Laden auf; stünde hier eines, ließe sich diese
 * Klasse ohne Mekanism nicht mehr laden. Was Mekanism kennt, steht in
 * {@link MekRegistry} und wird nur betreten, wenn die Mod da ist.
 */
public final class Chemicals {

    /** Der Namensraum, den {@code chemical:} ohne Angabe meint. */
    public static final String DEFAULT_NAMESPACE = "mekanism";

    private Chemicals() {
    }

    /**
     * Die Kennungen hinter einer Auswahl, als Text.
     *
     * <p>Leer, wenn Mekanism fehlt oder die Auswahl nichts trifft. Der
     * Unterschied ist für den Aufrufer keiner — beides heißt „hier ist
     * nichts" —, und für die Meldung fragt er {@link FnMekanism}.
     */
    public static List<String> resolve(Expr expr) {
        if (!FnMekanism.installed()) {
            return List.of();
        }
        return switch (expr) {
            case Expr.Selector selector -> ofSelector(selector);
            case Expr.Except except -> {
                Set<String> ids = new LinkedHashSet<>(resolve(except.base()));
                for (Expr exclusion : except.exclusions()) {
                    resolve(exclusion).forEach(ids::remove);
                }
                yield List.copyOf(ids);
            }
            case Expr.Amount amount -> resolve(amount.selection());
            case null, default -> List.of();
        };
    }

    private static List<String> ofSelector(Expr.Selector selector) {
        if (selector.kind() != Expr.Selector.Kind.CHEMICAL) {
            return List.of();
        }
        return selector.hasPattern() ? byPattern(selector) : single(selector);
    }

    private static List<String> single(Expr.Selector selector) {
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : DEFAULT_NAMESPACE;
        ResourceLocation id = ResourceLocation.tryBuild(namespace, selector.path());
        if (id == null) {
            return List.of();
        }
        return MekRegistry.has(id) ? List.of(id.toString()) : List.of();
    }

    private static List<String> byPattern(Expr.Selector selector) {
        Pattern pattern = toPattern(selector.path());
        String namespace = selector.hasNamespace()
                ? selector.namespace().toLowerCase(Locale.ROOT) : null;
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : MekRegistry.keys()) {
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (pattern.matcher(id.getPath()).matches()) {
                found.add(id.toString());
            }
        }
        return List.copyOf(found);
    }

    /**
     * Ein Muster als regulärer Ausdruck.
     *
     * <p>Nur {@code *} ist ein Platzhalter; alles andere wird wörtlich
     * genommen. Dieselbe Regel wie bei Gegenständen — ein Punkt in einer
     * Kennung ist ein Punkt und kein „irgendein Zeichen".
     */
    private static Pattern toPattern(String path) {
        StringBuilder out = new StringBuilder();
        for (String piece : path.split("\\*", -1)) {
            if (out.length() > 0) {
                out.append(".*");
            }
            out.append(Pattern.quote(piece));
        }
        return Pattern.compile(out.toString());
    }

    /** Wie eine Chemikalie im Klartext heißt, oder ihre Kennung. */
    public static String nameOf(String id) {
        if (!FnMekanism.installed()) {
            return id;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !MekRegistry.has(key)) {
            return id;
        }
        return MekRegistry.name(key);
    }

    /** Ob es diese Chemikalie gibt. */
    public static boolean known(String id) {
        if (!FnMekanism.installed()) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && MekRegistry.has(key);
    }
}
