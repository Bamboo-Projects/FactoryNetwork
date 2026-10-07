package dev.devpanda.factorynetwork.block.entity;

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

    static Saved read(CompoundTag tag) {
        Project running = tag.contains(FILES)
                ? readFiles(tag.getCompound(FILES)) : Project.of(tag.getString(SOURCE));
        Project draft = tag.contains(DRAFT) ? readFiles(tag.getCompound(DRAFT)) : running;
        return new Saved(running, draft);
    }

    private static Project readFiles(CompoundTag files) {
        Map<String, String> sources = new HashMap<>();
        files.getAllKeys().forEach(name -> sources.put(name, files.getString(name)));
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
        if (gameTime - lastCheck < ProgramFolder.CHECK_INTERVAL) {
            return null;
        }
        lastCheck = gameTime;
        if (folder != null && statusStale) {
            statusStale = false;
            writeStatus.accept(folder.path());
        }
        if (folder == null) {
            folder = openFolder.get();
            if (folder == null) {
                return null;
            }
            folder.write(draft);
            statusStale = true;
        }
        return folder.poll(draft);
    }
}
