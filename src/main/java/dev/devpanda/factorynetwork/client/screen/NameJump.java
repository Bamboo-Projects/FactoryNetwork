package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.lang.Definitions;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.BlockPos;

/**
 * Wohin ein Strg+Klick auf einen Namen führt.
 *
 * <p>Zwei Ziele, und welches gilt, entscheidet der Name: Steht er im
 * Programm, ist die Frage „wo wird das erklärt" — steht er in der Welt, ist
 * sie „welcher Block ist das", und die beantwortet eine Marke.
 *
 * <p><b>An einer Stelle, weil beide Fenster sie brauchen.</b> Sie stand
 * zuerst nur im eigenen Fenster; im Reiter des Terminals führte derselbe
 * Griff ins Leere. Aufgefallen ist das erst, als das Zeigen ihn dort
 * ankündigte — ein Hinweistext, der auf etwas verweist, das es nicht gibt,
 * ist schlimmer als kein Hinweis.
 */
public final class NameJump {

    /**
     * Ein Ziel.
     *
     * <p>Genau eines der beiden Felder ist gesetzt.
     *
     * @param inCode  wo der Name erklärt wird, oder {@code null}
     * @param inWorld wo der Block steht, oder {@code null}
     */
    public record Jump(Definitions.Location inCode, BlockPos inWorld) {
    }

    private NameJump() {
    }

    /**
     * Wohin dieser Name führt, oder {@code null}.
     *
     * <p>Die Erklärung im Programm hat Vorrang: Wer einen Namen im Code
     * sucht, meint meistens seine Erklärung — die Stelle in der Welt steht
     * ohnehin schon im Tooltip.
     */
    public static Jump resolve(String word, Project project) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        var declared = Definitions.find(project, word);
        if (declared.isPresent()) {
            return new Jump(declared.get(), null);
        }
        BlockPos place = ClientNetworkState.placeOf(word);
        return place == null ? null : new Jump(null, place);
    }
}
