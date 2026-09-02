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
 * Several displays side by side are one display.
 *
 * <p>Anyone who builds a wall from six panels wants one large screen and not
 * the same small one six times over. <b>So exactly one panel writes, and it
 * writes across the whole surface.</b>
 *
 * <p>Panels belong together when they face the same direction and touch
 * within their plane. Not around corners: a panel on the north wall and one
 * on the east wall do meet, but they are two screens — you cannot look at
 * both at once.
 *
 * <p><b>A known limit:</b> the search goes through {@code getBlockState}, and
 * that returns air for a location that is not loaded. A very wide wall at a
 * chunk boundary can therefore look smaller for a moment than it is — then
 * the writing panel moves, and the text jumps. Fixing that would take
 * bookkeeping over walls that survives chunk loading and world changes, and
 * that is too much machinery for the case.
 *
 * <p>The writing panel is the <b>bottom left</b> one, seen from the front. A
 * fixed rule, because it has to be explainable: otherwise "why does the text
 * sit at this one" cannot be answered. For a wall with a hole or a step the
 * enclosing rectangle still counts as the surface — the text then runs across
 * the gap, and that is still better than six texts.
 */
public record DisplayWall(BlockPos anchor, List<BlockPos> members,
                          int columns, int rows, int anchorColumn, int anchorRow) {

    /** At most this many panels become one wall — a guard against runaways. */
    private static final int MAX_MEMBERS = 256;

    /** Does this location show a display facing this direction? */
    private static boolean isPanel(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DisplayBlock
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    /**
     * The wall this panel belongs to.
     *
     * <p>A single panel is a wall of one — so nowhere is there any need to
     * distinguish between "alone" and "in a wall".
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

        // Bottom left, seen from the front: first the lowest row, and within
        // it the left column. For a wall with a step this is a panel that
        // really exists — the corner of the enclosing rectangle might under
        // some circumstances be air.
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

        // The members in reading order: top left first. The wall's name is
        // looked up afterwards, and the order has to be fixed.
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

    /** How far to the right a location sits from the origin, in blocks. */
    private static int columnOf(BlockPos pos, BlockPos origin, Direction right) {
        return (pos.getX() - origin.getX()) * right.getStepX()
                + (pos.getZ() - origin.getZ()) * right.getStepZ();
    }

    /** Does this panel write for the whole wall? */
    public boolean isAnchor(BlockPos pos) {
        return anchor.equals(pos);
    }

    public boolean isSingle() {
        return members.size() == 1;
    }
}
