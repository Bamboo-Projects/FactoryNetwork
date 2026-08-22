package dev.devpanda.factorynetwork.lang;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Das Programm eines Controllers als Datei neben der Welt.
 *
 * <p><b>Damit man es in einem richtigen Editor schreiben kann.</b> Der
 * Bildschirm im Terminal ist brauchbar geworden, aber er hat kein
 * Mehrfach-Cursor, keine Dateisuche und keine Versionsverwaltung. Wer ein
 * Programm von dreihundert Zeilen pflegt, will VS Code — und dafür muss das
 * Programm eine Datei sein.
 *
 * <p>Die Regel ist einfach und in beide Richtungen dieselbe: <b>Wer zuletzt
 * geschrieben hat, gewinnt.</b> Ein Übernehmen im Spiel schreibt die Datei,
 * ein Speichern im Editor wird im Spiel übernommen. Damit sich das nicht
 * aufschaukelt, merkt sich diese Klasse, was sie selbst geschrieben hat.
 *
 * <p><b>Nachgesehen wird im Sekundentakt, nicht überwacht.</b> Ein
 * {@code WatchService} wäre unmittelbar, bräuchte aber einen eigenen Thread,
 * eine Entprellung gegen die Doppelereignisse der Editoren und ein
 * verlässliches Aufräumen beim Weltwechsel. Eine Sekunde Verzug beim
 * Speichern merkt niemand; ein hängengebliebener Thread je geladener Welt
 * fällt erst nach Stunden auf.
 */
public final class ProgramFile {

    /** Der Ordner neben den Regionsdaten. */
    private static final String FOLDER = "factorynetwork";

    /** So oft wird nachgesehen — zwanzig Ticks sind eine Sekunde. */
    public static final int CHECK_INTERVAL = 20;

    private final Path path;

    /**
     * Der Zeitstempel, den wir zuletzt gesehen und verarbeitet haben.
     *
     * <p>Null heißt „noch nie hingesehen" — dann gilt beim ersten Blick, was
     * in der Datei steht, falls sie sich vom Programm unterscheidet.
     */
    private long seenAt;

    /**
     * Was wir zuletzt selbst geschrieben haben.
     *
     * <p>Ohne diesen Vergleich löst jedes eigene Schreiben beim nächsten
     * Nachsehen ein Übernehmen aus, das wieder schreibt. Der Zeitstempel
     * allein reicht nicht: Manche Editoren schreiben über eine Zwischendatei
     * und benennen um, und dann stimmt er nicht mit dem überein, was wir
     * gesetzt haben.
     */
    private String written;

    private ProgramFile(Path path) {
        this.path = path;
    }

    /**
     * Die Datei für einen Controller, oder {@code null}, wenn der Ordner
     * nicht angelegt werden kann.
     *
     * <p>Der Name trägt Dimension und Position: Ein Netz je Controller, und
     * zwei Controller können in verschiedenen Welten an derselben Stelle
     * stehen. Alles in einem flachen Ordner — Unterordner je Dimension wären
     * eine Verzeichnisebene für eine Datei.
     */
    public static ProgramFile of(ServerLevel level, BlockPos pos) {
        try {
            Path folder = level.getServer().getWorldPath(LevelResource.ROOT).resolve(FOLDER);
            Files.createDirectories(folder);
            String dimension = level.dimension().location().getPath();
            String name = "controller_%s_%d_%d_%d.mf".formatted(
                    dimension, pos.getX(), pos.getY(), pos.getZ());
            return new ProgramFile(folder.resolve(name));
        } catch (IOException | RuntimeException failed) {
            // Ohne Datei läuft alles weiter, nur eben ohne Brücke. Das ist
            // kein Grund, einen Controller nicht zu ticken.
            return null;
        }
    }

    public Path path() {
        return path;
    }

    /**
     * Schreibt das Programm hin und merkt sich, dass wir es waren.
     *
     * <p>Nur bei Änderung: Ein Schreiben ohne neuen Inhalt weckt die
     * Dateiüberwachung des Editors, und der springt dann in eine Datei, an
     * der niemand etwas getan hat.
     */
    public void write(String source) {
        String text = source == null ? "" : source;
        if (text.equals(written) && Files.exists(path)) {
            return;
        }
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
            written = text;
            seenAt = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // Eine schreibgeschützte Welt ist kein Fehler des Netzes.
        }
    }

    /**
     * Sieht nach, ob jemand von außen geschrieben hat.
     *
     * @param current was der Controller gerade hält — damit ein Inhalt, der
     *                sowieso schon gilt, kein Übernehmen auslöst
     * @return der neue Text, oder {@code null}, wenn nichts zu tun ist
     */
    public String poll(String current) {
        try {
            if (!Files.exists(path)) {
                // Wer die Datei löscht, will sie los sein — nicht das
                // Programm. Sie kommt beim nächsten Schreiben wieder.
                written = null;
                seenAt = 0;
                return null;
            }
            long stamp = Files.getLastModifiedTime(path).toMillis();
            if (stamp == seenAt) {
                return null;
            }
            seenAt = stamp;
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.equals(written) || text.equals(current)) {
                // Unsere eigene Schrift, oder eine Änderung, die nichts
                // ändert. Beides ist kein Anlass, das Programm neu zu bauen.
                written = text;
                return null;
            }
            written = text;
            return text;
        } catch (IOException ignored) {
            // Halb geschriebene Datei, gesperrt vom Editor — beim nächsten
            // Blick steht sie vollständig da.
            return null;
        }
    }
}
