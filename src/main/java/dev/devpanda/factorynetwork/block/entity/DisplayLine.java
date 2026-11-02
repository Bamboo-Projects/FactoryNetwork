package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.platform.Nbt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One line of a display, the way it goes over the wire.
 *
 * <p>Until items came to the wall, a line was a finished string — and that
 * was the point: the client draws, the server knows the language. An icon
 * cannot hide in a string, so it travels next to it. <b>The rule stays the
 * same:</b> the server decides which item, the client only looks up its
 * model.
 *
 * <p><b>The text is always there</b>, even for a grid. It is what the line
 * says when it cannot be drawn as a grid — and what a test or the log reads.
 *
 * @param text the formatted text, with colour codes
 * @param icon the item drawn in front of the text, or {@code null}
 * @param grid the cells of an {@code items} grid; empty for every other line
 */
public record DisplayLine(String text, @Nullable Item icon, List<Cell> grid) {

    /**
     * How many cells a grid carries at most.
     *
     * <p>A wall of four by four panels at normal size holds about this many.
     * Whatever is beyond it would not be drawn anywhere, and every cell costs
     * a model lookup per frame on the client.
     */
    public static final int GRID_LIMIT = 256;

    /** An item in a grid, with how much of it there is. */
    public record Cell(Item item, long amount) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Cell> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.registry(Registries.ITEM), Cell::item,
                        ByteBufCodecs.VAR_LONG, Cell::amount,
                        Cell::new);
    }

    public DisplayLine {
        grid = List.copyOf(grid);
    }

    /** A line that is only text — everything a display showed before items. */
    public static DisplayLine text(String text) {
        return new DisplayLine(text, null, List.of());
    }

    public boolean isGrid() {
        return !grid.isEmpty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayLine> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(512), DisplayLine::text,
                    ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM)),
                    line -> Optional.ofNullable(line.icon()),
                    Cell.STREAM_CODEC.apply(ByteBufCodecs.list(GRID_LIMIT)), DisplayLine::grid,
                    (text, icon, grid) -> new DisplayLine(text, icon.orElse(null), grid));

    // ---- Saving -----------------------------------------------------------

    private static final String KEY_TEXT = "Text";
    private static final String KEY_ICON = "Icon";
    private static final String KEY_GRID = "Grid";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_AMOUNT = "Amount";

    /**
     * The item by its registry name.
     *
     * <p>Not the item stack with its data: an icon shows the kind, and a
     * name is what survives a removed mod — the line simply loses its icon.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TEXT, text);
        if (icon != null) {
            tag.putString(KEY_ICON, BuiltInRegistries.ITEM.getKey(icon).toString());
        }
        if (!grid.isEmpty()) {
            ListTag cells = new ListTag();
            for (Cell cell : grid) {
                CompoundTag saved = new CompoundTag();
                saved.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(cell.item()).toString());
                saved.putLong(KEY_AMOUNT, cell.amount());
                cells.add(saved);
            }
            tag.put(KEY_GRID, cells);
        }
        return tag;
    }

    public static DisplayLine load(CompoundTag tag) {
        Item icon = itemNamed(Nbt.getString(tag, KEY_ICON));
        ListTag saved = Nbt.getList(tag, KEY_GRID, Tag.TAG_COMPOUND);
        List<Cell> cells = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag cell = Nbt.compoundAt(saved, i);
            Item item = itemNamed(Nbt.getString(cell, KEY_ITEM));
            if (item != null) {
                cells.add(new Cell(item, Nbt.getLong(cell, KEY_AMOUNT)));
            }
        }
        return new DisplayLine(Nbt.getString(tag, KEY_TEXT), icon, cells);
    }

    /** The item by its name, or {@code null} if it does not exist (any more). */
    private static @Nullable Item itemNamed(String name) {
        Identifier id = name.isEmpty() ? null : Identifier.tryParse(name);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getValue(id);
    }
}
