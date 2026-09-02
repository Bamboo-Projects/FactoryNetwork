package dev.devpanda.factorynetwork.runtime;

import net.minecraft.nbt.CompoundTag;

/**
 * One line in the log.
 *
 * @param level  how serious
 * @param time   when, in milliseconds since 1970 — real time and not game
 *               time: whoever checks why the plant stopped last night thinks
 *               in clock times, not in ticks
 * @param source where from, e.g. {@code worker erz_import} or {@code main.mf} —
 *               <b>half the information</b>. A line without a sender means
 *               nothing when there are thirty workers
 * @param text   what
 */
public record LogEntry(LogLevel level, long time, String source, String text) {

    private static final String KEY_LEVEL = "Level";
    private static final String KEY_TIME = "Time";
    private static final String KEY_SOURCE = "Source";
    private static final String KEY_TEXT = "Text";

    public LogEntry {
        level = level == null ? LogLevel.INFO : level;
        source = source == null ? "" : source;
        text = text == null ? "" : text;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_LEVEL, level.key());
        tag.putLong(KEY_TIME, time);
        tag.putString(KEY_SOURCE, source);
        tag.putString(KEY_TEXT, text);
        return tag;
    }

    public static LogEntry read(CompoundTag tag) {
        LogLevel level = LogLevel.of(tag.getString(KEY_LEVEL));
        return new LogEntry(level == null ? LogLevel.INFO : level,
                tag.getLong(KEY_TIME), tag.getString(KEY_SOURCE), tag.getString(KEY_TEXT));
    }
}
