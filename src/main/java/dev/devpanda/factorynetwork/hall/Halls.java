package dev.devpanda.factorynetwork.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every factory hall of a world: its number, its size and where its door
 * stands.
 *
 * <p><b>The hall stays where it is; the block is only the door.</b> Breaking
 * the block moves nothing inside: the item carries the number, and wherever it
 * is placed again, it opens the same hall. That is the whole trick behind
 * "break it, place it, everything is still there".
 *
 * <p>Kept in the overworld's data, because the overworld is always loaded and
 * the hall dimension is not.
 */
public final class Halls extends SavedData {

    /** The dimension the halls live in. */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "hall"));

    /** One hall per 1,024 blocks along x: far enough that their chunks never touch. */
    public static final int SPACING = 1024;

    /** The floor of every hall. */
    public static final int FLOOR_Y = 64;

    /**
     * One hall.
     *
     * @param tier the size, as {@link HallTier#ordinal()}
     * @param door where its block was last placed; empty while it lies in an
     *             inventory or was destroyed
     */
    public record Entry(int tier, Optional<GlobalPos> door) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("tier").forGetter(Entry::tier),
                GlobalPos.CODEC.optionalFieldOf("door").forGetter(Entry::door)
        ).apply(instance, Entry::new));

        public HallTier size() {
            return HallTier.byOrdinal(tier);
        }
    }

    private int next;
    private final Map<Integer, Entry> halls;

    private Halls(int next, Map<Integer, Entry> halls) {
        this.next = next;
        this.halls = new HashMap<>(halls);
    }

    private Halls() {
        this(1, Map.of());
    }

    private static final Codec<Halls> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("next").forGetter(halls -> halls.next),
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).fieldOf("halls").forGetter(Halls::byString)
    ).apply(instance, Halls::fromStrings));

    private static final SavedDataType<Halls> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "halls"), Halls::new, CODEC);

    private Map<String, Entry> byString() {
        Map<String, Entry> out = new HashMap<>();
        halls.forEach((id, entry) -> out.put(Integer.toString(id), entry));
        return out;
    }

    private static Halls fromStrings(int next, Map<String, Entry> stored) {
        Map<Integer, Entry> halls = new HashMap<>();
        stored.forEach((id, entry) -> halls.put(Integer.parseInt(id), entry));
        return new Halls(next, halls);
    }

    public static Halls of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** A new hall of this size; its room is built by whoever asks. */
    public int create(HallTier tier) {
        int id = next++;
        halls.put(id, new Entry(tier.ordinal(), Optional.empty()));
        setDirty();
        return id;
    }

    public @Nullable Entry get(int id) {
        return halls.get(id);
    }

    public Map<Integer, Entry> all() {
        return Map.copyOf(halls);
    }

    /** The block of this hall now stands here. */
    public void setDoor(int id, @Nullable GlobalPos door) {
        Entry entry = halls.get(id);
        if (entry != null && !entry.door().equals(Optional.ofNullable(door))) {
            halls.put(id, new Entry(entry.tier(), Optional.ofNullable(door)));
            setDirty();
        }
    }

    // ---- Geometry ---------------------------------------------------------

    /** The lower corner of the hall's shell (a wall block). */
    public static BlockPos origin(int id) {
        return new BlockPos(id * SPACING, FLOOR_Y, 0);
    }

    /** Where someone entering stands: the middle of the floor. */
    public static BlockPos arrival(int id, HallTier tier) {
        return origin(id).offset(tier.size() / 2 + 1, 1, tier.size() / 2 + 1);
    }

    /** Which hall a position in the hall dimension belongs to; 0 for none. */
    public static int hallAt(BlockPos pos) {
        int id = Math.floorDiv(pos.getX(), SPACING);
        return id > 0 ? id : 0;
    }

    /** Whether a position lies inside the room of a hall, walls excluded. */
    public static boolean inside(int id, HallTier tier, BlockPos pos) {
        BlockPos low = origin(id);
        int n = tier.size();
        return pos.getX() > low.getX() && pos.getX() <= low.getX() + n
                && pos.getY() > low.getY() && pos.getY() <= low.getY() + n
                && pos.getZ() > low.getZ() && pos.getZ() <= low.getZ() + n;
    }

    /**
     * Builds the walls of a hall, if they are not there yet.
     *
     * <p>Only the shell: whatever stands inside stays. Called on every entry
     * and cheap when the walls stand — one block is checked first.
     */
    public static void buildRoom(ServerLevel level, int id, HallTier tier,
                                 net.minecraft.world.level.block.state.BlockState wall) {
        BlockPos low = origin(id);
        int n = tier.size() + 1;
        if (level.getBlockState(low).is(wall.getBlock())) {
            return;
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 0; x <= n; x++) {
            for (int y = 0; y <= n; y++) {
                for (int z = 0; z <= n; z++) {
                    boolean shell = x == 0 || x == n || y == 0 || y == n || z == 0 || z == n;
                    if (shell) {
                        at.set(low.getX() + x, low.getY() + y, low.getZ() + z);
                        level.setBlock(at, wall, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
