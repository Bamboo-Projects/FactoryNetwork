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
}
