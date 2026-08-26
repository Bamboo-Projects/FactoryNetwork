package dev.devpanda.factorynetwork.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eine Chemikalie als Wert.
 *
 * <p>Sie fehlte im Wertemodell, obwohl `chemical:` sich längst bewegen und
 * zählen ließ: Ein Posten aus einer Bestandsliste konnte sagen, wie viel er
 * ist, aber nicht, was — {@code it.item} und {@code it.fluid} gab es,
 * {@code it.chemical} nicht. Das ist der Teil von 1.8, dessen Bedingung
 * erfüllt ist, seit Mekanism angebunden ist.
 *
 * <p><b>Der Kern spricht in Kennungen.</b> Eine Chemikalie steht hier als
 * Text da, so wie überall außerhalb von {@code compat/mekanism}: Eine
 * Signatur mit einem Mekanism-Typ würde die Klasse beim Laden auflösen, und
 * ohne die Mod bräche alles zusammen, was sie nur streift.
 *
 * <p>Was hier geprüft wird, ist deshalb die <b>Mechanik über Texten</b> — die
 * Auflösung gegen die Registry braucht das laufende Spiel und steht in den
 * Prüfläufen.
 */
class ChemicalValueTest {

    /** Eine Welt aus Papier mit einem Bestand, den sie sich ausdenkt. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();

        /** Was eine Bestandsliste liefern soll. */
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
        public List<Value> storedItems() {
            return stored;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("it.chemical nennt die Sorte eines Postens")
    void itChemicalNamesTheKind() {
        TestHost host = new TestHost();
        host.stored.add(new Value.ChemicalSelection(List.of("mekanism:hydrogen"), 500));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.amount)
                        log(posten.chemical)
                    }
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(2, host.logs.size(), () -> host.logs.toString());
        assertEquals("500", host.logs.get(0));
        assertTrue(host.logs.get(1).contains("hydrogen"),
                () -> "die Sorte muss ablesbar sein: " + host.logs.get(1));
    }

    @Test
    @DisplayName("Ein Posten über mehrere Sorten hat keine eine")
    void apostOverSeveralKindsHasNosingleOne() {
        // Dieselbe Regel wie bei it.item: Sich für die erste zu entscheiden
        // wäre geraten.
        TestHost host = new TestHost();
        host.stored.add(new Value.ChemicalSelection(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.chemical)
                    }
                }""", host);

        assertThrows(ScriptError.class, () -> interpreter.call("zeigen", List.of()));
    }

    @Test
    @DisplayName("Eine Chemikalie übersteht das Speichern eines Ablaufs")
    void achemicalSurvivesSavingAflow() {
        // Ein Ablauf, der auf ein Ereignis wartet, wird gespeichert. Ohne
        // Zwilling im Codec verlöre ein wartender Ablauf still seinen Wert.
        Value single = new Value.ChemicalValue("mekanism:hydrogen");
        Value selection = new Value.ChemicalSelection(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500);

        assertEquals(single, dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(single)));
        assertEquals(selection, dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(selection)));
    }

    @Test
    @DisplayName("Ohne Mekanism steht die Kennung da, nicht ein erfundener Name")
    void withoutMekanismTheIdIsShown() {
        // describe() geht über Chemicals.nameOf, und das gibt ohne die Mod
        // die Kennung zurück. Erfunden wird nichts.
        assertEquals("mekanism:hydrogen",
                new Value.ChemicalValue("mekanism:hydrogen").describe());
    }
}
