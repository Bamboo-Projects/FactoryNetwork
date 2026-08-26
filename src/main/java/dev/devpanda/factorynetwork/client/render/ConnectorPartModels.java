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
 * Die zwölf Modelle eines Anschlusses an einer Kabelfläche.
 *
 * <p><b>Zwölf statt zwei gedrehter:</b> Sechs Richtungen mal zwei
 * Kabelstärken. Ein einziges Modell zu drehen verlangte Quaternionen im
 * Renderer, und ob eine davon stimmt, sieht man erst im Spiel — erzeugte
 * Dateien lassen sich dagegen Zahl für Zahl gegen {@link CableLayout} prüfen,
 * und {@code CableLayoutTest} tut das.
 *
 * <p>Sie gehören zu keinem Blockzustand: Ein Kabelblock trägt seine
 * Anschlüsse in der BlockEntity, nicht im Zustand. Deshalb werden sie
 * eigens angemeldet und vom {@link CableBusRenderer} gezeichnet.
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

    /** Das Modell für diese Kabelstärke und diese Fläche. */
    public static ModelResourceLocation of(int size, Direction side) {
        return (size >= CableLayout.DENSE ? DENSE : THIN).get(side);
    }

    /** Alle zwölf, für die Anmeldung beim Laden der Modelle. */
    public static void all(Consumer<ModelResourceLocation> sink) {
        THIN.values().forEach(sink);
        DENSE.values().forEach(sink);
    }
}
