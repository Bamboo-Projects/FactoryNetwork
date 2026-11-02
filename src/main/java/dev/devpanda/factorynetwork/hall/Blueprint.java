package dev.devpanda.factorynetwork.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What stands in a hall, to build it a second time.
 *
 * <p>Blocks with their state, and for this mod's blocks whose settings we
 * know, the settings too. <b>Never contents:</b> a blueprint that cloned
 * chests with what is in them would be a copy machine. That is also why only
 * a few block entity types are copied, by name, and not "everything without
 * an inventory": a type that gains an inventory later would slip through.
 */
public record Blueprint(int tier, List<Placed> blocks, int skipped) {

    /** One block, relative to the hall's corner. */
    public record Placed(BlockPos at, BlockState state, Optional<CompoundTag> settings) {

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("at").forGetter(Placed::at),
                BlockState.CODEC.fieldOf("state").forGetter(Placed::state),
                CompoundTag.CODEC.optionalFieldOf("settings").forGetter(Placed::settings)
        ).apply(instance, Placed::new));
    }

    public static final Codec<Blueprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tier").forGetter(Blueprint::tier),
            Placed.CODEC.listOf().fieldOf("blocks").forGetter(Blueprint::blocks),
            Codec.INT.optionalFieldOf("skipped", 0).forGetter(Blueprint::skipped)
    ).apply(instance, Blueprint::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Blueprint> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** More than this and the item gets too large to send; a hall of 48 is rarely that full. */
    public static final int MAX_BLOCKS = 20_000;

    /** The block entity types whose settings are copied: no contents, only configuration. */
    private static Set<BlockEntityType<?>> copied() {
        return Set.of(FnBlockEntities.CABLE_BUS.get(), FnBlockEntities.ROUTER.get(),
                FnBlockEntities.DISPLAY.get());
    }

    /** Reads a hall. Returns {@code null} if it holds more than {@link #MAX_BLOCKS}. */
    public static Blueprint capture(ServerLevel hall, int id, HallTier tier) {
        BlockPos low = Halls.origin(id);
        HolderLookup.Provider registries = hall.registryAccess();
        Set<BlockEntityType<?>> copied = copied();
        List<Placed> blocks = new ArrayList<>();
        int skipped = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 1; x <= tier.size(); x++) {
            for (int y = 1; y <= tier.size(); y++) {
                for (int z = 1; z <= tier.size(); z++) {
                    at.set(low.getX() + x, low.getY() + y, low.getZ() + z);
                    BlockState state = hall.getBlockState(at);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getBlock().asItem() == Items.AIR) {
                        // Water, fire, a piston head: nothing to take from a store.
                        skipped++;
                        continue;
                    }
                    if (blocks.size() >= MAX_BLOCKS) {
                        return null;
                    }
                    BlockEntity entity = hall.getBlockEntity(at);
                    Optional<CompoundTag> settings = entity != null && copied.contains(entity.getType())
                            ? Optional.of(entity.saveCustomOnly(registries)) : Optional.empty();
                    blocks.add(new Placed(new BlockPos(x, y, z), state, settings));
                }
            }
        }
        return new Blueprint(tier.ordinal(), List.copyOf(blocks), skipped);
    }

    /** What building it takes, by item. */
    public Map<Item, Integer> materials() {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Placed placed : blocks) {
            needed.merge(placed.state().getBlock().asItem(), 1, Integer::sum);
            // A connector sits on a cable as a part, not as a block of its own.
            placed.settings().ifPresent(tag -> {
                int parts = connectorParts(tag);
                if (parts > 0) {
                    needed.merge(FnItems.CONNECTOR.get(), parts, Integer::sum);
                }
            });
        }
        return needed;
    }

    private static int connectorParts(CompoundTag settings) {
        return settings.getList("Parts").map(net.minecraft.nbt.ListTag::size).orElse(0);
    }

    /** Puts every block into the hall and gives the copied blocks their settings. */
    public void build(ServerLevel hall, int id) {
        BlockPos low = Halls.origin(id);
        for (Placed placed : blocks) {
            hall.setBlock(low.offset(placed.at()), placed.state(), Block.UPDATE_ALL);
        }
        for (Placed placed : blocks) {
            placed.settings().ifPresent(tag -> {
                BlockEntity entity = hall.getBlockEntity(low.offset(placed.at()));
                if (entity != null) {
                    entity.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING, hall.registryAccess(), tag));
                    entity.setChanged();
                    hall.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(),
                            entity.getBlockState(), Block.UPDATE_ALL);
                }
            });
        }
    }

    public HallTier size() {
        return HallTier.byOrdinal(tier);
    }
}
