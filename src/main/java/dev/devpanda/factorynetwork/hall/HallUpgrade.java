package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.platform.Chat;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnComponents;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A hall grows: a larger, still empty hall block clicked onto the hall's
 * block makes the hall that size, and everything inside stays where it is.
 *
 * <p><b>It grows away from its corner.</b> The corner of the shell is fixed
 * ({@link Halls#origin}); the three walls opposite move out. Nothing stands
 * there — halls are 1,024 blocks apart. The old far walls are taken out and
 * become room, the new ones go up.
 *
 * <p><b>Ports on the walls that move go with them</b>, to the same spot on
 * the new wall; a cable that ended at the old spot has to be laid on to it.
 * Ports on the three walls at the corner stay where they are.
 *
 * <p>The smaller block comes back to the player, empty: the upgrade costs
 * the larger block, not both.
 */
final class HallUpgrade {

    private HallUpgrade() {
    }

    /** Whether this held stack would grow this hall; nothing happens yet. */
    static boolean applies(ItemStack held, HallBlockEntity door) {
        return held.getItem() instanceof HallItem
                && ((HallBlock) ((HallItem) held.getItem()).getBlock()).tier().ordinal() > door.tier().ordinal()
                && door.hall() != HallBlockEntity.NONE
                && !held.has(FnComponents.HALL.get());
    }

    /** Grows the hall of this door to the size of the held block. */
    static void grow(ServerLevel level, BlockPos pos, HallBlockEntity door, ItemStack held, Player player) {
        HallTier from = door.tier();
        HallTier to = ((HallBlock) ((HallItem) held.getItem()).getBlock()).tier();
        int id = door.hall();
        Halls halls = Halls.of(level.getServer());
        Halls.Entry entry = halls.get(id);
        if (entry == null || !entry.door().equals(java.util.Optional.of(GlobalPos.of(level.dimension(), pos)))) {
            Chat.tell(player, Component.translatable("message.factorynetwork.hall.duplicate"), true);
            return;
        }
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        if (hall == null) {
            return;
        }

        // The block first: replacing it releases the old tickets and the
        // door entry (preRemoveSideEffects); both are taken anew below.
        level.setBlockAndUpdate(pos, FnBlocks.HALLS.get(to).get().defaultBlockState());
        HallBlockEntity grown = (HallBlockEntity) level.getBlockEntity(pos);
        grown.setHall(id);
        halls.setTier(id, to);
        halls.setDoor(id, GlobalPos.of(level.dimension(), pos));

        growRoom(hall, id, from, to);

        HallLoading.hold(grown, true);
        grown.announce();
        if (!player.isCreative()) {
            held.shrink(1);
            ItemStack smaller = new ItemStack(FnItems.HALLS.get(from).get());
            if (!player.getInventory().add(smaller)) {
                player.drop(smaller, false);
            }
        }
        Chat.tell(player, Component.translatable("message.factorynetwork.hall.grown",
                to.size(), to.size(), to.size()), true);
    }

    /**
     * Moves the three far walls out and builds the new shell. Inside the old
     * room nothing is touched.
     */
    static void growRoom(ServerLevel hall, int id, HallTier from, HallTier to) {
        BlockPos low = Halls.origin(id);
        int oldFar = from.size() + 1;
        int newFar = to.size() + 1;
        BlockState wall = FnBlocks.HALL_WALL.get().defaultBlockState();
        BlockState port = FnBlocks.HALL_PORT.get().defaultBlockState();

        // Ports on the far walls: where they go on the new wall.
        List<BlockPos[]> moves = new ArrayList<>();
        for (BlockPos at : HallPorts.of(id)) {
            int x = at.getX() - low.getX();
            int y = at.getY() - low.getY();
            int z = at.getZ() - low.getZ();
            int nx = x == oldFar ? newFar : x;
            int ny = y == oldFar ? newFar : y;
            int nz = z == oldFar ? newFar : z;
            if (nx != x || ny != y || nz != z) {
                moves.add(new BlockPos[] {at.immutable(), low.offset(nx, ny, nz)});
            }
        }

        // The old far walls become room.
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int a = 0; a <= oldFar; a++) {
            for (int b = 0; b <= oldFar; b++) {
                clear(hall, at.set(low.getX() + oldFar, low.getY() + a, low.getZ() + b));
                clear(hall, at.set(low.getX() + a, low.getY() + oldFar, low.getZ() + b));
                clear(hall, at.set(low.getX() + a, low.getY() + b, low.getZ() + oldFar));
            }
        }
        // The new shell, everywhere it is not yet: the walls at the corner
        // grow along, the far ones stand new.
        for (int x = 0; x <= newFar; x++) {
            for (int y = 0; y <= newFar; y++) {
                for (int z = 0; z <= newFar; z++) {
                    boolean shell = x == 0 || x == newFar || y == 0 || y == newFar || z == 0 || z == newFar;
                    if (!shell) {
                        continue;
                    }
                    at.set(low.getX() + x, low.getY() + y, low.getZ() + z);
                    if (!(hall.getBlockState(at).getBlock() instanceof HallWallBlock)) {
                        hall.setBlock(at, wall, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (BlockPos[] move : moves) {
            hall.setBlockAndUpdate(move[1], port);
        }
    }

    /** A wall or port of the old shell gives way; anything else stays. */
    private static void clear(ServerLevel hall, BlockPos at) {
        if (hall.getBlockState(at).getBlock() instanceof HallWallBlock) {
            hall.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
