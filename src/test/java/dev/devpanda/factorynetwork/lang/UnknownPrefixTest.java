package dev.devpanda.factorynetwork.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ein Wort vor dem Doppelpunkt, das keine Ressourcenart ist.
 *
 * <p><b>Der teuerste Fehler, den ein Übersetzer machen kann, ist einer, der
 * nicht sagt, was los ist.</b> Genau den gab es hier: {@code chemiacl:hydrogen}
 * zerfiel in sechs Meldungen — „Bei move fehlt das Ziel", „Hier wird ein Wert
 * erwartet, gefunden wurde :", „from ist ein Schlüsselwort" —, und keine
 * einzige nannte den Tippfehler.
 *
 * <p>Das ist dieselbe Falle, die am 25.08. für eine aus JEI kopierte Kennung
 * behoben wurde ({@code item:mekanism:steel_ingot}, sieben Meldungen). Der
 * Lexer trägt den Vermerk dazu bis heute — nur galt er für die eine Form und
 * nicht für die andere.
 *
 * <p>Seit die Ressourcenarten eine offene Registry sind, hat das Wort vor dem
 * Doppelpunkt eine Liste, gegen die es geprüft werden kann. Vorher stand die
 * Liste viermal da: im Lexer, im Parser, in {@link Selectors} und im
 * Wertemodell.
 */
class UnknownPrefixTest {

