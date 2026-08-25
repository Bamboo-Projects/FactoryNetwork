package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was sich mit einer Liste anfangen lässt.
 *
 * <p>{@code sprache.md} §12 nennt fünf: {@code where}, {@code sort},
 * {@code first}, {@code count}, {@code sum}. Die letzten drei brauchen kein
 * {@code it} und stehen hier; {@code where} und {@code sort} werten ihren
 * Ausdruck je Element aus und sind ein eigener Schritt.
 */
class ListMemberTest {

    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();
        final List<Value> contents = new ArrayList<>();

        @Override
        public long move(Value amount, Value from, Value to) {
            return 0;
        }

        @Override
        public long count(Value what) {
            return 0;
        }

        @Override
        public int redstone(String device) {
            return 0;
        }

        @Override
        public void setRedstone(String device, int strength) {
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public boolean hasDevice(String name) {
            return true;
        }

        @Override
        public String suggestDevice(String name) {
            return null;
        }

        @Override
        public List<Value> itemsIn(String device) {
            return contents;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("count zählt die Einträge")
    void countCountsTheEntries() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(3));
        host.contents.add(new Value.Int(4));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2"), host.logs);
    }

    @Test
    @DisplayName("first gibt das erste Element")
    void firstGivesTheFirstEntry() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Text("vorne"));
        host.contents.add(new Value.Text("hinten"));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("vorne"), host.logs);
    }

    @Test
    @DisplayName("first auf einer leeren Liste gibt nichts — und wirft nicht")
    void firstOnAnEmptyListGivesNothing() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("nichts"), host.logs,
                "eine leere Maschine ist kein Fehler");
    }

    @Test
    @DisplayName("sum addiert Zahlen auf")
    void sumAddsNumbers() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(3));
        host.contents.add(new Value.Int(4));
        host.contents.add(new Value.Int(5));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sum())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("12"), host.logs);
    }

    @Test
    @DisplayName("where sortiert aus, mit it als Eintrag")
    void whereFiltersWithIt() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(3));
        host.contents.add(new Value.Int(30));
        host.contents.add(new Value.Int(7));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().where(it > 5).count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2"), host.logs);
    }

    @Test
    @DisplayName("sort ordnet nach dem Ausdruck")
    void sortOrdersByTheExpression() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(30));
        host.contents.add(new Value.Int(3));
        host.contents.add(new Value.Int(7));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sort(it).first())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("3"), host.logs);
    }

    @Test
    @DisplayName("it lebt nur im Aufruf und verschwindet danach")
    void itLivesOnlyInsideTheCall() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(1));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().where(it > 0).count())
                    log(crusher_1.items().where(it > 5).count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("1", "0"), host.logs,
                "der zweite Aufruf sieht sein eigenes it, nicht das des ersten");
    }

    @Test
    @DisplayName("Ein Name von außen bleibt im Ausdruck erreichbar")
    void anOuterNameStaysReachableInside() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Int(3));
        host.contents.add(new Value.Int(30));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    let grenze = 5
                    log(crusher_1.items().where(it > grenze).count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("1"), host.logs,
                "it legt sich darüber, es schneidet nicht ab");
    }

    @Test
    @DisplayName("sum über eine leere Liste ist null")
    void sumOfNothingIsZero() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sum())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("0"), host.logs);
    }

    @Test
    @DisplayName("An einem Eintrag steht seine Menge")
    void anEntryCarriesItsAmount() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Selection(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first().amount)
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("5"), host.logs);
    }

    @Test
    @DisplayName("sum zählt die Mengen zusammen")
    void sumAddsTheAmounts() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Selection(List.of(), 5));
        host.contents.add(new Value.Selection(List.of(), 7));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sum())
                }""", host);
        interpreter.call("zeigen", List.of());

        // Vorher warf sum an dieser Stelle: Ein Posten war keine Zahl, und
        // eine Bestandsliste ist der Fall, für den sum überhaupt da ist.
        assertEquals(List.of("12"), host.logs);
    }

    @Test
    @DisplayName("where filtert nach der Menge")
    void whereFiltersByAmount() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Selection(List.of(), 5));
        host.contents.add(new Value.Selection(List.of(), 70));
        host.contents.add(new Value.Selection(List.of(), 90));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().where(it.amount > 64).count())
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2"), host.logs);
    }

    @Test
    @DisplayName("sort ordnet nach der Menge")
    void sortOrdersByAmount() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Selection(List.of(), 90));
        host.contents.add(new Value.Selection(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sort(it.amount).first().amount)
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("5"), host.logs, "der kleinste Posten steht vorn");
    }

    @Test
    @DisplayName("Eine Auswahl über mehrere Arten hat keine eine Art")
    void aSelectionOverSeveralKindsHasNoSingleItem() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Selection(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first().item)
                }""", host);

        // Ohne Art ist it.item keine Auskunft, die sich erfinden lässt.
        ScriptError error = org.junit.jupiter.api.Assertions.assertThrows(
                ScriptError.class, () -> interpreter.call("zeigen", List.of()));
        assertTrue(error.getMessage().contains("Art"), error.getMessage());
    }
}
