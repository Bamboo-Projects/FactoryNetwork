package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.platform.Nbt;
import dev.devpanda.factorynetwork.lang.ProgramFolder;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.nbt.CompoundTag;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The controller's project save format and external editor files.
 *
 * <p>Owns persistence details, not deployment: a polled project is returned
 * to the controller, which decides whether it can run. The controller also
 * supplies the world-specific folder and status data only when needed.
 */
final class ControllerProjectPersistence {
    private static final String SOURCE = "Source";
    private static final String FILES = "Files";
    private static final String DRAFT = "Draft";

    record Saved(Project running, Project draft) {}

    private ProgramFolder folder;
    // Negative rather than MIN_VALUE so subtraction cannot overflow on first use.
    private long lastCheck = -ProgramFolder.CHECK_INTERVAL;
    private boolean statusStale = true;
    /** A read of the folder running in the background, and our write count when it started. */
    private java.util.concurrent.CompletableFuture<Map<String, String>> pendingRead;
    private int writesAtRead;

    /**
     * Reads the program folders off the server thread. Each controller used
     * to walk and read its folder there once a second; with many networks,
     * or a slow disk, that is disk access in the middle of the tick.
     */
    private static final java.util.concurrent.ExecutorService FILE_READS =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FactoryNetwork program files");
                thread.setDaemon(true);
                return thread;
            });

    private final java.util.concurrent.Executor reads;

    ControllerProjectPersistence() {
        this(FILE_READS);
    }

    /** With an executor of one's own; tests pass one that runs at once. */
    ControllerProjectPersistence(java.util.concurrent.Executor reads) {
        this.reads = reads;
    }

    static Saved read(CompoundTag tag) {
        Project running = tag.contains(FILES)
                ? readFiles(Nbt.getCompound(tag, FILES)) : Project.of(Nbt.getString(tag, SOURCE));
        Project draft = tag.contains(DRAFT) ? readFiles(Nbt.getCompound(tag, DRAFT)) : running;
        return new Saved(running, draft);
    }

    private static Project readFiles(CompoundTag files) {
        Map<String, String> sources = new HashMap<>();
        Nbt.keys(files).forEach(name -> sources.put(name, Nbt.getString(files, name)));
        return new Project(sources);
    }

    static void write(CompoundTag tag, Project running, Project draft) {
        CompoundTag files = new CompoundTag();
        running.files().forEach(files::putString);
        tag.put(FILES, files);
        // Equal states need no second copy in the world's save data.
        if (!draft.files().equals(running.files())) {
            CompoundTag pending = new CompoundTag();
            draft.files().forEach(pending::putString);
            tag.put(DRAFT, pending);
        }
        // Keep the legacy single-file value for older versions of the mod.
        tag.putString(SOURCE, running.joined());
    }

    void markStatusStale() {
        statusStale = true;
    }

    /** Null before the first successful attempt to open the controller's folder. */
    Path path() {
        return folder == null ? null : folder.path();
    }

    /** Export an attempted draft, including code that did not compile. */
    void writeDraft(Project draft) {
        if (folder != null) {
            folder.write(draft);
        }
    }

    /**
     * Checks at the existing file interval and returns an external edit, if any.
     *
     * <p>The first successful open exports the controller's current draft.
     * Subsequent external changes are returned for deployment. Status is
     * written before polling, so a deployment's new diagnostics are exported
     * on the following check. Opening failures are retried at the same interval.
     */
    Project poll(long gameTime, Supplier<ProgramFolder> openFolder, Project draft,
                 Consumer<Path> writeStatus) {
        // The result of the last background read, once it is there.
        Project incoming = takeRead(draft);
        if (gameTime - lastCheck < ProgramFolder.CHECK_INTERVAL) {
            return incoming;
        }
        lastCheck = gameTime;
        if (folder != null && statusStale) {
            statusStale = false;
            writeStatus.accept(folder.path());
        }
        if (folder == null) {
            folder = openFolder.get();
            if (folder == null) {
                return incoming;
            }
            folder.write(draft);
            statusStale = true;
        }
        if (pendingRead == null) {
            ProgramFolder reading = folder;
            writesAtRead = reading.writes();
            pendingRead = java.util.concurrent.CompletableFuture.supplyAsync(reading::read, reads);
            // An executor that ran at once has the answer already.
            if (incoming == null) {
                incoming = takeRead(draft);
            }
        }
        return incoming;
    }

    private Project takeRead(Project draft) {
        if (pendingRead == null || !pendingRead.isDone()) {
            return null;
        }
        Map<String, String> found = pendingRead.getNow(null);
        pendingRead = null;
        // We wrote in the meantime: what was read may be half ours.
        if (folder == null || folder.writes() != writesAtRead) {
            return null;
        }
        return folder.accept(found, draft);
    }
}
