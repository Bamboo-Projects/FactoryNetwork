package dev.devpanda.factorynetwork.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Teil erklärter Rezepte, der ohne Spielregistrierungen prüfbar ist.
 *
 * <p>Was eine Auswahl trifft, kann hier niemand fragen — dafür gibt es die
 * Prüfläufe im Spiel. Was hier steht, ist die <b>Buchführung</b>: wie eine
 * Station heißt, wie man sie wieder auseinandernimmt, und wie viel eine
 * Nebenzutat für mehrere Durchgänge braucht. Genau daran wäre der Fehler
 * teuer und im Spiel schwer zu sehen: Zwei Rezepte am selben Gerät, und die
 * Maschine bekommt das Wasser des anderen.
 */
class DeclaredRecipesTest {

    private static Expr.Selector wasser() {
        return new Expr.Selector(Expr.Selector.Kind.FLUID, "minecraft", "water",
                Span.of(0, 0, 1, 1));
    }

    @Test
    @DisplayName("Eine Station trägt Gerät und Rezeptnamen")
    void astationCarriesDeviceAndRecipe() {
        String station = DeclaredRecipes.stationFor("brecher", "erz_mahlen");

        assertEquals("brecher", DeclaredRecipes.deviceOf(station));
        assertEquals("erz_mahlen", DeclaredRecipes.recipeOf(station));
    }

    @Test
    @DisplayName("Zwei Rezepte am selben Gerät sind zwei Stationen")
    void twoRecipesAtOnedeviceAreTwoStations() {
        assertNotEquals(DeclaredRecipes.stationFor("brecher", "erz_mahlen"),
                DeclaredRecipes.stationFor("brecher", "erz_waschen"));
    }

    @Test
    @DisplayName("Eine Station aus einer alten Welt hat noch keinen Rezeptnamen")
    void anoldStationHasNorecipeName() {
        // Gespeicherte Aufträge aus der Zeit vor den Nebenzutaten nennen nur
        // das Gerät. Sie müssen weiterlaufen; sie haben nur keine Zutaten,
        // die eingefüllt werden wollen.
        assertEquals("brecher", DeclaredRecipes.deviceOf("at:brecher"));
        assertEquals("", DeclaredRecipes.recipeOf("at:brecher"));
    }

    @Test
    @DisplayName("Was keine Station ist, gibt kein Gerät her")
    void whatIsNotAstationHasNodevice() {
        assertNull(DeclaredRecipes.deviceOf("minecraft:blasting"));
    }

    @Test
    @DisplayName("Eine Nebenzutat wächst mit der Zahl der Durchgänge")
    void anextraGrowsWithTheRuns() {
        // Der Fehler, den das verhindert: Die Zutaten gehen für alle
        // Durchgänge auf einmal in die Maschine, das Wasser aber nur für
        // einen — dann steht eine Maschine mit vier Erzen und einem Eimer da.
        DeclaredRecipes.Extra extra = new DeclaredRecipes.Extra(true, 1000, wasser());

        assertEquals(1000, extra.needFor(1));
        assertEquals(4000, extra.needFor(4));
    }

    @Test
    @DisplayName("Ohne Durchgang wird nichts gebraucht")
    void withoutArunNothingIsNeeded() {
        assertEquals(0, new DeclaredRecipes.Extra(true, 1000, wasser()).needFor(0));
    }
}
