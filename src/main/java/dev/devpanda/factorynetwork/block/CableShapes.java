package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wo die Stränge eines Bündels im Block liegen.
 *
 * <p>Dieselben Zahlen wie im Modellskript {@code tools/assets.py} — sie
 * müssen übereinstimmen, sonst greift man neben das, was man sieht. Sie hier
 * noch einmal zu führen ist der Preis dafür, dass Minecraft Modelle und
 * Trefferflächen getrennt hält; ein Test wacht darüber.
 */
public final class CableShapes {

    private static final Map<Integer, VoxelShape[]> CORES = new HashMap<>();
    private static final Map<Integer, Map<Direction, VoxelShape[]>> ARMS = new HashMap<>();

    static {
        for (int count = 1; count <= 4; count++) {
            int[][] positions = CableLayout.positions(count);
            int size = CableLayout.size(count);
            int depth = CableLayout.depth(count);

            VoxelShape[] cores = new VoxelShape[positions.length];
            Map<Direction, VoxelShape[]> arms = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                arms.put(direction, new VoxelShape[positions.length]);
            }

            for (int index = 0; index < positions.length; index++) {
                int x = positions[index][0];
                int y = positions[index][1];
                cores[index] = box(x, y, depth, x + size, y + size, depth + size);
                for (Direction direction : Direction.values()) {
                    arms.get(direction)[index] = arm(direction, x, y, size, depth);
                }
            }
            CORES.put(count, cores);
            ARMS.put(count, arms);
        }
    }

    private static int clamp(int count) {
        return CableLayout.clamp(count);
    }

    private static VoxelShape box(double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        return Shapes.box(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    /** Der Arm eines Strangs von der Blockkante bis an seinen Kern. */
    private static VoxelShape arm(Direction direction, int x, int y, int size, int depth) {
        int far = 16 - depth;
        return switch (direction) {
            case NORTH -> box(x, y, 0, x + size, y + size, depth);
            case SOUTH -> box(x, y, far, x + size, y + size, 16);
            case WEST -> box(0, y, x, depth, y + size, x + size);
            case EAST -> box(far, y, x, 16, y + size, x + size);
            case DOWN -> box(x, 0, y, x + size, depth, y + size);
            case UP -> box(x, far, y, x + size, 16, y + size);
        };
    }

    /**
     * Die Trefferfläche eines einzelnen Strangs — Kern samt seiner Arme.
     *
     * <p>Darauf zielt der Spieler beim Abbauen. Ohne eigene Flächen je Strang
     * bliebe nur der ganze Block, und ein Bündel ließe sich nur als Ganzes
     * herausbrechen.
     */
    public static VoxelShape strand(int strandCount, int index, List<Direction> connections) {
        int count = clamp(strandCount);
        VoxelShape[] cores = CORES.get(count);
        if (index < 0 || index >= cores.length) {
            return Shapes.empty();
        }
        VoxelShape shape = cores[index];
        Map<Direction, VoxelShape[]> arms = ARMS.get(count);
        for (Direction direction : connections) {
            shape = Shapes.join(shape, arms.get(direction)[index], BooleanOp.OR);
        }
        return shape;
    }

    /** Alle Stränge zusammen — das, woran man hängenbleibt. */
    public static VoxelShape whole(int strandCount, List<Direction> connections) {
        int count = clamp(strandCount);
        VoxelShape shape = Shapes.empty();
        for (int index = 0; index < CORES.get(count).length; index++) {
            shape = Shapes.join(shape, strand(count, index, connections), BooleanOp.OR);
        }
        return shape;
    }

    private CableShapes() {
    }
}
