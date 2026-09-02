package dev.devpanda.factorynetwork.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A word before the colon that is no resource kind.
 *
 * <p><b>The most expensive mistake a compiler can make is one that does not
 * say what is going on.</b> Exactly that existed here: {@code chemiacl:hydrogen}
 * fell apart into six messages — „Bei move fehlt das Ziel", „Hier wird ein Wert
 * erwartet, gefunden wurde :", „from ist ein Schlüsselwort" —, and not a
 * single one named the typo.
 *
 * <p>That is the same trap that was fixed on 25.08. for an ID copied from JEI
 * ({@code item:mekanism:steel_ingot}, seven messages). The lexer carries the
 * note about it to this day — only it applied to the one form and not to the
 * other.
 *
 * <p>Since the resource kinds became an open registry, the word before the
 * colon has a list it can be checked against. Before, the list existed four
 * times: in the lexer, in the parser, in {@link Selectors} and in the value
 * model.
 */
class UnknownPrefixTest {

    private static List<Diagnostic> errorsIn(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    @Test
    @DisplayName("A mistyped prefix is one message and not six")
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
    @DisplayName("And it suggests what was meant")
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
    @DisplayName("The suggestion is applicable and hits only the word before")
    void thesuggestionIsApplicableAndHitsOnlyTheWordBefore() {
        // The hint is a sentence for a human. An editor would have to pick it
        // apart to build a quick fix from it — and two versions of the same
        // information drift apart. That is why the message additionally
        // carries the suggestion as a replacement.
        Diagnostic error = errorsIn("""
                fn f() {
                    move 5 chemiacl:hydrogen from lager to tank
                }""").get(0);

        assertTrue(error.fix() != null, () -> "ohne Vorschlag: " + error);
        assertEquals("chemical", error.fix().text());

        // The whole selection is underlined, only the word before is
        // replaced: what comes after the colon was right, after all.
        assertEquals("chemiacl".length(),
                error.fix().span().end() - error.fix().span().start(),
                () -> "der Vorschlag greift zu weit: " + error.fix().span());
        assertTrue(error.span().end() > error.fix().span().end(),
                () -> "die Meldung soll mehr unterstreichen als sie ersetzt: " + error);
    }

    @Test
    @DisplayName("Without a meant kind there is nothing to apply either")
    void withoutAmeantKindThereIsNothingToApply() {
        // A suggestion here would be a guess, and a quick fix that guesses is
        // worse than none.
        Diagnostic error = errorsIn("""
                fn f() {
                    move 5 pressure:air from quelle to storage
                }""").get(0);

        assertTrue(error.fix() == null, () -> "geraten: " + error.fix());
    }

    @Test
    @DisplayName("A prefix from a mod that is missing names the known ones")
    void aprefixFromAmissingModNamesTheKnownOnes() {
        // Not a typo, but a kind that does not exist in this pack. A
        // suggestion here would be a guess; the list is the honest answer.
        //
        // No longer „source": since 26.08. compat/ars registers this kind at
        // load time, even without Ars Nouveau — otherwise source:source in a
        // pack without the mod would mean "no resource kind" instead of "this
        // mod is missing". A unit test loads no FML and therefore still would
        // not see it; but an example that means something different in game
        // than it does here is no example.
        List<Diagnostic> errors = errorsIn("""
                fn f() {
                    move 5 pressure:air from quelle to storage
                }""");

        assertEquals(1, errors.size(),
                () -> "erwartet war eine Meldung, gekommen sind: " + errors);
        String whole = errors.get(0).message() + " " + errors.get(0).hint();
        assertTrue(whole.contains("pressure"), () -> "das Wort muss dastehen: " + whole);
        assertTrue(whole.contains("item") && whole.contains("fluid"),
                () -> "und was es hier gibt: " + whole);
    }

    @Test
    @DisplayName("A worker with a mistyped prefix reports the same")
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
    @DisplayName("The message about a missing name is in German")
    void themessageAboutAmissingNameIsGerman() {
        // Read in the window on first playing: „Hier wird der Name der
        // Gerät erwartet." The sentence stood there once and inserted the
        // bare word — for the three feminine nouns that worked out, for the
        // eight others it did not.
        // Third column: the sentence that stood there before. Without it this
        // loop checked only that something correct is present — and not that
        // the wrong thing is gone.
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
    @DisplayName("A type annotation without a space stays a type annotation")
    void atypeAnnotationWithoutAspaceStaysOne() {
        // fn f(x:Int) is valid and always was. The new message must not
        // collect it — it sits in a parameter list and not where a value is
        // expected.
        assertTrue(errorsIn("fn f(x:Int) {\n    log(x)\n}").isEmpty(),
                () -> "gemeldet wurde: " + errorsIn("fn f(x:Int) {\n    log(x)\n}"));
        assertTrue(errorsIn("event E(x:Int)\n\non E(v) {\n    log(v)\n}").isEmpty());
    }

    @Test
    @DisplayName("A named argument stays one")
    void anamedArgumentStaysOne() {
        // strategy: least_filled sits with a colon inside parentheses. Even
        // without a space it is no selection.
        assertTrue(errorsIn("""
                fn f() {
                    log(storage.items().sort(it.amount))
                }""").isEmpty());
    }

    @Test
    @DisplayName("The four built-in spellings stay untouched")
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
