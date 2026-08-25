package dev.devpanda.factorynetwork.lang;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Das Projekt eines Controllers als Ordner neben der Welt.
 *
 * <p><b>Damit man es in einem richtigen Editor schreiben kann.</b> Ein Ordner
 * und keine Datei mehr: Ein Projekt aus mehreren {@code .mf}-Dateien ist in
 * VS Code ein Arbeitsbereich, und das ist der ganze Punkt — Übersicht.
 *
 * <p>Die Regel ist in beide Richtungen dieselbe: <b>Wer zuletzt geschrieben
 * hat, gewinnt.</b> Ein Übernehmen im Spiel schreibt den Ordner, ein Speichern
 * im Editor wird im Spiel übernommen.
 *
 * <p>Verglichen wird der <b>Inhalt</b> des ganzen Ordners und nicht der
 * Zeitstempel je Datei. Das löst drei Fälle auf einmal, die mit Zeitstempeln
 * jeder für sich zu behandeln wären: Eine Datei wird angelegt, eine wird
 * gelöscht, eine wird umbenannt — Umbenennen ist von außen nichts anderes als
 * beides zusammen. Und der Fall „zwei Schreibvorgänge in derselben
 * Millisekunde" verschwindet mit.
 *
 * <p><b>Nachgesehen wird im Sekundentakt, nicht überwacht.</b> Ein
 * {@code WatchService} wäre unmittelbar, bräuchte aber einen eigenen Thread,
 * eine Entprellung gegen die Doppelereignisse der Editoren und ein
 * verlässliches Aufräumen beim Weltwechsel.
 */
public final class ProgramFolder {

    private static final String ROOT = "factorynetwork";

    /** So oft wird nachgesehen — zwanzig Ticks sind eine Sekunde. */
    public static final int CHECK_INTERVAL = 20;

    private final Path folder;

    /**
     * Was wir zuletzt selbst geschrieben haben.
     *
     * <p>Ohne diesen Vergleich löst jedes eigene Schreiben beim nächsten
     * Nachsehen ein Übernehmen aus, das wieder schreibt.
     */
    private Map<String, String> written = Map.of();

    private ProgramFolder(Path folder) {
        this.folder = folder;
    }

    /**
     * Der Ordner eines Controllers, oder {@code null}, wenn er sich nicht
     * anlegen lässt.
     *
     * <p>Der Name trägt Dimension und Position: Zwei Controller können in
     * verschiedenen Welten an derselben Stelle stehen.
     */
    public static ProgramFolder of(ServerLevel level, BlockPos pos) {
        try {
            Path root = level.getServer().getWorldPath(LevelResource.ROOT).resolve(ROOT);
            String name = "controller_%s_%d_%d_%d".formatted(
                    level.dimension().location().getPath(),
                    pos.getX(), pos.getY(), pos.getZ());
            Path folder = root.resolve(name);
            Files.createDirectories(folder);
            migrateSingleFile(root, name, folder);
            return at(folder);
        } catch (IOException | RuntimeException failed) {
            // Ohne Ordner läuft alles weiter, nur eben ohne Brücke.
            return null;
        }
    }

    /**
     * Die Brücke zu einem beliebigen Ordner.
     *
     * <p>Getrennt von {@link #of}, weil es zwei Fragen sind: <b>welcher
     * Ordner</b> — dafür braucht es Welt und Position — und <b>was mit dem
     * Ordner geschieht</b>. Nur die zweite ist hier interessant, und nur sie
     * lässt sich ohne laufenden Server vorführen.
     */
    public static ProgramFolder at(Path folder) {
        return new ProgramFolder(folder);
    }