    private static List<Diagnostic> errorsIn(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    @Test
    @DisplayName("Ein vertipptes Präfix ist eine Meldung und nicht sechs")
    void amistypedPrefixIsOneMessageAndNotSix() {
        List<Diagnostic> errors = errorsIn("""
                fn f() {
                    move 5 chemiacl:hydrogen from lager to tank
                }""");

        assertEquals(1, errors.size(),
                () -> "erwartet war eine Meldung, gekommen sind: " + errors);
        assertTrue(errors.get(0).message().contains("chemiacl"),
                () -> "sie muss das Wort nennen: " + errors.get(0));
    }

    @Test
    @DisplayName("Und sie schlägt das gemeinte vor")
    void anditSuggestsWhatWasMeant() {
        List<Diagnostic> errors = errorsIn("""
                fn f() {
                    move 5 chemiacl:hydrogen from lager to tank
                }""");

        String whole = errors.get(0).message() + " " + errors.get(0).hint();
        assertTrue(whole.contains("chemical"),
                () -> "der Hinweis soll auf chemical zeigen: " + whole);
    }

    @Test
    @DisplayName("Der Vorschlag ist anwendbar und trifft nur das Wort davor")
    void thesuggestionIsApplicableAndHitsOnlyTheWordBefore() {
        // Der Hinweis ist ein Satz für einen Menschen. Ein Editor müsste ihn
        // zerpflücken, um daraus eine Schnellkorrektur zu bauen — und zwei
        // Fassungen derselben Auskunft laufen auseinander. Deshalb trägt die
        // Meldung den Vorschlag zusätzlich als Ersetzung.
        Diagnostic error = errorsIn("""
                fn f() {
                    move 5 chemiacl:hydrogen from lager to tank
                }""").get(0);

        assertTrue(error.fix() != null, () -> "ohne Vorschlag: " + error);
        assertEquals("chemical", error.fix().text());

        // Unterstrichen wird die ganze Auswahl, ersetzt nur das Wort davor:
        // Was hinter dem Doppelpunkt steht, war ja richtig.
        assertEquals("chemiacl".length(),
                error.fix().span().end() - error.fix().span().start(),
                () -> "der Vorschlag greift zu weit: " + error.fix().span());
        assertTrue(error.span().end() > error.fix().span().end(),
                () -> "die Meldung soll mehr unterstreichen als sie ersetzt: " + error);
    }

    @Test
    @DisplayName("Ohne gemeinte Art gibt es auch nichts anzuwenden")
    void withoutAmeantKindThereIsNothingToApply() {
        // Ein Vorschlag wäre hier geraten, und eine Schnellkorrektur, die rät,
        // ist schlimmer als keine.
        Diagnostic error = errorsIn("""
                fn f() {
                    move 5 source:mana from quelle to storage
                }""").get(0);

        assertTrue(error.fix() == null, () -> "geraten: " + error.fix());
    }

    @Test
    @DisplayName("Ein Präfix aus einer Mod, die fehlt, nennt die bekannten")
    void aprefixFromAmissingModNamesTheKnownOnes() {
        // Kein Tippfehler, sondern eine Art, die es in diesem Pack nicht gibt.
        // Ein Vorschlag wäre hier geraten; die Liste ist die ehrliche Antwort.
        List<Diagnostic> errors = errorsIn("""
                fn f() {
                    move 5 source:mana from quelle to storage
                }""");

        assertEquals(1, errors.size(),
                () -> "erwartet war eine Meldung, gekommen sind: " + errors);
        String whole = errors.get(0).message() + " " + errors.get(0).hint();
        assertTrue(whole.contains("source"), () -> "das Wort muss dastehen: " + whole);
        assertTrue(whole.contains("item") && whole.contains("fluid"),
                () -> "und was es hier gibt: " + whole);
    }

    @Test
    @DisplayName("Ein Worker mit vertipptem Präfix meldet dasselbe")
    void aworkerWithAmistypedPrefixSaysTheSame() {
        List<Diagnostic> errors = errorsIn("""
                worker w {
                    from lager
                    to tank
                    filter fluidd:water
                }""");

        assertEquals(1, errors.size(),
                () -> "erwartet war eine Meldung, gekommen sind: " + errors);
        assertTrue(errors.get(0).message().contains("fluidd"),
                () -> "sie muss das Wort nennen: " + errors.get(0));
    }

    @Test
    @DisplayName("Die Meldung über einen fehlenden Namen ist deutsch")
    void themessageAboutAmissingNameIsGerman() {
        // Beim ersten Spielen im Fenster gelesen: „Hier wird der Name der
        // Gerät erwartet." Der Satz stand einmal da und setzte das nackte
        // Wort ein — für die drei weiblichen ging das auf, für die acht
        // anderen nicht.
        // Dritte Spalte: der Satz, der vorher dastand. Ohne ihn prüfte diese
        // Schleife nur, dass irgendetwas Richtiges dabeisteht — und nicht,
        // dass das Falsche weg ist.
        for (String[] fall : new String[][] {
                {"store", "des Geräts", "der Gerät"},
                {"worker", "des Workers", "der Worker"},
                {"display", "des Displays", "der Display"},
                {"recipe", "des Rezepts", "der Rezept"},
                {"multiblock", "des Multiblocks", "der Multiblock"},
                {"event", "des Ereignisses", "der Ereignis"},
                {"group", "der Gruppe", null},
                {"filter", "der Vorlage", null},
                {"fn", "der Funktion", null}}) {
            List<Diagnostic> errors = errorsIn(fall[0]);
            assertTrue(errors.stream().anyMatch(d -> d.message().contains(fall[1])),
                    () -> fall[0] + " soll " + fall[1] + " nennen: " + errors);
            if (fall[2] != null) {
                assertTrue(errors.stream().noneMatch(d -> d.message().contains(fall[2])),
                        () -> fall[0] + " darf nicht mehr " + fall[2] + " sagen: " + errors);
            }
        }
    }

    @Test
    @DisplayName("Eine Typangabe ohne Leerzeichen bleibt eine Typangabe")
    void atypeAnnotationWithoutAspaceStaysOne() {
        // fn f(x:Int) ist gültig und war es immer. Die neue Meldung darf sie
        // nicht einsammeln — sie steht in einer Parameterliste und nicht dort,
        // wo ein Wert erwartet wird.
        assertTrue(errorsIn("fn f(x:Int) {\n    log(x)\n}").isEmpty(),
                () -> "gemeldet wurde: " + errorsIn("fn f(x:Int) {\n    log(x)\n}"));
        assertTrue(errorsIn("event E(x:Int)\n\non E(v) {\n    log(v)\n}").isEmpty());
    }

    @Test
    @DisplayName("Ein benanntes Argument bleibt eines")
    void anamedArgumentStaysOne() {
        // strategy: least_filled steht mit Doppelpunkt in einer Klammer. Auch
        // ohne Leerzeichen ist es keine Auswahl.
        assertTrue(errorsIn("""
                fn f() {
                    log(storage.items().sort(it.amount))
                }""").isEmpty());
    }

    @Test
    @DisplayName("Die vier eingebauten Schreibweisen bleiben unberührt")
    void thefourBuiltinSpellingsAreUntouched() {
        assertTrue(errorsIn("""
                fn f() {
                    move 5 item:iron_ore from a to b
                    move 5 tag:c/ores from a to b
                    move 5 fluid:water from a to b
                    move 5 fluidtag:c/molten from a to b
                    move 5 chemical:mekanism/hydrogen from a to b
                    move all from a to b
                }""").isEmpty());
    }
}
