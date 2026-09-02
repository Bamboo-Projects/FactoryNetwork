package dev.devpanda.factorynetwork.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a selection says about itself.
 *
 * <p>{@code describe()} stands in the log, in the network tab and in every
 * error message that names a value. The texts are therefore promises to the
 * player and not side effects of the value model — a rebuild beneath must not
 * move them.
 *
 * <p>Items and fluids stand here only as an empty selection: a single kind
 * needs the registry, and that does not exist without a running game. As far
 * as the number in front is concerned, that is no loss — it counts entries
 * and not kinds.
 */
class ResourceValueTest {

    @Test
    @DisplayName("An item selection counts kinds")
    void anitemSelectionCountsKinds() {
        assertEquals("0 Arten", Value.Selection.ofItems(List.of(), 64).describe());
    }

    @Test
    @DisplayName("A fluid selection counts fluids")
    void afluidSelectionCountsFluids() {
        assertEquals("0 Flüssigkeiten", Value.Selection.ofFluids(List.of(), 1000).describe());
    }

    @Test
    @DisplayName("A chemical selection counts chemicals")
    void achemicalSelectionCountsChemicals() {
        assertEquals("2 Chemikalien", Value.Selection.ofChemicals(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500).describe());
    }

    @Test
    @DisplayName("A selection carries one kind and not two")
    void aselectionCarriesOneKindAndNotTwo() {
        // A selection over water and stone would be something different at
        // every place of use — the same rule that FilterKind sets up for
        // templates. Previously it was enforced by three separate records;
        // now it stands in the constructor, and that is why it is tested here.
        assertThrows(IllegalArgumentException.class, () -> new Value.Selection(
                ResourceKinds.ITEM, List.of("mekanism:hydrogen"), 5));
    }

    @Test
    @DisplayName("The kind of a resolved selection can be read")
    void thekindOfAresolvedSelectionCanBeRead() {
        // Exactly this question chooses the path in move and count. Previously
        // it existed twice — once for fluids, once for chemicals — and the
        // second one did not know the resolved selection.
        assertEquals(ResourceKinds.CHEMICAL, ResourceKind.of(
                Value.Selection.ofChemicals(List.of("mekanism:hydrogen"), 100)));
        assertEquals(ResourceKinds.CHEMICAL, ResourceKind.of(
                Value.Resource.ofChemical("mekanism:hydrogen")));
        assertEquals(ResourceKinds.FLUID,
                ResourceKind.of(new Value.Request("fluid:water", -1)));
        assertEquals(ResourceKinds.ITEM,
                ResourceKind.of(new Value.Request("tag:c/ores", -1)));
    }

    @Test
    @DisplayName("all is not a resource kind")
    void allIsNotAresourceKind() {
        // It is the declaration that there is no filter. If ITEM came out
        // here, „all" would decide the kind too — and whoever writes it means
        // precisely not that.
        assertNull(ResourceKind.of(new Value.Request("all", -1)));
        assertNull(ResourceKind.of(Value.Nothing.get()));
    }

    @Test
    @DisplayName("The hint names the kind that was asked for")
    void thehintNamesTheMemberThatWasAsked() {
        // Previously every one of these hints said „it.item" — even when
        // someone had written it.fluid. The twin was copied, the text in it
        // not adjusted along with it.
        TestHost host = new TestHost();
        host.stored.add(Value.Selection.ofFluids(List.of(), 1000));
        Parser.ParseResult result = Parser.parse("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.fluid)
                    }
                }""");
        Interpreter interpreter = new Interpreter(result.program(), host);

        ScriptError error = assertThrows(ScriptError.class,
                () -> interpreter.call("zeigen", List.of()));

        assertTrue(error.getMessage().contains("it.fluid")
                        || String.valueOf(error.hint()).contains("it.fluid"),
                () -> "gemeldet wurde: " + error.getMessage() + " / " + error.hint());
    }

    /** A paper world with an inventory it makes up. */
    private static final class TestHost implements Interpreter.Host {

        final List<Value> stored = new ArrayList<>();

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
        public List<Value> storedItems() {
            return stored;
        }
    }
}
