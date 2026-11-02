package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.platform.Chat;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Going into a hall and out again.
 *
 * <p>Out leads to the hall's block, wherever it stands now. If it stands
 * nowhere — broken and lying in a chest, or blown up — out leads to the
 * player's spawn point, with a message; a hall that keeps its visitor locked
 * in would be a trap.
 */
public final class HallDoor {

    private HallDoor() {
    }

    /** Through the block into its hall. */
    public static void enter(ServerPlayer player, HallBlockEntity door) {
        ServerLevel here = (ServerLevel) door.getLevel();
        if (here == null) {
            return;
        }
        if (here.dimension() == Halls.DIMENSION) {
            Chat.tell(player, Component.translatable("message.factorynetwork.hall.nested"), true);
            return;
        }
        Halls halls = Halls.of(player.level().getServer());
        HallTier tier = door.tier();
        GlobalPos at = GlobalPos.of(here.dimension(), door.getBlockPos());
        int id = door.hall();
        if (id == HallBlockEntity.NONE || halls.get(id) == null) {
            id = halls.create(tier, player.level().getServer().getLevel(Halls.DIMENSION));
            door.setHall(id);
        } else if (otherDoorStands(player, halls.get(id), at, id)) {
            // A copied block, from creative pick-block: two doors to one hall
            // would make "out" a guess.
            Chat.tell(player, Component.translatable("message.factorynetwork.hall.duplicate"), true);
            return;
        }
        halls.setDoor(id, at);
        // A new hall, or a door that moved: from now on this block holds it.
        HallLoading.hold(door, true);

        ServerLevel hall = player.level().getServer().getLevel(Halls.DIMENSION);
        if (hall == null) {
            return;
        }
        Halls.Entry entry = halls.get(id);
        Halls.buildRoom(hall, id, entry.size(), FnBlocks.HALL_WALL.get().defaultBlockState());
        BlockPos arrival = Halls.arrival(id, entry.size());
        player.teleport(new TeleportTransition(hall, Vec3.atBottomCenterOf(arrival), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
    }

    /** From inside a hall back to its block. */
    public static void leave(ServerPlayer player, BlockPos inside) {
        Halls halls = Halls.of(player.level().getServer());
        int id = Halls.hallAt(inside);
        Halls.Entry entry = halls.get(id);
        GlobalPos door = entry == null ? null : entry.door().orElse(null);
        ServerLevel level = door == null ? null : player.level().getServer().getLevel(door.dimension());
        if (level != null && level.getBlockEntity(door.pos()) instanceof HallBlockEntity block
                && block.hall() == id) {
            BlockPos stand = standBeside(level, door.pos());
            player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(stand), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
            return;
        }
        Chat.tell(player, Component.translatable("message.factorynetwork.hall.no_door"), false);
        player.teleport(player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
    }

    private static boolean otherDoorStands(ServerPlayer player, Halls.Entry entry, GlobalPos here, int id) {
        GlobalPos door = entry.door().orElse(null);
        if (door == null || door.equals(here)) {
            return false;
        }
        ServerLevel level = player.level().getServer().getLevel(door.dimension());
        return level != null && level.isLoaded(door.pos())
                && level.getBlockEntity(door.pos()) instanceof HallBlockEntity other && other.hall() == id;
    }

    /** A free spot next to the door, so leaving does not put anyone inside a wall. */
    private static BlockPos standBeside(ServerLevel level, BlockPos door) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = door.relative(direction);
            if (free(level, feet) && free(level, feet.above())) {
                return feet;
            }
        }
        return door.above();
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
