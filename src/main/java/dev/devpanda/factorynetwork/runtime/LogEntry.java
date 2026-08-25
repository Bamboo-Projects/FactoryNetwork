package dev.devpanda.factorynetwork.runtime;

import net.minecraft.nbt.CompoundTag;

/**
 * Eine Zeile im Protokoll.
 *
 * @param level  wie ernst
 * @param time   wann, in Millisekunden seit 1970 — echte Zeit und nicht
 *               Spielzeit: Wer nachsieht, warum die Anlage heute Nacht stehen
 *               blieb, denkt in Uhrzeiten und nicht in Ticks
 * @param source woher, etwa {@code worker erz_import} oder {@code main.mf} —
 *               <b>die halbe Auskunft</b>. Eine Zeile ohne Absender bedeutet
 *               bei dreißig Workern nichts
 * @param text   was
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
