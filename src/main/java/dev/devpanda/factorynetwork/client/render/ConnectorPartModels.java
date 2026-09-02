package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableLayout;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The twelve models of a connector on a cable face.
 *
 * <p><b>Twelve instead of two rotated ones:</b> six directions times two
 * cable thicknesses. Rotating a single model would require quaternions in the
 * renderer, and whether one of them is correct you only see in the game —
 * generated files, by contrast, can be checked number for number against
 * {@link CableLayout}, and {@code CableLayoutTest} does that.
 *
 * <p>They belong to no block state: a cable block carries its connectors in
 * the BlockEntity, not in the state. That is why they are registered
 * separately and drawn by the {@link CableBusRenderer}.
 */
public final class ConnectorPartModels {

    private static final Map<Direction, ModelResourceLocation> THIN = build("");
    private static final Map<Direction, ModelResourceLocation> DENSE = build("dense_");

    private ConnectorPartModels() {
    }

    private static Map<Direction, ModelResourceLocation> build(String prefix) {
        Map<Direction, ModelResourceLocation> found = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            found.put(side, ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID,
                            "block/" + prefix + "connector_part_" + side.getName())));
        }
        return found;
    }

    /** The model for this cable thickness and this face. */
    public static ModelResourceLocation of(int size, Direction side) {
        return (size >= CableLayout.DENSE ? DENSE : THIN).get(side);
    }

    /** All twelve, for registration when the models are loaded. */
    public static void all(Consumer<ModelResourceLocation> sink) {
        THIN.values().forEach(sink);
        DENSE.values().forEach(sink);
    }
}
