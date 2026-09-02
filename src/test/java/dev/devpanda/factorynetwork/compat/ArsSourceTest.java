package dev.devpanda.factorynetwork.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.compat.ars.ArsSource;
import dev.devpanda.factorynetwork.compat.ars.SourceBuffer;
import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.MachineAccess;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Source from Ars Nouveau — the proof for which the open registry was built.
 *
 * <p>This kind comes from a foreign mod. What it costs stands entirely in
 * {@code compat/ars}: four classes and one call at load time. What this check
 * captures is exactly that — that it fulfills the interface without a single
 * line in the core knowing about it.
 *
 * <p>What it <b>cannot</b> check is access to the machines: that would need
 * Ars Nouveau to be running. Without the mod it reports itself as "not
 * there", and that is exactly the right answer here.
 */
class ArsSourceTest {

    private static final Span ANYWHERE = new Span(0, 0, 1, 1);

    private static Expr.Selector selector(String path) {
        return new Expr.Selector(Expr.Selector.Kind.CUSTOM, "source", "", path, ANYWHERE);
    }

    @Test
    @DisplayName("The kind carries prefix, id and disk name")
    void thekindCarriesPrefixIdAndDiskName() {
        assertEquals("source", ArsSource.INSTANCE.prefix());
        assertEquals("ars_nouveau", ArsSource.INSTANCE.id().getNamespace());
        assertEquals("source", ArsSource.INSTANCE.id().getPath());
        // The name on the disk must stay the same across restarts and must
        // not equal any other — the registry checks both at registration, and
        // both are fixed here.
        assertEquals("src", ArsSource.INSTANCE.tag());
        assertEquals("srcsel", ArsSource.INSTANCE.selectionTag());
    }

    @Test
    @DisplayName("source:source hits the one sort, nothing else")
    void sourcecolonSourceHitsTheOneSort() {
        assertEquals(1, ArsSource.INSTANCE.resolve(selector("source")).size());
        // No silent reinterpretation: whoever mistypes moves nothing, instead
        // of unnoticed hitting the only one that exists.
        assertTrue(ArsSource.INSTANCE.resolve(selector("mana")).isEmpty());
        assertTrue(ArsSource.INSTANCE.resolve(selector("")).isEmpty());
    }

    @Test
    @DisplayName("The key is a string and not a foreign type")
    void thekeyIsAstringAndNotAforeignType() {
        // A signature with a class from Ars Nouveau would resolve it at load
        // time — even in a pack without the mod. The same caution as with the
        // chemicals.
        assertEquals(String.class, ArsSource.INSTANCE.type());
        Object key = ArsSource.INSTANCE.resolve(selector("source")).get(0);
        assertTrue(ArsSource.INSTANCE.type().isInstance(key));
        assertEquals("source", ArsSource.INSTANCE.idOf(key));
    }

    @Test
    @DisplayName("Every network gets its own buffer")
    void everynetworkGetsItsOwnBuffer() {
        assertNotSame(ArsSource.INSTANCE.newStore(), ArsSource.INSTANCE.newStore());
    }

    @Test
    @DisplayName("The buffer reports what did not fit")
    void thebufferReportsWhatDidNotFit() {
        SourceBuffer buffer = new SourceBuffer();
        assertEquals(0, buffer.insert("source", 100));
        assertEquals(100, buffer.count("source"));

        // insert reports the rest, not what arrived — the same rule as with
        // all other stores.
        long tooMuch = SourceBuffer.CAPACITY;
        assertEquals(100, buffer.insert("source", tooMuch));
        assertEquals(SourceBuffer.CAPACITY, buffer.count("source"));

        assertEquals(SourceBuffer.CAPACITY, buffer.extract("source", Long.MAX_VALUE));
        assertEquals(0, buffer.count("source"));
    }

    @Test
    @DisplayName("Another kind does not rest there")
    void anotherKindDoesNotRestThere() {
        SourceBuffer buffer = new SourceBuffer();
        // The rest is everything: none of it fit in.
        assertEquals(50, buffer.insert("mana", 50));
        assertEquals(0, buffer.count("mana"));
        assertEquals(0, buffer.extract("mana", 50));
    }

    @Test
    @DisplayName("Without Ars Nouveau nothing arrives at any machine")
    void withoutArsNouveauNothingArrivesAtAmachine() {
        MachineAccess access = ArsSource.INSTANCE.machine();
        // Not NONE: the access is registered. It just finds nothing because
        // the mod is missing — and that is a different sentence for the player
        // than "this kind cannot be moved at any machine".
        assertNotSame(MachineAccess.NONE, access);
        assertEquals(0, access.count(null, null, null, List.of("source")));
    }
}
