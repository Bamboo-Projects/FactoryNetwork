package dev.devpanda.factorynetwork.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** The 1.21.5 shape of the same promises. */
public final class Nbt {

    private Nbt() {
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLongOr(key, 0L);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByteOr(key, (byte) 0);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDoubleOr(key, 0.0);
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key).orElse(new int[0]);
    }

    public static byte[] getByteArray(CompoundTag tag, String key) {
        return tag.getByteArray(key).orElse(new byte[0]);
    }

    public static java.util.Set<String> keys(CompoundTag tag) {
        return tag.keySet();
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    public static ListTag getList(CompoundTag tag, String key, int elementType) {
        ListTag list = tag.getListOrEmpty(key);
        for (Tag element : list) {
            if (element.getId() != elementType) {
                return new ListTag();
            }
        }
        return list;
    }

    public static boolean has(CompoundTag tag, String key, int type) {
        Tag found = tag.get(key);
        if (found == null) {
            return false;
        }
        int id = found.getId();
        return id == type || (type == 99 && id >= 1 && id <= 6);
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.getIntArray(key).map(a -> a.length == 4).orElse(false);
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return net.minecraft.core.UUIDUtil.uuidFromIntArray(tag.getIntArray(key).orElseThrow());
    }

    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, net.minecraft.core.UUIDUtil.uuidToIntArray(value));
    }

    public static CompoundTag compoundAt(ListTag list, int index) {
        return list.getCompoundOrEmpty(index);
    }

    public static String stringAt(ListTag list, int index) {
        return list.getStringOr(index, "");
    }

    public static int intAt(ListTag list, int index) {
        return list.getIntOr(index, 0);
    }

    // ---- Block entities: ValueInput and ValueOutput (since 1.21.8) ---------

    /**
     * Everything a block entity was saved with, as one tag.
     *
     * <p>Factory Network writes its block entities as tag trees; since 1.21.8
     * the game hands over a {@code ValueInput} instead. This is the way back to
     * the tree, with the same keys at the top level as before, so a world from
     * 1.21.1 still loads.
     */
    public static CompoundTag whole(net.minecraft.world.level.storage.ValueInput input) {
        @SuppressWarnings("deprecation")
        var all = input.read(com.mojang.serialization.MapCodec.assumeMapUnsafe(CompoundTag.CODEC));
        return all.orElseGet(CompoundTag::new);
    }

    /** The registries a {@code ValueInput} was opened with. */
    @SuppressWarnings("deprecation")
    public static net.minecraft.core.HolderLookup.Provider registries(
            net.minecraft.world.level.storage.ValueInput input) {
        return input.lookup();
    }

    /**
     * The registries for writing.
     *
     * <p>A {@code ValueOutput} does not hand them out; the block entity's level
     * does. Without a level (rare: a block entity saved before it was placed)
     * the running server's, and failing that, none.
     */
    public static net.minecraft.core.HolderLookup.Provider registries(
            net.minecraft.world.level.Level level) {
        if (level != null) {
            return level.registryAccess();
        }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
    }

    // ---- Item stacks as tags ---------------------------------------------

    /** An item stack as a tag, the way {@code ItemStack.save} wrote it. */
    public static Tag saveStack(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.core.HolderLookup.Provider registries) {
        return net.minecraft.world.item.ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack)
                .getOrThrow();
    }

    /** The other way; empty if the tag is no stack any more. */
    public static java.util.Optional<net.minecraft.world.item.ItemStack> parseStack(
            net.minecraft.core.HolderLookup.Provider registries, Tag tag) {
        return net.minecraft.world.item.ItemStack.CODEC
                .parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result();
    }

    /**
     * A container's slots, in the shape of {@code ContainerHelper.saveAllItems}
     * until 1.21.1: a list {@code Items} with a byte {@code Slot} in each stack.
     */
    public static void saveAllItems(CompoundTag tag,
                                    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
                                    net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = (CompoundTag) saveStack(stack, registries);
                entry.putByte("Slot", (byte) slot);
                list.add(entry);
            }
        }
        tag.put("Items", list);
    }

    /** The other way; slots without an entry stay as they are. */
    public static void loadAllItems(CompoundTag tag,
                                    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
                                    net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = tag.getListOrEmpty("Items");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            int slot = entry.getByteOr("Slot", (byte) 0) & 255;
            if (slot < items.size()) {
                items.set(slot, parseStack(registries, entry).orElse(net.minecraft.world.item.ItemStack.EMPTY));
            }
        }
    }
}
