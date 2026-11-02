package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.ProgramFolder;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ControllerProjectPersistenceTest {
    @TempDir Path folder;

    @Test
    void loadsLegacySingleFileAsRunningAndDraft() {
        var tag = new CompoundTag();
        tag.putString("Source", "// ä 日本語\nfn alt() {}\n");
        var saved = ControllerProjectPersistence.read(tag);
        assertEquals(tag.getString("Source"), saved.running().source(Project.MAIN));
        assertEquals(saved.running(), saved.draft());
    }

    @Test
    void roundTripsRunningProjectAndRejectedDraftSeparately() {
        var running = new Project(Map.of("main.mf", "fn laeuft() {}",
                "erz/fluss.mf", "fn erz() {}"));
        var draft = running.with("main.mf", "fn halb() { let a =");
        var tag = new CompoundTag();
        ControllerProjectPersistence.write(tag, running, draft);
        assertEquals(running.joined(), tag.getString("Source"));
        assertEquals(new ControllerProjectPersistence.Saved(running, draft),
                ControllerProjectPersistence.read(tag));
    }

    @Test
    void equalDraftNeedsNoSecondCopyAndModernFilesWinOverLegacySource() {
        var running = Project.of("fn aktuell() {}");
        var tag = new CompoundTag();
        ControllerProjectPersistence.write(tag, running, running);
        assertFalse(tag.contains("Draft"));
        tag.putString("Source", "fn veraltet() {}");
        assertEquals(new ControllerProjectPersistence.Saved(running, running),
                ControllerProjectPersistence.read(tag));
    }

    @Test
    void firstPollExportsDraftAndStatusWaitsUntilNextCheck() throws IOException {
        var persistence = new ControllerProjectPersistence(Runnable::run);
        var draft = Project.of("fn halb() { let a =");
        var statusWrites = new ArrayList<Path>();
        assertNull(persistence.path());
        persistence.writeDraft(draft);
        assertFalse(Files.exists(folder.resolve("main.mf")));
        assertNull(persistence.poll(0, () -> ProgramFolder.at(folder), draft, statusWrites::add));
        assertEquals(folder, persistence.path());
        assertEquals(draft.source(Project.MAIN), Files.readString(folder.resolve("main.mf")));
        assertTrue(statusWrites.isEmpty());

        persistence.poll(ProgramFolder.CHECK_INTERVAL - 1, () -> fail("folder already open"),
                draft, statusWrites::add);
        assertTrue(statusWrites.isEmpty());
        persistence.poll(ProgramFolder.CHECK_INTERVAL, () -> fail("folder already open"),
                draft, statusWrites::add);
        assertEquals(List.of(folder), statusWrites);
        persistence.poll(2L * ProgramFolder.CHECK_INTERVAL, () -> fail("folder already open"),
                draft, statusWrites::add);
        assertEquals(1, statusWrites.size(), "unchanged status is not rebuilt");

        persistence.markStatusStale();
        persistence.poll(3L * ProgramFolder.CHECK_INTERVAL, () -> fail("folder already open"),
                draft, statusWrites::add);
        assertEquals(2, statusWrites.size());
    }

    @Test
    void externalEditIsReturnedOnceAndExportDoesNotEcho() throws IOException {
        var persistence = new ControllerProjectPersistence(Runnable::run);
        var draft = Project.of("fn alt() {}");
        persistence.poll(0, () -> ProgramFolder.at(folder), draft, path -> {});
        String external = "fn extern() {}";
        Files.writeString(folder.resolve("main.mf"), external);
        assertNull(persistence.poll(1, () -> fail("folder already open"), draft, path -> {}));
        var incoming = persistence.poll(ProgramFolder.CHECK_INTERVAL,
                () -> fail("folder already open"), draft, path -> {});
        assertEquals(Project.of(external), incoming);
        assertNull(persistence.poll(2L * ProgramFolder.CHECK_INTERVAL,
                () -> fail("folder already open"), draft, path -> {}));

        var failedAttempt = Project.of("fn unvollstaendig( {");
        persistence.writeDraft(failedAttempt);
        assertEquals(failedAttempt.source(Project.MAIN), Files.readString(folder.resolve("main.mf")));
        assertNull(persistence.poll(3L * ProgramFolder.CHECK_INTERVAL,
                () -> fail("folder already open"), failedAttempt, path -> {}));
    }

    @Test
    void readOverlappingOurOwnWriteIsDiscarded() throws IOException {
        var queued = new ArrayList<Runnable>();
        var persistence = new ControllerProjectPersistence(queued::add);
        var draft = Project.of("fn alt() {}");
        persistence.poll(0, () -> ProgramFolder.at(folder), draft, path -> {});
        // The background read has not run yet; an external edit lands, and
        // then we write ourselves before the read finishes.
        Files.writeString(folder.resolve("main.mf"), "fn extern() {}");
        queued.removeFirst().run();
        persistence.writeDraft(Project.of("fn eigen() {}"));
        assertNull(persistence.poll(1, () -> fail("folder already open"), draft, path -> {}),
                "a read that overlapped our write must not count as an external edit");
    }

    @Test
    void unavailableFolderIsRetriedAtTheNormalInterval() {
        var persistence = new ControllerProjectPersistence(Runnable::run);
        var attempts = new AtomicInteger();
        var draft = Project.of("fn alt() {}");
        assertNull(persistence.poll(0, () -> { attempts.incrementAndGet(); return null; },
                draft, path -> fail("no folder for status")));
        assertNull(persistence.path());
        persistence.poll(1, () -> { attempts.incrementAndGet(); return null; },
                draft, path -> fail("no folder for status"));
        assertEquals(1, attempts.get());
        persistence.poll(ProgramFolder.CHECK_INTERVAL,
                () -> { attempts.incrementAndGet(); return ProgramFolder.at(folder); },
                draft, path -> fail("first status write is deferred"));
        assertEquals(2, attempts.get());
        assertEquals(folder, persistence.path());
    }
}
