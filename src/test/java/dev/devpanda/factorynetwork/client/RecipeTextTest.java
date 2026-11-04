package dev.devpanda.factorynetwork.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The recipe declaration written from a recipe viewer's slots. */
class RecipeTextTest {

    @Test
    @DisplayName("Inputs, outputs and a name from the first output")
    void declaration() {
        String text = RecipeText.of(
                List.of(new RecipeText.Part(1, "item:iron_ore"), new RecipeText.Part(1000, "fluid:water")),
                List.of(new RecipeText.Part(2, "item:mekanism/dust_iron")));
        assertEquals("""
                recipe dust_iron at maschine {
                    in 1 item:iron_ore
                    in 1000 fluid:water
                    out 2 item:mekanism/dust_iron
                }
                """, text);
    }
}
