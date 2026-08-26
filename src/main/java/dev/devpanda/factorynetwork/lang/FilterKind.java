package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Wovon eine Filter-Vorlage handelt.
 *
 * <p><b>Gegenstände oder Flüssigkeiten, nie beides.</b> {@code move} schickt
 * Wasser und Steine über verschiedene Wege; eine Vorlage, die beides
 * enthielte, wäre an jeder Verwendungsstelle etwas anderes. Gemischt ist
 * deshalb ein Fehler und keine stillschweigende Auswahl — gemeldet wird er
 * in {@link FilterCheck}, hier steht nur der Befund.
 *
 * <p>Reine Baumbetrachtung, ohne Registry: <b>welche</b> Gegenstände
 * {@code tag:c/ores} trifft, weiß erst die Welt — dass es Gegenstände sind,
 * steht schon im Programm. Deshalb liegt das hier und nicht in
 * {@code runtime}.
 */
public enum FilterKind {

    /** Gegenstände. Ein {@code tag:} zählt dazu. */
    ITEM,

    /** Flüssigkeiten. */
    FLUID,

    /** Beides in einer Vorlage — ein Fehler. */
    MIXED,

    /** Keine Zeile, die etwas einschließt. */
    EMPTY;

    /**
     * Die Sorte einer Vorlage.
     *
     * <p>Ohne einschließende Zeile ist sie {@link #EMPTY}, ganz gleich, was
     * in den Ausnahmen steht: Es gibt nichts, wovon abgezogen würde.
     * Andernfalls entscheiden <b>alle</b> Zeilen mit, auch die Ausnahmen —
     * sonst hinge die Sorte davon ab, in welcher Zeile etwas steht, und eine
     * Ausnahme, die gar nicht zur Vorlage passt, fiele niemandem auf.
     */
    public static FilterKind of(Decl.FilterTemplate template) {
        if (template.includes().isEmpty()) {
            return EMPTY;
        }
        boolean items = false;
        boolean fluids = false;
        for (Expr entry : entries(template)) {
            for (Expr.Selector selector : selectorsOf(entry)) {
                switch (selector.kind()) {
                    case ITEM, TAG -> items = true;
                    case FLUID, FLUIDTAG -> fluids = true;
                    // Chemikalien, Strom und fremde Arten sagen nichts über
                    // Gegenstand oder Flüssigkeit. Dass sie hier nichts zu
                    // suchen haben, meldet FilterCheck.
                    case CHEMICAL, POWER, CUSTOM -> { }
                }
            }
        }
        if (items && fluids) {
            return MIXED;
        }
        if (fluids) {
            return FLUID;
        }
        return ITEM;
    }

    /** Alle Zeilen einer Vorlage, Ausnahmen mitgezählt. */
    public static List<Expr> entries(Decl.FilterTemplate template) {
        List<Expr> all = new ArrayList<>(template.includes());
        all.addAll(template.excludes());
        return all;
    }

    /**
     * Die Selektoren in einer Zeile — durch {@code except} und Mengen
     * hindurch.
     *
     * <p>Was hier nicht durchkommt, ist keine Auswahl: ein Name (also der
     * Versuch, eine Vorlage in eine andere zu legen), eine Rechnung, ein
     * Text. {@link FilterCheck} erkennt das daran, dass eine Zeile keinen
     * einzigen Selektor hergibt.
     */
    public static List<Expr.Selector> selectorsOf(Expr entry) {
        List<Expr.Selector> found = new ArrayList<>();
        collect(entry, found);
        return found;
    }

    private static void collect(Expr entry, List<Expr.Selector> found) {
        switch (entry) {
            case Expr.Selector selector -> found.add(selector);
            case Expr.Amount amount -> collect(amount.selection(), found);
            case Expr.Except except -> {
                collect(except.base(), found);
                except.exclusions().forEach(exclusion -> collect(exclusion, found));
            }
            case null, default -> { }
        }
    }
}
