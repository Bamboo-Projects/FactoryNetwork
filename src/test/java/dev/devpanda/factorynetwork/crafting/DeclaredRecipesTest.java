package dev.devpanda.factorynetwork.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The part of declared recipes that is checkable without game registrations.
 *
 * <p>What makes a choice cannot be asked here — that is what the in-game
 * checks are for. What stands here is the <b>bookkeeping</b>: what a station
 * is called, how it is taken apart again, and how much a side ingredient
 * needs for several runs. On exactly this the error would be costly and hard
 * to see in the game: two recipes at the same device, and the machine gets
 * the water of the other.
 */
class DeclaredRecipesTest {

    private static Expr.Selector wasser() {
        return new Expr.Selector(Expr.Selector.Kind.FLUID, "fluid", "minecraft", "water",
                Span.of(0, 0, 1, 1));
    }

    @Test
    @DisplayName("A station carries device and recipe name")
    void astationCarriesDeviceAndRecipe() {
        String station = DeclaredRecipes.stationFor("brecher", "erz_mahlen");

        assertEquals("brecher", DeclaredRecipes.deviceOf(station));
        assertEquals("erz_mahlen", DeclaredRecipes.recipeOf(station));
    }

    @Test
    @DisplayName("Two recipes at the same device are two stations")
    void twoRecipesAtOnedeviceAreTwoStations() {
        assertNotEquals(DeclaredRecipes.stationFor("brecher", "erz_mahlen"),
                DeclaredRecipes.stationFor("brecher", "erz_waschen"));
    }

    @Test
    @DisplayName("A station from an old world has no recipe name yet")
    void anoldStationHasNorecipeName() {
        // Saved orders from the time before the side ingredients name only
        // the device. They must keep running; they just have no ingredients
        // that want to be filled in.
        assertEquals("brecher", DeclaredRecipes.deviceOf("at:brecher"));
        assertEquals("", DeclaredRecipes.recipeOf("at:brecher"));
    }

    @Test
    @DisplayName("What is not a station yields no device")
    void whatIsNotAstationHasNodevice() {
        assertNull(DeclaredRecipes.deviceOf("minecraft:blasting"));
    }

    @Test
    @DisplayName("A side ingredient grows with the number of runs")
    void anextraGrowsWithTheRuns() {
        // The error this prevents: the ingredients go into the machine for
        // all runs at once, but the water only for one — then a machine
        // stands there with four ores and one bucket.
        DeclaredRecipes.Extra extra = new DeclaredRecipes.Extra(true, 1000, wasser());

        assertEquals(1000, extra.needFor(1));
        assertEquals(4000, extra.needFor(4));
    }

    @Test
    @DisplayName("Without a run nothing is needed")
    void withoutArunNothingIsNeeded() {
        assertEquals(0, new DeclaredRecipes.Extra(true, 1000, wasser()).needFor(0));
    }
}
