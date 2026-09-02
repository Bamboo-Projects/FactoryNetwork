package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.lang.Definitions;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.BlockPos;

/**
 * Where a Ctrl+click on a name leads.
 *
 * <p>Two destinations, and the name decides which one applies: if it lives in
 * the program, the question is "where is this declared" — if it lives in the
 * world, it is "which block is this", and a marker answers that.
 *
 * <p><b>In one place, because both windows need it.</b> At first it lived only
 * in the standalone window; in the terminal's tab the same gesture led
 * nowhere. That only came to light once the tooltip announced it there — a
 * hint that points to something which does not exist is worse than no hint.
 */
public final class NameJump {

    /**
     * A destination.
     *
     * <p>Exactly one of the two fields is set.
     *
     * @param inCode  where the name is declared, or {@code null}
     * @param inWorld where the block sits, or {@code null}
     */
    public record Jump(Definitions.Location inCode, BlockPos inWorld) {
    }

    private NameJump() {
    }

    /**
     * Where this name leads, or {@code null}.
     *
     * <p>The declaration in the program takes precedence: someone searching
     * for a name in the code usually means its declaration — the spot in the
     * world is already in the tooltip anyway.
     */
    public static Jump resolve(String word, Project project) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        var declared = Definitions.find(project, word);
        if (declared.isPresent()) {
            return new Jump(declared.get(), null);
        }
        BlockPos place = ClientNetworkState.placeOf(word);
        return place == null ? null : new Jump(null, place);
    }
}
