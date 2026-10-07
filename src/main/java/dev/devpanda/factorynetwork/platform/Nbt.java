package dev.devpanda.factorynetwork.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

/**
 * Reading what the mod has written into the world.
 *
 * <p><b>Why not ask the tag directly.</b> Between Minecraft versions the
 * reading side of {@link CompoundTag} changes its shape: where
 * {@code getString} returns a string here, later versions return an optional
 * one, and the typed {@code contains} and {@code getList} lose their second
 * argument. That is one change — and it would land on well over a hundred
 * lines spread over every class that saves something.
 *
 * <p>So everything that reads goes through here, and <b>this class is the
 * only one that differs per version</b>. Each method promises what the
 * original promised on the version the mod was written for, so that a save
 * means the same wherever it is read:
 *
 * <ul>
 *   <li>a missing or wrongly typed entry reads as empty — {@code ""},
 *       {@code 0}, {@code false}, an empty compound, an empty list;</li>
 *   <li>a list is only returned if its elements have the asked type.</li>
 * </ul>
 *
 * <p>Writing stays with the tag: {@code putString} and its siblings have kept
 * their shape.
 */
public final class Nbt {

    private Nbt() {
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getString(key);
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLong(key);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBoolean(key);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByte(key);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDouble(key);
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key);
    }

    public static byte[] getByteArray(CompoundTag tag, String key) {
        return tag.getByteArray(key);
    }

    /** The names of all entries. */
    public static java.util.Set<String> keys(CompoundTag tag) {
        return tag.getAllKeys();
    }

    /** The compound under this key; an empty one if there is none. */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    /**
     * The list under this key, if its elements have this type; otherwise an
     * empty one.
     *
     * @param elementType one of the {@code Tag.TAG_*} constants
     */
    public static ListTag getList(CompoundTag tag, String key, int elementType) {
        return tag.getList(key, elementType);
    }

    /**
     * Whether there is an entry of this type under this key.
     *
     * @param type one of the {@code Tag.TAG_*} constants, {@code TAG_ANY_NUMERIC} included
     */
    public static boolean has(CompoundTag tag, String key, int type) {
        return tag.contains(key, type);
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.hasUUID(key);
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.getUUID(key);
    }

    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.putUUID(key, value);
    }

    /** The compound at this place of a list; an empty one if it is something else. */
    public static CompoundTag compoundAt(ListTag list, int index) {
        return list.getCompound(index);
    }

    /** The string at this place of a list; empty if it is something else. */
    public static String stringAt(ListTag list, int index) {
        return list.getString(index);
    }

    /** The number at this place of a list; zero if it is something else. */
    public static int intAt(ListTag list, int index) {
        return list.getInt(index);
    }
}
