package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Woran man hängenbleibt.
 *
 * <p>Dieselben Zahlen wie im Modellskript {@code tools/assets.py} — sie müssen
 * übereinstimmen, sonst greift man neben das, was man sieht. Sie hier noch
 * einmal zu führen ist der Preis dafür, dass Minecraft Modelle und
 * Trefferflächen getrennt hält; ein Test wacht darüber.
 */
public final class CableShapes {

    private static final Map<Integer, VoxelShape> CORES = new HashMap<>();
    private static final Map<Integer, Map<Direction, VoxelShape>> ARMS = new HashMap<>();

    static {
        for (int size : new int[] {CableLayout.THIN, CableLayout.DENSE}) {
            int lo = CableLayout.offset(size);
            int hi = CableLayout.far(size);
            CORES.put(size, box(lo, lo, lo, hi, hi, hi));

            Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                arms.put(direction, arm(direction, lo, hi));
            }
            ARMS.put(size, arms);
        }
    }

    private static VoxelShape box(double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        return Shapes.box(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    /** Der Arm von der Blockkante bis an den Kern. */
    private static VoxelShape arm(Direction direction, int lo, int hi) {
        return switch (direction) {
            case NORTH -> box(lo, lo, 0, hi, hi, lo);
            case SOUTH -> box(lo, lo, hi, hi, hi, 16);
            case WEST -> box(0, lo, lo, lo, hi, hi);
            case EAST -> box(hi, lo, lo, 16, hi, hi);
            case DOWN -> box(lo, 0, lo, hi, lo, hi);
            case UP -> box(lo, hi, lo, hi, 16, hi);
        };
    }

    /** Kern samt der Arme, die wirklich verbunden sind. */
    public static VoxelShape whole(int size, List<Direction> connections) {
        return whole(size, connections, List.of());
    }

    /**
     * Kern, Arme und die Anschlüsse an den Flächen.
     *
     * <p>Ein Anschluss ist zwei Kästen: die Platte an der Blockfläche und der
     * Stiel zum Kern. Ohne den Stiel schwebte die Platte, sobald das Kabel
     * dünn ist — beim dichten stößt sie ohnehin an den Mantel, und dann gibt
     * es keinen.
     */
    public static VoxelShape whole(int size, List<Direction> connections,
                                   Collection<Direction> parts) {
        VoxelShape shape = CORES.getOrDefault(size, CORES.get(CableLayout.THIN));
        Map<Direction, VoxelShape> arms = ARMS.getOrDefault(size, ARMS.get(CableLayout.THIN));
        for (Direction direction : connections) {
            shape = Shapes.join(shape, arms.get(direction), BooleanOp.OR);
        }
        for (Direction direction : parts) {
            shape = Shapes.join(shape, part(size, direction), BooleanOp.OR);
        }
        return shape;
    }

    /** Platte und Stiel eines Anschlusses an dieser Fläche. */
    public static VoxelShape part(int size, Direction facing) {
        int wide = CableLayout.partOffset();
        VoxelShape plate = slab(facing, 0, CableLayout.PART_DEPTH, wide, 16 - wide);
        int stem = CableLayout.stemLength(size);
        if (stem == 0) {
            return plate;
        }
        return Shapes.join(plate,
                slab(facing, CableLayout.PART_DEPTH, CableLayout.offset(size),
                        CableLayout.offset(size), CableLayout.far(size)),
                BooleanOp.OR);
    }

    /**
     * Ein Kasten, gemessen von einer Blockfläche nach innen.
     *
     * <p>{@code near} und {@code far} sind der Abstand von der Fläche, zu der
     * {@code facing} zeigt; {@code lo} und {@code hi} spannen die beiden
     * anderen Achsen. Dieselbe Rechnung erzeugt im Modellskript die Modelle —
     * {@code CableLayoutTest} hält beide zusammen.
     */
    public static VoxelShape slab(Direction facing, double near, double far,
                                  double lo, double hi) {
        double[] box = slabBox(facing, near, far, lo, hi);
        return box(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /** Dieselben Zahlen als {@code from}/{@code to} eines Modellkastens. */
    public static double[] slabBox(Direction facing, double near, double far,
                                   double lo, double hi) {
        return switch (facing) {
            case NORTH -> new double[] {lo, lo, near, hi, hi, far};
            case SOUTH -> new double[] {lo, lo, 16 - far, hi, hi, 16 - near};
            case WEST -> new double[] {near, lo, lo, far, hi, hi};
            case EAST -> new double[] {16 - far, lo, lo, 16 - near, hi, hi};
            case DOWN -> new double[] {lo, near, lo, hi, far, hi};
            case UP -> new double[] {lo, 16 - far, lo, hi, 16 - near, hi};
        };
    }

    private CableShapes() {
    }
}
