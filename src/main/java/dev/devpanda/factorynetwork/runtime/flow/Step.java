package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.runtime.Value;

/**
 * Was nach einer Anweisung geschehen soll.
 *
 * <p>Der Kniff, der zwei Ausführungsarten aus einer Logik erlaubt: Was eine
 * Anweisung <b>tut</b>, steht an einer Stelle; wie es <b>weitergeht</b>,
 * entscheidet der Aufrufer. Der gewöhnliche Interpreter ruft sich selbst
 * rekursiv, der Ablauf legt einen Rahmen auf seinen Stapel — und beide
 * bewegen Gegenstände mit demselben Code.
 *
 * <p>Ohne diese Trennung gäbe es jede Anweisung zweimal, und eine der beiden
 * Fassungen liefe irgendwann auseinander.
 */
public sealed interface Step {

    /** Weiter zur nächsten Anweisung. */
    record Next() implements Step {
        private static final Next INSTANCE = new Next();

        public static Next get() {
            return INSTANCE;
        }
    }

    /** In einen Block hineingehen — Schleifenrumpf, Bedingungszweig. */
    record Enter(Block block, boolean loop) implements Step {}

    /**
     * Über eine Liste laufen.
     *
     * <p>Eigener Schritt und nicht bloß ein {@link Enter}, weil die Liste
     * einmal ausgewertet wird und dann Runde für Runde abgearbeitet werden
     * muss. Bei {@code while} steht die Bedingung im Programm und wird jedes
     * Mal neu geprüft; hier gäbe es nichts, woran sich der Stand ablesen ließe
     * — also wandert er in den Rahmen und damit auf die Platte.
     */
    record ForEach(Block body, String variable, java.util.List<Value> values)
            implements Step {}

    /** Aus der Funktion heraus. */
    record Return(Value value) implements Step {}

    /**
     * Eine eigene Funktion aufrufen.
     *
     * <p>Ohne diesen Schritt liefe die gerufene Funktion im gewöhnlichen
     * Interpreter zu Ende — und könnte dort nicht warten. Ein Ablauf, der eine
     * Funktion mit {@code await} ruft, bekommt so einen zweiten Rahmen auf
     * denselben Stapel; beide werden gemeinsam aufgeschrieben.
     *
     * <p>{@code resultName} ist der Name, unter dem der Rückgabewert im
     * rufenden Rahmen landet, oder {@code null} bei einem Aufruf ohne
     * Zuweisung.
     */
    record Invoke(java.util.List<String> parameters, java.util.List<Value> arguments,
                  Block body, String resultName, String devicePrefix) implements Step {}

    /** Aus der Schleife heraus. */
    record Break() implements Step {}

    /** Zur nächsten Runde der Schleife. */
    record Continue() implements Step {}

    /**
     * Warten, bis die Spielzeit erreicht ist.
     *
     * <p>Die Zeit ist absolut, nicht relativ. Solange der Server steht,
     * vergeht keine Spielzeit — ein Wartezeitraum von dreißig Sekunden läuft
     * also nicht ab, während niemand spielt. Das ist die richtige Bedeutung
     * für Minecraft, sieht aber für den, der an eine Uhr denkt, nach einem
     * Fehler aus.
     */
    record Sleep(long untilGameTime) implements Step {}

    /**
     * Auf ein Ereignis warten.
     *
     * <p>{@code deadline} ist wieder absolute Spielzeit, oder negativ, wenn
     * keine Frist gesetzt wurde.
     */
    record Await(String event, Expr where, long deadline, Block elseBody,
                 String resultName) implements Step {}
}
