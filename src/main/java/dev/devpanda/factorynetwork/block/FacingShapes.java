package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the boxes of a model into the hitboxes for all four directions.
 *
 * <p><b>Why this is needed:</b> A horizontally aligned block carries its
 * model only once — the block state rotates it. Nothing rotates the hitbox
 * along with it. A drive facing east would therefore have the hitbox of one
 * facing north, and you would grab past it on three of four walls.
 *
 * <p>The rotation is exactly the one Minecraft applies to the model:
 * {@code y=90} moves the front from north to east, so {@code (x, z) → (16 - z, x)}.
 * Anyone wanting to check the formula takes the centre of the front,
 * {@code (8, 0)}: it must land on {@code (16, 8)}.
 */
public final class FacingShapes {

    /**
     * The hitbox for a set of boxes, unrotated.
     *
     * <p>For the blocks that have no front — gateway and controller. They do
     * not need the rotation, but the same union of boxes, and that should not
     * appear three times in the project.
     */
    public static VoxelShape whole(List<int[]> boxes) {
        VoxelShape shape = Shapes.empty();
        for (int[] box : boxes) {
            shape = Shapes.join(shape, Shapes.box(
                    box[0] / 16.0, box[1] / 16.0, box[2] / 16.0,
                    box[3] / 16.0, box[4] / 16.0, box[5] / 16.0), BooleanOp.OR);
        }
        return shape.optimize();
    }

    /**
     * The four hitboxes for a set of boxes that faces north.
     */
    public static Map<Direction, VoxelShape> horizontal(List<int[]> boxes) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int[] box : boxes) {
                shape = Shapes.join(shape, turn(box, facing), BooleanOp.OR);
            }
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    /** A box, rotated in the direction the block faces. */
    private static VoxelShape turn(int[] box, Direction facing) {
        double[] near = spin(box[0], box[2], facing);
        double[] far = spin(box[3], box[5], facing);
        return Shapes.box(
                Math.min(near[0], far[0]) / 16.0, box[1] / 16.0,
                Math.min(near[1], far[1]) / 16.0,
                Math.max(near[0], far[0]) / 16.0, box[4] / 16.0,
                Math.max(near[1], far[1]) / 16.0);
    }

    /**
     * A point of the footprint, rotated about the block's centre.
     *
     * <p>Package-visible so that a test can check the formula without building
     * a {@link VoxelShape} — that needs a loaded Minecraft, the formula does
     * not.
     */
    static double[] spin(int x, int z, Direction facing) {
        return switch (facing) {
            case EAST -> new double[] {16 - z, x};
            case SOUTH -> new double[] {16 - x, 16 - z};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    private FacingShapes() {
    }
}
