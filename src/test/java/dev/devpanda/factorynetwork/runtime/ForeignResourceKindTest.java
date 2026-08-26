package dev.devpanda.factorynetwork.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.runtime.flow.ValueCodec;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eine Ressourcenart aus einer Mod, die es nicht gibt.
 *
 * <p><b>Der Beweis, dass die Registry offen ist.</b> Sie ist am 26.08.
 * entschieden worden: Fremde Mods dürfen die Sprache erweitern. Was das
 * heißt, lässt sich nicht daran ablesen, dass die eigenen drei Arten
 * funktionieren — die taten es vorher auch. Es lässt sich nur an einer
 * <b>vierten</b> ablesen, die nirgends im Kern steht.
 *
 * <p>Diese hier heißt {@code testsource} und ist eine Erfindung dieses
 * Tests. Sie braucht keine Mod: Ihre Schlüssel sind Kennungen als Text, wie
 * bei den Chemikalien, und ihre Auflösung denkt sich zwei Einträge aus. Was
 * geprüft wird, ist alles unterhalb des Übersetzers — der Wert, sein Text,
 * die Platte und der Speicher.
 *
 * <p>Was hier <b>nicht</b> geprüft wird, ist die zweite Achse: wie eine
 * fremde Art an einer fremden Maschine gelesen und geschrieben wird. Die gibt
 * es noch nicht, und {@code entscheidungen.md} sagt, warum das kein
 * Versäumnis dieser Registry ist.
 */
class ForeignResourceKindTest {

    private static final String PREFIX = "testsource";

    /** Eine Art, die der Kern nicht kennt. */
    private static final ResourceKind SOURCE = new ResourceKind() {

        @Override
        public net.minecraft.resources.ResourceLocation id() {
            return net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath("factorynetwork_test", "source");
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String plural() {
            return "Quellen";
        }

        @Override
        public Class<?> type() {
            return String.class;
        }

        @Override
        public String tag() {
            return "tsrc";
        }

        @Override
        public String selectionTag() {
            return "tsrcsel";
        }

        @Override
        public String idOf(Object key) {
            return (String) key;
        }

        @Override
        public Object fromId(String id) {
            return id;
        }

        @Override
        public String nameOf(Object key) {
            return "Quelle " + key;
        }

        @Override
        public List<?> resolve(Expr selector) {
            return List.of("ars:mana", "ars:starlight");
        }
    };

    @BeforeAll
    static void anmelden() {
        if (ResourceKinds.byPrefix(PREFIX) == null) {
            ResourceKinds.register(SOURCE);
        }
    }

    @Test
    @DisplayName("Die fremde Art steht in der Registry, unter ihrem Präfix")
    void theforeignKindIsInTheRegistry() {
        assertSame(SOURCE, ResourceKinds.byPrefix(PREFIX));
        assertNotNull(ResourceKinds.byId(SOURCE.id()));
    }

    @Test
    @DisplayName("Ein Wert der fremden Art beschreibt sich selbst")
    void avalueOfTheForeignKindDescribesItself() {
        // Weder Value noch describe() wissen von dieser Art. Sie fragen sie.
        assertEquals("Quelle ars:mana",
                new Value.Resource(SOURCE, "ars:mana").describe());
        assertEquals("2 Quellen",
                new Value.Selection(SOURCE, List.of("ars:mana", "ars:starlight"), 500)
                        .describe());
    }

    @Test
    @DisplayName("Gemischt geht auch bei einer fremden Art nicht")
    void mixingIsRefusedForAforeignKindToo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Value.Selection(SOURCE, List.of(1), 5));
    }

    @Test
    @DisplayName("Ein wartender Ablauf hält sie unter ihrem eigenen Namen")
    void awaitingFlowKeepsItUnderItsOwnName() {
        // Die Namen auf der Platte gehören der Art und nicht dem Codec —
        // sonst müsste für jede neue eine Zeile dort dazu.
        CompoundTag single = new CompoundTag();
        single.putString("t", "tsrc");
        single.putString("v", "ars:mana");
        assertEquals(single, ValueCodec.write(ValueCodec.read(single)));

        ListTag ids = new ListTag();
        ids.add(StringTag.valueOf("ars:mana"));
        ids.add(StringTag.valueOf("ars:starlight"));
        CompoundTag selection = new CompoundTag();
        selection.putString("t", "tsrcsel");
        selection.put("i", ids);
        selection.putLong("a", 500);
        assertEquals(selection, ValueCodec.write(ValueCodec.read(selection)));
    }

    @Test
    @DisplayName("Ohne eigenen Speicher lagert sie nichts, und sagt das")
    void withoutAstoreItKeepsNothing() {
        // Eine Art darf beweglich sein, ohne lagerbar zu sein. Der Speicher,
        // der nichts kann, ist die ehrliche Antwort darauf.
        assertSame(dev.devpanda.factorynetwork.network.ResourceStore.NONE, SOURCE.newStore());
    }

    @Test
    @DisplayName("Ein Programm darf die fremde Art hinschreiben")
    void aprogramMayWriteTheForeignKind() {
        // Der eigentliche Beweis: Der Übersetzer kennt „testsource" nicht und
        // nimmt es trotzdem an — weil er die Registry fragt, statt eine
        // eingebaute Liste zu haben. Ohne diese Zeile wäre die Registry ein
        // Innenausbau ohne Tür.
        var result = new dev.devpanda.factorynetwork.lang.Project(
                java.util.Map.of("main.mf", """
                        fn f() {
                            move 5 testsource:mana from quelle to storage
                        }""")).parse();

        assertEquals(List.of(), result.diagnostics().stream()
                        .filter(dev.devpanda.factorynetwork.lang.Diagnostic::isError).toList(),
                "eine angemeldete Art muss sich hinschreiben lassen");
    }

    @Test
    @DisplayName("Zweimal dasselbe Präfix ist ein Fehler, kein Zufall")
    void thesamePrefixTwiceIsAnerror() {
        assertThrows(IllegalStateException.class, () -> ResourceKinds.register(SOURCE));
    }

    @Test
    @DisplayName("Die reservierten Wörter gehören der Sprache")
    void thereservedWordsBelongToTheLanguage() {
        // tag, fluidtag, power und all stehen im Programm und meinen keine
        // Art. Wer sie belegt, macht bestehende Programme mehrdeutig.
        for (String reserviert : List.of("tag", "fluidtag", "power", "all", "item")) {
            assertThrows(IllegalStateException.class,
                    () -> ResourceKinds.register(kindWithPrefix(reserviert)),
                    () -> reserviert + " darf niemand belegen");
        }
    }

    private static ResourceKind kindWithPrefix(String prefix) {
        return new ResourceKind() {

            @Override
            public net.minecraft.resources.ResourceLocation id() {
                return net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("factorynetwork_test", prefix);
            }

            @Override
            public String prefix() {
                return prefix;
            }

            @Override
            public String plural() {
                return prefix;
            }

            @Override
            public Class<?> type() {
                return String.class;
            }

            @Override
            public String tag() {
                return "x_" + prefix;
            }

            @Override
            public String selectionTag() {
                return "xs_" + prefix;
            }

            @Override
            public String idOf(Object key) {
                return (String) key;
            }

            @Override
            public Object fromId(String id) {
                return id;
            }

            @Override
            public String nameOf(Object key) {
                return String.valueOf(key);
            }

            @Override
            public List<?> resolve(Expr selector) {
                return List.of();
            }
        };
    }
}
