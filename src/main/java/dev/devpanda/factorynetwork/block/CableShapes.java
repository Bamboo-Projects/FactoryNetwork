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
 * What you bump into.
 *
 * <p>The same numbers as in the model script {@code tools/assets.py} — they
 * must match, otherwise you grab beside what you see. Carrying them here a
 * second time is the price for Minecraft keeping models and hitboxes apart; a
 * test watches over it.
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

    /** The arm from the block edge in to the core. */
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

    /** The core together with the arms that are actually connected. */
    public static VoxelShape whole(int size, List<Direction> connections) {
        return whole(size, connections, List.of());
    }

    /**
     * Core, arms, and the connectors on the faces.
     *
     * <p>A connector is two boxes: the plate on the block face and the stalk to
     * the core. Without the stalk the plate would float as soon as the cable is
     * thin — on the dense one it meets the sheath anyway, and then there is
     * none.
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

    /**
     * A holder without a cable: only the plates of its connectors.
     *
     * <p>No core, no arms — it holds no cable. What remains is thin and hard to
     * hit, and that is honest: there hangs a plate on a wall and nothing else.
     */
    public static VoxelShape holder(int size, Collection<Direction> parts) {
        VoxelShape shape = Shapes.empty();
        for (Direction direction : parts) {
            shape = Shapes.join(shape, part(size, direction), BooleanOp.OR);
        }
        return shape;
    }

    /**
     * The plate of a connector on this face.
     *
     * <p>Only the plate: the stretch to the core is covered by the arm that the
     * cable grows to every face with a connector.
     *
     * <p>{@code size} no longer matters and yet stays — the plate is the same
     * at both cable thicknesses, and the callers should not have to remember
     * that.
     */
    public static VoxelShape part(int size, Direction facing) {
        int wide = CableLayout.partOffset();
        return slab(facing, 0, CableLayout.PART_DEPTH, wide, 16 - wide);
    }

    /**
     * A box, measured inward from a block face.
     *
     * <p>{@code near} and {@code far} are the distance from the face that
     * {@code facing} points to; {@code lo} and {@code hi} span the two other
     * axes. The same computation produces the models in the model script —
     * {@code CableLayoutTest} holds both together.
     */
    public static VoxelShape slab(Direction facing, double near, double far,
                                  double lo, double hi) {
        double[] box = slabBox(facing, near, far, lo, hi);
        return box(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /** The same numbers as the {@code from}/{@code to} of a model box. */
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
