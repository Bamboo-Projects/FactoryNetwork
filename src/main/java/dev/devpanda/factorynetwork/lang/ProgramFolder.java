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
 * A controller's project as a folder next to the world.
 *
 * <p><b>So that you can write it in a real editor.</b> A folder and no longer a
 * file: a project of several {@code .mf} files is a workspace in VS Code, and
 * that is the whole point — overview.
 *
 * <p>The rule is the same in both directions: <b>whoever wrote last wins.</b>
 * An adoption in the game writes the folder, a save in the editor is adopted in
 * the game.
 *
 * <p>What is compared is the <b>content</b> of the whole folder and not the
 * timestamp per file. That resolves three cases at once that would each have to
 * be handled separately with timestamps: a file is created, one is deleted, one
 * is renamed — a rename, seen from outside, is nothing other than both
 * together. And the case of "two writes in the same millisecond" goes away with
 * them.
 *
 * <p><b>Checked every second, not watched.</b> A {@code WatchService} would be
 * immediate, but would need its own thread, a debounce against the editors'
 * double events, and reliable cleanup on a world change.
 */
public final class ProgramFolder {

    private static final String ROOT = "factorynetwork";

    /** How often it is checked — twenty ticks are one second. */
    public static final int CHECK_INTERVAL = 20;

    private final Path folder;

    /**
     * What we last wrote ourselves.
     *
     * <p>Without this comparison, every write of our own triggers an adoption
     * on the next check, which writes again.
     */
    private Map<String, String> written = Map.of();

    private ProgramFolder(Path folder) {
        this.folder = folder;
    }

    /**
     * A controller's folder, or {@code null} if it cannot be created.
     *
     * <p>The name carries dimension and position: two controllers can stand at
     * the same spot in different worlds.
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
            // Without a folder everything keeps running, just without the bridge.
            return null;
        }
    }

    /**
     * The bridge to an arbitrary folder.
     *
     * <p>Separate from {@link #of}, because these are two questions: <b>which
     * folder</b> — for that you need world and position — and <b>what happens
     * to the folder</b>. Only the second is of interest here, and only it can
     * be demonstrated without a running server.
     */
    public static ProgramFolder at(Path folder) {
        return new ProgramFolder(folder);
    }

    /**
     * Pulls the old single file into the folder.
     *
     * <p>Before the project, the program lay as {@code controller_….mf} next to
     * the folder. It becomes the {@code main.mf} and disappears — otherwise two
     * truths would stand side by side, and the bridge would see only one.
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
     * Writes the project out and remembers that it was us.
     *
     * <p>Files that no longer exist in the project disappear from the folder
     * too: a deleted program piece that stays lying around as a file would come
     * back on the next check.
     */
    public void write(Project project) {
        try {
            for (Map.Entry<String, String> file : project.files().entrySet()) {
                Path path = folder.resolve(file.getKey());
                // A file's folder comes into being with it. Without this the
                // first file in a new folder would fail, and silently at that.
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
            // A read-only world folder is not an error of the network.
        }
    }

    /**
     * Checks whether someone has written from outside.
     *
     * <p>A created file belongs from now on, a deleted one is gone.
     * <b>Deleting is an intent</b> — with a single file it came back on the
     * next write, because one hardly deletes it on purpose; in a project one
     * deletes a program piece that one no longer wants.
     *
     * @param current what the controller currently holds
     * @return the new project, or {@code null} if there is nothing to do
     */
    public Project poll(Project current) {
        Map<String, String> found = read();
        if (found == null || found.isEmpty()) {
            return null;
        }
        if (found.equals(written) || found.equals(current.files())) {
            // Our own writing, or a change that changes nothing.
            written = found;
            return null;
        }
        written = found;
        return new Project(found);
    }

    /**
     * How deep below the folder the check goes.
     *
     * <p>Deeper than a valid name can reach: a name has at most ninety-six
     * characters, and each level costs two. The number is the brake against a
     * folder into which someone has laid a reference cycle — not the limit for
     * the human.
     */
    private static final int MAX_DEPTH = 16;

    /**
     * All valid file names in the folder, including subfolders.
     *
     * <p><b>The name is the path below the folder, with forward slashes.</b> On
     * Windows {@code relativize} yields the backslash, and an
     * {@code erz\brecher.mf} does not match any {@code erz/brecher.mf} in the
     * project: the bridge would see a foreign file and a missing one at the same
     * time and would write two truths against each other every second.
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
     * Reads the folder, or {@code null} if it cannot be read.
     *
     * <p>Files with a name the project does not allow are skipped. A
     * {@code Notizen.txt} in the folder is not a program piece, and neither is
     * an {@code Erz/Gross.mf} with capital letters.
     *
     * <p>Public for testing: the round trip is the one thing that can go wrong
     * in this class, and it can be demonstrated in a throwaway folder.
     */
    public Map<String, String> read() {
        try {
            Map<String, String> found = new HashMap<>();
            for (String name : listNames()) {
                found.put(name, Files.readString(folder.resolve(name), StandardCharsets.UTF_8));
            }
            return found;
        } catch (IOException ignored) {
            // Half-written file, locked by the editor — on the next look it
            // stands there in full.
            return null;
        }
    }
}
