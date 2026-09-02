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
 * A resource kind from a mod that does not exist.
 *
 * <p><b>The proof that the registry is open.</b> It was decided on 26.08.:
 * foreign mods may extend the language. What that means cannot be read off
 * the fact that the own three kinds work — they did so before too. It can
 * only be read off a <b>fourth</b> one that stands nowhere in the core.
 *
 * <p>This one here is called {@code testsource} and is an invention of this
 * test. It needs no mod: its keys are identifiers as text, as with the
 * chemicals, and its resolution makes up two entries. What is tested is
 * everything below the compiler — the value, its text, the disk and the
 * storage.
 *
 * <p>What is <b>not</b> tested here is the second axis: how a foreign kind is
 * read and written at a foreign machine. That does not exist yet, and
 * {@code entscheidungen.md} says why this is no failing of this registry.
 */
class ForeignResourceKindTest {

    private static final String PREFIX = "testsource";

    /** A kind that the core does not know. */
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
    @DisplayName("The foreign kind is in the registry, under its prefix")
    void theforeignKindIsInTheRegistry() {
        assertSame(SOURCE, ResourceKinds.byPrefix(PREFIX));
        assertNotNull(ResourceKinds.byId(SOURCE.id()));
    }

    @Test
    @DisplayName("A value of the foreign kind describes itself")
    void avalueOfTheForeignKindDescribesItself() {
        // Neither Value nor describe() knows of this kind. They ask it.
        assertEquals("Quelle ars:mana",
                new Value.Resource(SOURCE, "ars:mana").describe());
        assertEquals("2 Quellen",
                new Value.Selection(SOURCE, List.of("ars:mana", "ars:starlight"), 500)
                        .describe());
    }

    @Test
    @DisplayName("Mixing is refused for a foreign kind too")
    void mixingIsRefusedForAforeignKindToo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Value.Selection(SOURCE, List.of(1), 5));
    }

    @Test
    @DisplayName("A waiting flow keeps it under its own name")
    void awaitingFlowKeepsItUnderItsOwnName() {
        // The names on disk belong to the kind and not to the codec —
        // otherwise every new one would need a line added there.
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
    @DisplayName("Without its own store it keeps nothing, and says so")
    void withoutAstoreItKeepsNothing() {
        // A kind may be movable without being storable. The store that can
        // do nothing is the honest answer to that.
        assertSame(dev.devpanda.factorynetwork.network.ResourceStore.NONE, SOURCE.newStore());
    }

    @Test
    @DisplayName("A program may write down the foreign kind")
    void aprogramMayWriteTheForeignKind() {
        // The actual proof: the compiler does not know „testsource" and yet
        // accepts it — because it asks the registry instead of having a
        // built-in list. Without this line the registry would be an interior
        // build-out without a door.
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
    @DisplayName("Without machine access it moves nowhere, and says so")
    void withoutMachineAccessItMovesNowhere() {
        // The second axis: where a kind lies in the network, newStore() says;
        // how it reaches a foreign machine, machine() says. Both may be
        // missing, and both are then missing audibly instead of silently.
        assertSame(dev.devpanda.factorynetwork.network.MachineAccess.NONE, SOURCE.machine());

        var none = dev.devpanda.factorynetwork.network.MachineAccess.NONE;
        assertEquals(0L, none.count(null, null, null, List.of("ars:mana")));
        assertEquals(0L, none.fill(dev.devpanda.factorynetwork.network.ResourceStore.NONE,
                null, null, null, List.of("ars:mana"), 100));
        assertEquals(0L, none.drain(null, null, null, List.of("ars:mana"),
                dev.devpanda.factorynetwork.network.ResourceStore.NONE, 100));
    }

    @Test
    @DisplayName("The same prefix twice is an error, not a coincidence")
    void thesamePrefixTwiceIsAnerror() {
        assertThrows(IllegalStateException.class, () -> ResourceKinds.register(SOURCE));
    }

    @Test
    @DisplayName("The reserved words belong to the language")
    void thereservedWordsBelongToTheLanguage() {
        // tag, fluidtag, power and all stand in the program and mean no kind.
        // Whoever claims them makes existing programs ambiguous.
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
