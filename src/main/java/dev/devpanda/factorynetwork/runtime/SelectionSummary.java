package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * Worauf sich eine Auswahl gerade auflöst.
 *
 * <p><b>Die Anzeige, die {@code sprache.md} an zwei Stellen verspricht.</b>
 * Bei {@code except} steht dort „der Editor zeigt zu jedem Muster an, was es
 * gerade trifft", und bei {@code maintain} ist ohne sie nicht abzusehen, was
 * man zugesagt hat: {@code maintain 64 tag:c/ores} hält von <i>jeder</i> Art
 * vierundsechzig, und wie viele das sind, weiß nur das Pack.
 *
 * <p>Ein Muster ist eine Suche, und eine Suche ohne Trefferliste ist eine
 * Zusage ins Blaue.
 *
 * <p>Steht hier und nicht im Editor, weil es die Registry braucht und keinen
 * Bildschirm — so lässt es sich in einer echten Welt prüfen.
 */
public final class SelectionSummary {

    /** Mehr Namen deckten den halben Bildschirm. */
    private static final int MAX_NAMES = 6;

    private SelectionSummary() {
    }

    /**
     * Ein paar Zeilen über das, was diese Auswahl trifft.
     *
     * <p>Die erste nennt die Zahl, die weiteren die Namen. Trifft sie nichts,
     * steht genau das da — die häufigste Ursache ist ein Tag, den dieses Pack
     * nicht kennt, und der sieht im Editor aus wie jeder andere.
     */
    public static List<String> of(Expr.Selector selector) {
        List<String> lines = new ArrayList<>();
        if (selector == null) {
            return lines;
        }
        if (selector.kind() == Expr.Selector.Kind.CHEMICAL) {
            if (!dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()) {
                // Ohne Punkt am Ende: Im Kasten des Editors steht eine
                // Auskunft und kein Satz.
                lines.add("Chemikalien brauchen Mekanism");
                return lines;
            }
            List<String> ids = dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                    .resolve(selector);
            return summarise(lines, ids.stream()
                    .map(dev.devpanda.factorynetwork.compat.mekanism.Chemicals::nameOf)
                    .toList());
        }
        boolean fluids = selector.kind() == Expr.Selector.Kind.FLUID
                || selector.kind() == Expr.Selector.Kind.FLUIDTAG;
        List<String> names = new ArrayList<>();
        if (fluids) {
            for (Fluid fluid : FluidSelection.resolve(selector)) {
                names.add(fluid.getFluidType().getDescription().getString());
            }
        } else {
            for (Item item : ItemSelection.resolve(selector)) {
                names.add(item.getDescription().getString());
            }
        }
        return summarise(lines, names);
    }

    /**
     * Die Zahl und die ersten Namen.
     *
     * <p>An einer Stelle, weil es inzwischen drei Arten von Auswahl gibt und
     * alle dieselbe Auskunft geben sollen — eine Zahl, ein paar Namen, und
     * gezählt statt abgeschnitten.
     */
    private static List<String> summarise(List<String> lines, List<String> names) {
        if (names.isEmpty()) {
            lines.add("trifft nichts");
            return lines;
        }
        lines.add("trifft " + names.size() + (names.size() == 1 ? " Art" : " Arten"));
        for (int i = 0; i < Math.min(names.size(), MAX_NAMES); i++) {
            lines.add(names.get(i));
        }
        if (names.size() > MAX_NAMES) {
            lines.add("… und " + (names.size() - MAX_NAMES) + " weitere");
        }
        return lines;
    }
}
