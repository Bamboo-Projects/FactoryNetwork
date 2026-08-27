package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Macht aus den Kästen eines Modells die Trefferflächen aller vier
 * Richtungen.
 *
 * <p><b>Warum das nötig ist:</b> Ein waagerecht ausgerichteter Block trägt
 * sein Modell nur einmal — der Blockzustand dreht es. Die Trefferfläche dreht
 * dabei niemand mit. Ein Laufwerk, das nach Osten zeigt, hätte also die
 * Trefferfläche eines, das nach Norden zeigt, und man griffe an drei von vier
 * Wänden daneben.
 *
 * <p>Gedreht wird genau so, wie Minecraft das Modell dreht: {@code y=90} legt
 * die Vorderseite von Norden nach Osten, also {@code (x, z) → (16 - z, x)}.
 * Wer die Formel nachrechnen will, nimmt die Mitte der Vorderseite,
 * {@code (8, 0)}: Sie muss auf {@code (16, 8)} landen.
 */
public final class FacingShapes {

    /**
     * Die Trefferfläche zu einem Satz Kästen, ungedreht.
     *
     * <p>Für die Blöcke, die keine Vorderseite haben — Gateway und
     * Controller. Sie brauchen die Drehung nicht, aber dieselbe Vereinigung
     * von Kästen, und die soll nicht dreimal im Projekt stehen.
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
     * Die vier Trefferflächen zu einem Satz Kästen, der nach Norden zeigt.
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

    /** Ein Kasten, in die Richtung gedreht, in die der Block zeigt. */
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
     * Ein Punkt der Grundfläche, um die Blockmitte gedreht.
     *
     * <p>Sichtbar fürs Paket, damit ein Test die Formel nachrechnen kann,
     * ohne eine {@link VoxelShape} zu bauen — die braucht ein geladenes
     * Minecraft, die Formel nicht.
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
