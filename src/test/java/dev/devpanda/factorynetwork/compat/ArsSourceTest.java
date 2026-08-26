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
 * Source aus Ars Nouveau — der Beweis, für den die offene Registry gebaut
 * wurde.
 *
 * <p>Diese Art kommt von einer fremden Mod. Was sie kostet, steht vollständig
 * in {@code compat/ars}: vier Klassen und ein Aufruf beim Laden. Was dieser
 * Prüflauf festhält, ist genau das — dass sie die Schnittstelle erfüllt, ohne
 * dass eine Zeile im Kern von ihr weiß.
 *
 * <p>Was er <b>nicht</b> prüfen kann, ist der Zugriff auf die Maschinen: Dazu
 * müsste Ars Nouveau laufen. Ohne die Mod meldet er sich als „nicht da", und
 * genau das ist hier die richtige Antwort.
 */
class ArsSourceTest {

    private static final Span ANYWHERE = new Span(0, 0, 1, 1);

    private static Expr.Selector selector(String path) {
        return new Expr.Selector(Expr.Selector.Kind.CUSTOM, "source", "", path, ANYWHERE);
    }

    @Test
    @DisplayName("Die Art trägt Präfix, Kennung und Plattennamen")
    void thekindCarriesPrefixIdAndDiskName() {
        assertEquals("source", ArsSource.INSTANCE.prefix());
        assertEquals("ars_nouveau", ArsSource.INSTANCE.id().getNamespace());
        assertEquals("source", ArsSource.INSTANCE.id().getPath());
        // Der Name auf der Platte muss über Neustarts derselbe bleiben und
        // darf keinem anderen gleichen — beides prüft die Registry beim
        // Anmelden, und beides steht hier fest.
        assertEquals("src", ArsSource.INSTANCE.tag());
        assertEquals("srcsel", ArsSource.INSTANCE.selectionTag());
    }

    @Test
    @DisplayName("source:source trifft die eine Sorte, alles andere nichts")
    void sourcecolonSourceHitsTheOneSort() {
        assertEquals(1, ArsSource.INSTANCE.resolve(selector("source")).size());
        // Keine stille Umdeutung: Wer sich vertippt, bewegt nichts, statt
        // unbemerkt das Einzige zu treffen, das es gibt.
        assertTrue(ArsSource.INSTANCE.resolve(selector("mana")).isEmpty());
        assertTrue(ArsSource.INSTANCE.resolve(selector("")).isEmpty());
    }

    @Test
    @DisplayName("Der Schlüssel ist eine Zeichenkette und kein fremder Typ")
    void thekeyIsAstringAndNotAforeignType() {
        // Eine Signatur mit einer Klasse von Ars Nouveau würde diese beim
        // Laden auflösen — auch in einem Pack ohne die Mod. Dieselbe Vorsicht
        // wie bei den Chemikalien.
        assertEquals(String.class, ArsSource.INSTANCE.type());
        Object key = ArsSource.INSTANCE.resolve(selector("source")).get(0);
        assertTrue(ArsSource.INSTANCE.type().isInstance(key));
        assertEquals("source", ArsSource.INSTANCE.idOf(key));
    }

    @Test
    @DisplayName("Jedes Netz bekommt seinen eigenen Zwischenhalt")
    void everynetworkGetsItsOwnBuffer() {
        assertNotSame(ArsSource.INSTANCE.newStore(), ArsSource.INSTANCE.newStore());
    }

    @Test
    @DisplayName("Der Zwischenhalt meldet, was nicht hineinpasste")
    void thebufferReportsWhatDidNotFit() {
        SourceBuffer buffer = new SourceBuffer();
        assertEquals(0, buffer.insert("source", 100));
        assertEquals(100, buffer.count("source"));

        // insert meldet den Rest, nicht das Angekommene — dieselbe Regel wie
        // bei allen anderen Speichern.
        long tooMuch = SourceBuffer.CAPACITY;
        assertEquals(100, buffer.insert("source", tooMuch));
        assertEquals(SourceBuffer.CAPACITY, buffer.count("source"));

        assertEquals(SourceBuffer.CAPACITY, buffer.extract("source", Long.MAX_VALUE));
        assertEquals(0, buffer.count("source"));
    }

    @Test
    @DisplayName("Eine andere Art lagert dort nicht")
    void anotherKindDoesNotRestThere() {
        SourceBuffer buffer = new SourceBuffer();
        // Der Rest ist alles: Nichts davon passte hinein.
        assertEquals(50, buffer.insert("mana", 50));
        assertEquals(0, buffer.count("mana"));
        assertEquals(0, buffer.extract("mana", 50));
    }

    @Test
    @DisplayName("Ohne Ars Nouveau kommt an keiner Maschine etwas an")
    void withoutArsNouveauNothingArrivesAtAmachine() {
        MachineAccess access = ArsSource.INSTANCE.machine();
        // Nicht NONE: Der Zugriff ist angemeldet. Er findet nur nichts, weil
        // die Mod fehlt — und das ist ein anderer Satz für den Spieler als
        // „diese Art lässt sich an keiner Maschine bewegen".
        assertNotSame(MachineAccess.NONE, access);
        assertEquals(0, access.count(null, null, null, List.of("source")));
    }
}
