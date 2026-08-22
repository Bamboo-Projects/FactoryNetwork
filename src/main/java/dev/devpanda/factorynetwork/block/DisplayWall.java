package dev.devpanda.factorynetwork.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mehrere Anzeigen nebeneinander sind eine Anzeige.
 *
 * <p>Wer eine Wand aus sechs Tafeln baut, will einen großen Bildschirm und
 * nicht sechsmal denselben kleinen. <b>Also schreibt genau eine Tafel, und
 * sie schreibt über die ganze Fläche.</b>
 *
 * <p>Zusammen gehören Tafeln, die in dieselbe Richtung zeigen und sich in
 * ihrer Ebene berühren. Nicht über Ecken: Eine Tafel an der Nordwand und
 * eine an der Ostwand stoßen zwar aneinander, sind aber zwei Bildschirme —
 * man kann nicht beide gleichzeitig ansehen.
 *
 * <p><b>Eine bekannte Grenze:</b> Gesucht wird über {@code getBlockState},
 * und das liefert für eine nicht geladene Stelle Luft. Eine sehr breite Wand
 * an einer Chunk-Grenze kann deshalb für einen Moment kleiner aussehen, als
 * sie ist — dann wandert die schreibende Tafel, und der Text springt. Zu
 * beheben wäre das nur mit einer Buchführung über Wände, die Chunkladen und
 * Weltwechsel überlebt, und das ist für den Fall zu viel Maschinerie.
 *
 * <p>Die schreibende Tafel ist die <b>unten links</b>, von vorn gesehen. Eine
 * feste Regel, weil sie erklärbar sein muss: „warum steht der Text bei
 * dieser" ist sonst nicht zu beantworten. Für eine Wand mit Loch oder Stufe
 * gilt trotzdem das umschließende Rechteck als Fläche — der Text läuft dann
 * über die Lücke hinweg, und das ist immer noch besser als sechs Texte.
 */
public record DisplayWall(BlockPos anchor, List<BlockPos> members,
                          int columns, int rows, int anchorColumn, int anchorRow) {

    /** So viele Tafeln werden höchstens zu einer Wand — gegen Ausreißer. */
    private static final int MAX_MEMBERS = 256;

    /** Zeigt diese Stelle eine Anzeige in diese Richtung? */
    private static boolean isPanel(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DisplayBlock
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    /**
     * Die Wand, zu der diese Tafel gehört.
     *
     * <p>Eine einzelne Tafel ist eine Wand aus einer — so muss nirgends
     * zwischen „allein" und „in einer Wand" unterschieden werden.
     */
    public static DisplayWall around(BlockGetter level, BlockPos pos, Direction facing) {
        Direction right = DisplayBlock.rightOf(facing);
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> members = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos.immutable());
        seen.add(pos.immutable());

        while (!queue.isEmpty() && members.size() < MAX_MEMBERS) {
            BlockPos current = queue.poll();
            members.add(current);
            for (Direction step : new Direction[] {right, right.getOpposite(),
                    Direction.UP, Direction.DOWN}) {
                BlockPos next = current.relative(step).immutable();
                if (seen.contains(next) || !isPanel(level, next, facing)) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }

        int minColumn = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (BlockPos member : members) {
            int column = columnOf(member, pos, right);
            int row = member.getY() - pos.getY();
            minColumn = Math.min(minColumn, column);
            maxColumn = Math.max(maxColumn, column);
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
        }

        // Unten links, von vorn gesehen: erst die tiefste Reihe, darin die
        // linke Spalte. Bei einer Wand mit Stufe ist das eine Tafel, die es
        // wirklich gibt — die Ecke des umschließenden Rechtecks wäre unter
        // Umständen Luft.
        BlockPos anchor = members.get(0);
        int anchorColumn = columnOf(anchor, pos, right);
        int anchorRow = anchor.getY() - pos.getY();
        for (BlockPos member : members) {
            int column = columnOf(member, pos, right);
            int row = member.getY() - pos.getY();
            if (row < anchorRow || (row == anchorRow && column < anchorColumn)) {
                anchor = member;
                anchorColumn = column;
                anchorRow = row;
            }
        }

        // Die Mitglieder in Leserichtung: oben links zuerst. Danach wird der
        // Name der Wand gesucht, und die Reihenfolge muss feststehen.
        int originColumn = minColumn;
        int originRow = minRow;
        members.sort((a, b) -> {
            int rowA = b.getY() - a.getY();
            if (rowA != 0) {
                return rowA;
            }
            return Integer.compare(columnOf(a, pos, right), columnOf(b, pos, right));
        });

        return new DisplayWall(anchor, List.copyOf(members),
                maxColumn - minColumn + 1, maxRow - minRow + 1,
                anchorColumn - originColumn, anchorRow - originRow);
    }

    /** Wie weit rechts eine Stelle vom Ausgangspunkt liegt, in Blöcken. */
    private static int columnOf(BlockPos pos, BlockPos origin, Direction right) {
        return (pos.getX() - origin.getX()) * right.getStepX()
                + (pos.getZ() - origin.getZ()) * right.getStepZ();
    }

    /** Schreibt diese Tafel für die ganze Wand? */
    public boolean isAnchor(BlockPos pos) {
        return anchor.equals(pos);
    }

    public boolean isSingle() {
        return members.size() == 1;
    }
}