    /**
     * Holt die alte Einzeldatei in den Ordner.
     *
     * <p>Vor dem Projekt lag das Programm als {@code controller_….mf} neben
     * dem Ordner. Sie wird zur {@code main.mf} und verschwindet — sonst
     * stünden zwei Wahrheiten nebeneinander, und die Brücke sähe nur eine.
     */
    private static void migrateSingleFile(Path root, String name, Path folder)
            throws IOException {
        Path old = root.resolve(name + ".mf");
        if (!Files.exists(old)) {
            return;
        }
        Path target = folder.resolve(Project.MAIN);
        if (!Files.exists(target)) {
            Files.writeString(target, Files.readString(old, StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
        Files.delete(old);
    }

    public Path path() {
        return folder;
    }

    /**
     * Schreibt das Projekt hin und merkt sich, dass wir es waren.
     *
     * <p>Dateien, die es im Projekt nicht mehr gibt, verschwinden auch im
     * Ordner: Ein gelöschtes Programmstück, das als Datei liegen bleibt,
     * käme beim nächsten Nachsehen zurück.
     */
    public void write(Project project) {
        try {
            for (Map.Entry<String, String> file : project.files().entrySet()) {
                Path path = folder.resolve(file.getKey());
                // Der Ordner einer Datei entsteht mit ihr. Ohne das schlüge
                // die erste Datei in einem neuen Ordner fehl, und zwar still.
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (!Files.exists(path)
                        || !Files.readString(path, StandardCharsets.UTF_8)
                                .equals(file.getValue())) {
                    Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
                }
            }
            for (String name : listNames()) {
                if (!project.files().containsKey(name)) {
                    Files.deleteIfExists(folder.resolve(name));
                }
            }
            written = Map.copyOf(project.files());
        } catch (IOException ignored) {
            // Ein schreibgeschützter Weltordner ist kein Fehler des Netzes.
        }
    }

    /**
     * Sieht nach, ob jemand von außen geschrieben hat.
     *
     * <p>Eine angelegte Datei gehört ab jetzt dazu, eine gelöschte ist weg.
     * <b>Löschen ist eine Absicht</b> — bei einer einzelnen Datei kam sie
     * beim nächsten Schreiben zurück, weil man sie kaum absichtlich löscht;
     * in einem Projekt löscht man ein Programmstück, das man nicht mehr
     * will.
     *
     * @param current was der Controller gerade hält
     * @return das neue Projekt, oder {@code null}, wenn nichts zu tun ist
     */
    public Project poll(Project current) {
        Map<String, String> found = read();
        if (found == null || found.isEmpty()) {
            return null;
        }
        if (found.equals(written) || found.equals(current.files())) {
            // Unsere eigene Schrift, oder eine Änderung, die nichts ändert.
            written = found;
            return null;
        }
        written = found;
        return new Project(found);
    }

    /**
     * Wie tief unter dem Ordner nachgesehen wird.
     *
     * <p>Weiter, als ein gültiger Name reichen kann: Ein Name hat höchstens
     * sechsundneunzig Zeichen, und jede Ebene kostet zwei. Die Zahl ist die
     * Bremse gegen einen Ordner, in den jemand einen Verweiskreis gelegt hat
     * — nicht die Grenze für den Menschen.
     */
    private static final int MAX_DEPTH = 16;

    /**
     * Alle gültigen Dateinamen im Ordner, mitsamt Unterordnern.
     *
     * <p><b>Der Name ist der Pfad unter dem Ordner, mit Schrägstrichen.</b>
     * Auf Windows liefert {@code relativize} den Rückstrich, und ein
     * {@code erz\brecher.mf} entspricht keinem {@code erz/brecher.mf} im
     * Projekt: Die Brücke sähe eine fremde Datei und zugleich eine fehlende
     * und schriebe im Sekundentakt zwei Wahrheiten gegeneinander.
     */
    private List<String> listNames() throws IOException {
        try (Stream<Path> entries = Files.walk(folder, MAX_DEPTH)) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> folder.relativize(path).toString().replace('\\', '/'))
                    .filter(Project::isValidName)
                    .sorted()
                    .toList();
        }
    }

    /**
     * Liest den Ordner, oder {@code null}, wenn er sich nicht lesen lässt.
     *
     * <p>Dateien mit einem Namen, den das Projekt nicht erlaubt, werden
     * übergangen. Eine {@code Notizen.txt} im Ordner ist kein Programmstück,
     * und ein {@code Erz/Gross.mf} mit Großbuchstaben auch nicht.
     *
     * <p>Öffentlich für die Prüfung: Der Weg hin und zurück ist die eine
     * Sache, die an dieser Klasse schiefgehen kann, und er lässt sich in
     * einem Wegwerfordner vorführen.
     */
    public Map<String, String> read() {
        try {
            Map<String, String> found = new HashMap<>();
            for (String name : listNames()) {
                found.put(name, Files.readString(folder.resolve(name), StandardCharsets.UTF_8));
            }
            return found;
        } catch (IOException ignored) {
            // Halb geschriebene Datei, gesperrt vom Editor — beim nächsten
            // Blick steht sie vollständig da.
            return null;
        }
    }
}
