package dev.devpanda.factorynetwork.client.render;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableLayout;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

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
 *
 * <p>Since 26.1 a model outside the block states is a standalone model with a
 * key of its own; the key is what the renderer asks the model manager for.
 */
public final class ConnectorPartModels {

    private static final Map<Direction, StandaloneModelKey<BlockStateModelPart>> THIN = build("");
    private static final Map<Direction, StandaloneModelKey<BlockStateModelPart>> DENSE = build("dense_");

    private ConnectorPartModels() {
    }

    private static Map<Direction, StandaloneModelKey<BlockStateModelPart>> build(String prefix) {
        Map<Direction, StandaloneModelKey<BlockStateModelPart>> found = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            Identifier model = Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID,
                    "block/" + prefix + "connector_part_" + side.getName());
            found.put(side, new StandaloneModelKey<>(model::toString));
        }
        return found;
    }

    /** The model for this cable thickness and this face. */
    public static StandaloneModelKey<BlockStateModelPart> of(int size, Direction side) {
        return (size >= CableLayout.DENSE ? DENSE : THIN).get(side);
    }

    /** All twelve, for registration when the models are loaded. */
    /** Registers all twelve with the model loading of 26.1. */
    public static void register(ModelEvent.RegisterStandalone event) {
        for (String prefix : new String[] {"", "dense_"}) {
            var keys = prefix.isEmpty() ? THIN : DENSE;
            for (Direction side : Direction.values()) {
                Identifier model = Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID,
                        "block/" + prefix + "connector_part_" + side.getName());
                event.register(keys.get(side), SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
            }
        }
    }
}
