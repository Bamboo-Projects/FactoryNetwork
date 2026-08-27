package dev.devpanda.factorynetwork.upgrade;

import java.util.ArrayList;
import java.util.List;

/**
 * Was eine Bestückung kann, und wie hoch ihre Werte sind.
 *
 * <p>Zwei Fragen, mehr nicht: <i>Habe ich diese Fähigkeit?</i> und <i>wie
 * hoch ist dieser Wert?</i> Alles, was Steckplätze hat, stellt sie — der
 * Sendemast, die Geräte, die Anzeigetafel.
 */
public record Loadout(List<Upgrade> installed) {

    public Loadout {
        installed = List.copyOf(installed);
    }

    public static Loadout of(List<? extends Upgrade> installed) {
        // Der Umweg über ArrayList ist nötig: List.copyOf einer Liste von
        // Untertypen bleibt eine Liste von Untertypen, und der Record will
        // eine von Upgrade.
        return new Loadout(new ArrayList<Upgrade>(installed));
    }

    /** Steckt ein Modul dieser Art darin? */
    public boolean has(Ability ability) {
        return installed.contains(ability);
    }

    /**
     * Die Summe aller Karten auf diesen Wert.
     *
     * <p>Ohne die Grenzenlos-Karte: Deren Schritt ist null, und wer sie
     * steckt, fragt {@link #unlimited} statt dieser Zahl.
     */
    public int value(Stat stat) {
        int sum = 0;
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat) {
                sum += card.step();
            }
        }
        return sum;
    }

    /** Hebt eine der Karten die Grenze dieses Werts auf? */
    public boolean unlimited(Stat stat) {
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat
                    && card.unlimited()) {
                return true;
            }
        }
        return false;
    }
}
