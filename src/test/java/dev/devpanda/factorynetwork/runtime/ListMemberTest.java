package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can be done with a list.
 *
 * <p>{@code sprache.md} §12 names five: {@code where}, {@code sort},
 * {@code first}, {@code count}, {@code sum}. The last three need no
 * {@code it} and live here; {@code where} and {@code sort} evaluate their
 * expression per element and are a step of their own.
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
    @DisplayName("count counts the entries")
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
    @DisplayName("first gives the first element")
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
    @DisplayName("first on an empty list gives nothing — and does not throw")
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
    @DisplayName("sum adds numbers up")
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
    @DisplayName("where filters out, with it as the entry")
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
    @DisplayName("sort orders by the expression")
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
    @DisplayName("it lives only in the call and disappears afterwards")
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
    @DisplayName("A name from outside stays reachable in the expression")
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
    @DisplayName("sum over an empty list is zero")
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
    @DisplayName("An entry carries its amount")
    void anEntryCarriesItsAmount() {
        TestHost host = new TestHost();
        host.contents.add(Value.Selection.ofItems(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first().amount)
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("5"), host.logs);
    }

    @Test
    @DisplayName("sum adds the amounts together")
    void sumAddsTheAmounts() {
        TestHost host = new TestHost();
        host.contents.add(Value.Selection.ofItems(List.of(), 5));
        host.contents.add(Value.Selection.ofItems(List.of(), 7));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sum())
                }""", host);
        interpreter.call("zeigen", List.of());

        // Previously sum threw at this point: an entry was not a number, and
        // an inventory listing is the case that sum exists for in the first
        // place.
        assertEquals(List.of("12"), host.logs);
    }

    @Test
    @DisplayName("where filters by the amount")
    void whereFiltersByAmount() {
        TestHost host = new TestHost();
        host.contents.add(Value.Selection.ofItems(List.of(), 5));
        host.contents.add(Value.Selection.ofItems(List.of(), 70));
        host.contents.add(Value.Selection.ofItems(List.of(), 90));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().where(it.amount > 64).count())
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2"), host.logs);
    }

    @Test
    @DisplayName("sort orders by the amount")
    void sortOrdersByAmount() {
        TestHost host = new TestHost();
        host.contents.add(Value.Selection.ofItems(List.of(), 90));
        host.contents.add(Value.Selection.ofItems(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().sort(it.amount).first().amount)
                }""", host);
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("5"), host.logs, "der kleinste Posten steht vorn");
    }

    @Test
    @DisplayName("A selection over several kinds has no single kind")
    void aSelectionOverSeveralKindsHasNoSingleItem() {
        TestHost host = new TestHost();
        host.contents.add(Value.Selection.ofItems(List.of(), 5));

        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items().first().item)
                }""", host);

        // Without a kind, it.item is no information that can be invented.
        ScriptError error = org.junit.jupiter.api.Assertions.assertThrows(
                ScriptError.class, () -> interpreter.call("zeigen", List.of()));
        assertTrue(error.getMessage().contains("Art"), error.getMessage());
    }

    // ---- Lists in the program ----------------------------------------------

    @Test
    @DisplayName("A list can be written down")
    void alistCanBeWrittenDown() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log([3, 4, 5].count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("3"), host.logs);
    }

    @Test
    @DisplayName("plus gives a new list and leaves the old one alone")
    void plusGivesAnewListAndLeavesTheOldAlone() {
        // This is the decision nailed down here: a list is replaced, not
        // mutated. A mutating add would slip past the guard for const and the
        // protection in multiplayer — both hang on assignment.
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    let alt = [1, 2]
                    let neu = alt.plus(3)
                    log(alt.count())
                    log(neu.count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2", "3"), host.logs, "die alte Liste bleibt zweielementig");
    }

    @Test
    @DisplayName("without removes every occurrence")
    void withoutRemovesEveryOccurrence() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log([1, 2, 1].without(1).count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("1"), host.logs);
    }

    @Test
    @DisplayName("rest is everything but the first")
    void restIsEverythingButThefirst() {
        // Together with first this is the queue: take the front one, keep the
        // rest. Access by number does not exist.
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log([7, 8, 9].rest().first())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("8"), host.logs);
    }

    @Test
    @DisplayName("rest of an empty list is an empty list")
    void restOfAnemptyListIsAnemptyList() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log([].rest().count())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("0"), host.logs, "keine Ausnahme, sondern nichts");
    }

    @Test
    @DisplayName("A list literal can be walked over")
    void alistLiteralCanBeWalked() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    for name in ["a", "b"] {
                        log(name)
                    }
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("a", "b"), host.logs);
    }
}
