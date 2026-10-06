package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The version of what a block entity writes into the world.
 *
 * <p><b>Why a number at all.</b> The layout has changed several times without
 * one, and each time the reader had to guess the age of a save from which
 * keys happened to be present. That works until two layouts share their keys
 * and differ in meaning.
 *
 * <p><b>A save without the number is version 0.</b> Every world written
 * before this class existed reads that way, and all of them are still valid.
 *
 * <p><b>A save from a newer mod version is not ours to rewrite.</b> Whoever
 * loads a world with an older jar must get it back unchanged; see
 * {@link #isNewer(int)}.
 */
public final class SaveVersion {

    /** The layout this build writes. Raise it whenever a reader would misread an older save. */
    public static final int CURRENT = 1;

    private static final String KEY = "DataVersion";

    private SaveVersion() {
    }

    /** The version a save was written with; 0 for saves older than the number. */
    public static int read(CompoundTag tag) {
        return tag.contains(KEY, Tag.TAG_INT) ? tag.getInt(KEY) : 0;
    }

    /** Stamps a save with the layout this build writes. */
    public static void write(CompoundTag tag) {
        tag.putInt(KEY, CURRENT);
    }

    /** Whether a save comes from a build that knows more than this one. */
    public static boolean isNewer(int version) {
        return version > CURRENT;
    }
}
