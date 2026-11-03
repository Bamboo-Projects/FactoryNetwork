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

    /**
     * One block, relative to the hall's corner.
     *
     * @param parts what sat in a shelf (server parts in a rack, cells in a
     *              drive), by slot and item. Built from fresh items paid for
     *              like the blocks — an empty cell, never its contents.
     */
    public record Placed(BlockPos at, BlockState state, Optional<CompoundTag> settings, List<Part> parts) {

        public Placed(BlockPos at, BlockState state, Optional<CompoundTag> settings) {
            this(at, state, settings, List.of());
        }

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("at").forGetter(Placed::at),
                BlockState.CODEC.fieldOf("state").forGetter(Placed::state),
                CompoundTag.CODEC.optionalFieldOf("settings").forGetter(Placed::settings),
                Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Placed::parts)
        ).apply(instance, Placed::new));
    }

    /** An item in a shelf slot. */
    public record Part(int slot, Item item) {

        static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(Part::slot),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Part::item)
        ).apply(instance, Part::new));
    }

    /** The key a controller's program travels under in a block's settings. */
    private static final String PROGRAM = "FnProgram";

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
        return capture(hall, Halls.origin(id), tier);
    }

    /** Reads the room whose shell corner is {@code low}. */
    public static Blueprint capture(ServerLevel hall, BlockPos low, HallTier tier) {
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
                    // A controller: its program and nothing else — the stock
                    // and the power it saves along are contents.
                    if (entity instanceof dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity controller) {
                        CompoundTag files = new CompoundTag();
                        controller.project().files().forEach(files::putString);
                        CompoundTag tag = new CompoundTag();
                        tag.put(PROGRAM, files);
                        settings = Optional.of(tag);
                    }
                    List<Part> parts = new ArrayList<>();
                    if (entity instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf) {
                        for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                            if (!shelf.getItem(slot).isEmpty()) {
                                parts.add(new Part(slot, shelf.getItem(slot).getItem()));
                            }
                        }
                    }
                    blocks.add(new Placed(new BlockPos(x, y, z), state, settings, List.copyOf(parts)));
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
            placed.parts().forEach(part -> needed.merge(part.item(), 1, Integer::sum));
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
        build(hall, Halls.origin(id));
    }

    /** Builds into the room whose shell corner is {@code low}. */
    public void build(ServerLevel hall, BlockPos low) {
        for (Placed placed : blocks) {
            hall.setBlock(low.offset(placed.at()), placed.state(), Block.UPDATE_ALL);
        }
        for (Placed placed : blocks) {
            if (!placed.parts().isEmpty()
                    && hall.getBlockEntity(low.offset(placed.at()))
                            instanceof dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity shelf) {
                for (Part part : placed.parts()) {
                    shelf.setItem(part.slot(), new net.minecraft.world.item.ItemStack(part.item()));
                }
            }
        }
        List<Runnable> programs = new ArrayList<>();
        for (Placed placed : blocks) {
            placed.settings().ifPresent(tag -> {
                BlockEntity entity = hall.getBlockEntity(low.offset(placed.at()));
                if (entity instanceof dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity controller
                        && tag.contains(PROGRAM)) {
                    java.util.Map<String, String> files = new java.util.HashMap<>();
                    CompoundTag saved = tag.getCompoundOrEmpty(PROGRAM);
                    for (String name : saved.keySet()) {
                        files.put(name, saved.getStringOr(name, ""));
                    }
                    // After everything else stands: the program is checked
                    // against the network, and a rejected one stays as draft.
                    programs.add(() -> {
                        controller.rebuildNetwork();
                        controller.deploy(new dev.devpanda.factorynetwork.lang.Project(files));
                    });
                    return;
                }
                if (entity != null) {
                    entity.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING, hall.registryAccess(), tag));
                    entity.setChanged();
                    hall.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(),
                            entity.getBlockState(), Block.UPDATE_ALL);
                }
            });
        }
        programs.forEach(Runnable::run);
    }

    public HallTier size() {
        return HallTier.byOrdinal(tier);
    }
}
