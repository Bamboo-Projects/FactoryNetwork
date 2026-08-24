package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkFluids;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.network.packet.DisplayStatePacket;
import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;
import dev.devpanda.factorynetwork.network.packet.StorageSnapshotPacket;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.runtime.DisplayValues;
import dev.devpanda.factorynetwork.runtime.Interpreter;
import dev.devpanda.factorynetwork.runtime.MultiblockInstances;
import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import dev.devpanda.factorynetwork.runtime.WorkerRuntime;
import dev.devpanda.factorynetwork.runtime.WorldHost;
import dev.devpanda.factorynetwork.runtime.flow.Flow;
import dev.devpanda.factorynetwork.runtime.flow.FlowCodec;
import dev.devpanda.factorynetwork.runtime.flow.FlowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wurzel eines Netzwerks: hält Programm, Speicher, Graph und Laufzeit.
 *
 * <p>Der Graph wird nicht in jedem Tick neu aufgebaut, sondern in Abständen
 * und wenn jemand den Controller anklickt. Ein Kabel wird selten gesetzt; ein
 * dauernder Neuaufbau wäre die teuerste Stelle der Mod für den geringsten
 * Nutzen.
 */
public class ControllerBlockEntity extends BlockEntity {

    private static final String KEY_SOURCE = "Source";
    private static final String KEY_STORAGE = "Storage";
    private static final String KEY_FLOWS = "Flows";
    private static final String KEY_FLUIDS = "Fluids";
    private static final String KEY_POWER = "Power";
    private static final int REBUILD_INTERVAL = 100;

    /**
     * Das Programm als Projekt aus mehreren Dateien.
     *
     * <p>Ein Programm von dreihundert Zeilen in einem Stück ist keine
     * Übersicht. Alle Dateien teilen einen Namensraum — Dateien sind Ordnung
     * für den Menschen, keine Grenze für die Sprache.
     */
    /**
     * Wer welche Datei gerade bearbeitet.
     *
     * <p>Nur zur Laufzeit — eine Sperre über einen Serverneustart hinweg
     * wäre eine Datei, die niemandem mehr gehört und die keiner aufmacht.
     */
    private final dev.devpanda.factorynetwork.network.FileLocks locks =
            new dev.devpanda.factorynetwork.network.FileLocks();

    /**
     * Was im Editor steht.
     *
     * <p>Neben {@link #project}, das läuft. Ein Tippfehler darf die Fabrik
     * nicht anhalten: Der Entwurf darf kaputt sein, der laufende Stand nicht.
     */
    private dev.devpanda.factorynetwork.lang.Project draft =
            dev.devpanda.factorynetwork.lang.Project.of("");

    private dev.devpanda.factorynetwork.lang.Project project =
            dev.devpanda.factorynetwork.lang.Project.of("");
    private Program program = new Program(List.of());
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private FactoryGraph graph = FactoryGraph.empty();
    private final NetworkStorage storage = new NetworkStorage();
    private final NetworkFluids fluidStorage = new NetworkFluids();
    private final WorkerRuntime runtime = new WorkerRuntime();

    /**
     * Die Serverschränke im Netz.
     *
     * <p>Ohne einen davon rechnet das Netz nicht — weder Worker noch Abläufe.
     * So wie ein Laufwerk die Voraussetzung dafür ist, dass es lagert.
     */
    private final List<dev.devpanda.factorynetwork.block.entity.RackBlockEntity> racks =
            new ArrayList<>();

    /** Die Laufwerke im Netz — für den Stromverbrauch je Zelle. */
    private final List<DriveBlockEntity> drives = new ArrayList<>();

    /**
     * Der Strom des Netzes.
     *
     * <p>Der Puffer sitzt im Controller und nimmt Forge Energy an. Ohne
     * Strom steht alles still — und muss danach erst wieder hochfahren.
     */
    private final dev.devpanda.factorynetwork.network.NetworkPower power =
            new dev.devpanda.factorynetwork.network.NetworkPower();
    /** Abläufe, die warten können — sie überleben einen Serverneustart. */
    private FlowEngine flows;

    /**
     * Aufgeschriebene Abläufe, die noch warten, bis es eine Welt gibt.
     *
     * <p>Beim Laden steht die Welt noch nicht bereit, die Abläufe brauchen
     * aber einen Interpreter und der eine Welt. Also bleibt der Tag liegen,
     * bis der erste Tick kommt.
     */
    private CompoundTag pendingFlows;

    /**
     * Ob das Netz schon einmal aufgebaut wurde.
     *
     * <p>Ein leerer Graph und ein noch nie aufgebauter sehen gleich aus, sind
     * es aber nicht: Beim ersten Mal ist nichts dazugekommen.
     */
    private boolean networkKnown;
    /** Negativ statt Long.MIN_VALUE: Die Differenz liefe sonst über. */
    private long lastRebuild = -REBUILD_INTERVAL;

    /**
     * Das Projekt als Ordner neben der Welt — die Brücke zu VS Code.
     *
     * <p>Erst beim ersten Tick angelegt: Vorher gibt es keinen Server und
     * damit keinen Weltordner.
     */
    private dev.devpanda.factorynetwork.lang.ProgramFolder programFolder;
    private long lastFileCheck = -dev.devpanda.factorynetwork.lang.ProgramFolder.CHECK_INTERVAL;

    /** Letzte gesehene Redstone-Stärke je Connector, für das Ereignis. */
    private final Map<String, Integer> lastRedstone = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    /**
     * Wer gerade die Speicheransicht offen hat.
     *
     * <p>Nur an diese Spieler wird der Bestand geschickt, und nur solange sie
     * hinsehen. Wer den Code-Reiter liest, braucht keine Bestandsänderungen —
     * und der Bestand ist das Teuerste, was über die Leitung geht.
     */
    private final Set<ServerPlayer> storageWatchers = new HashSet<>();

    /**
     * Wer das Terminal überhaupt offen hat, auf welchem Reiter auch immer.
     *
     * <p><b>Getrennt vom Bestand, weil die Statuszeile immer steht.</b> Strom,
     * Kanäle und Server gehören zu jedem Reiter; sie nur beim Speicher zu
     * schicken hieße, dass die Zeile im Code-Reiter einfriert und beim
     * Zurückwechseln springt. Das sieht aus wie ein Fehler und ist einer.
     *
     * <p>Teuer ist das nicht: ein paar Zahlen und die Liste der Abläufe, alle
     * zehn Ticks.
     */
    private final Set<ServerPlayer> terminalWatchers = new HashSet<>();
    private long lastStoragePush = -10;
    private boolean storageDirty;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONTROLLER.get(), pos, state);
        storage.setChangeListener(this::markStorageDirty);
    }

    // ---- Programm ---------------------------------------------------------

    /** Das Projekt mit allen seinen Dateien. */
    public dev.devpanda.factorynetwork.lang.Project project() {
        return project;
    }

    /**
     * Der ganze Quelltext am Stück.
     *
     * <p>Für alles, was nur lesen will. Wer eine einzelne Datei braucht,
     * fragt das Projekt.
     */
    public String source() {
        return project.joined();
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public Program program() {
        return program;
    }

    /**
     * Übernimmt neuen Code. Bei Fehlern bleibt das laufende Programm stehen —
     * eine Fabrik, die wegen eines Tippfehlers ausgeht, ist schlimmer als eine,
     * die noch mit der alten Fassung läuft.
     */
    /**
     * Übernimmt einen einzelnen Text als ganzes Projekt.
     *
     * <p>Der bequeme Weg für alles, was nur einen Text hat — Prüfungen, die
     * Dateibrücke einer alten Welt, ein Aufruf von außen.
     */
    public boolean deploy(String newSource) {
        return deploy(dev.devpanda.factorynetwork.lang.Project.of(newSource));
    }

    public boolean deploy(dev.devpanda.factorynetwork.lang.Project newProject) {
        // Mit dem Blick aufs Netz: Auch wer über den Ordner neben der Welt
        // schreibt, soll erfahren, dass keine Tafel „test" heißt.
        Parser.ParseResult result = newProject.parse(networkView());
        this.project = newProject;
        this.diagnostics = new ArrayList<>(result.diagnostics());
        // Übernommen heißt: Entwurf und laufender Stand sind dasselbe.
        this.draft = newProject;
        writeProgramFile();
        pushProjectToWatchers();
        // Erst nachsehen, dann urteilen: Wer eben einen Schrank gesetzt hat,
        // bekäme sonst „kein Server" zu hören, obwohl einer danebensteht.
        if (level != null && !hasServer()) {
            rebuildNetwork();
        }
        if (!hasServer()) {
            // Ein Programm ohne Server ist kein Fehler im Code. Es wird
            // trotzdem nicht übernommen — sonst stünde es da und liefe nicht,
            // und niemand wüsste warum.
            this.diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    new dev.devpanda.factorynetwork.lang.Span(0, 0, 1, 1),
                    "Kein Serverschrank im Netz.",
                    "Setze einen Serverschrank ans Kabel und bestücke einen Einschub "
                            + "mit Rechenwerk, Speicher und Datenträger."));
            setChanged();
            return false;
        }
        setChanged();
        if (result.hasErrors()) {
            return false;
        }
        // Die Grenze steht hier und nicht erst beim Laufen: Wer auf Übernehmen
        // drückt, soll im selben Moment erfahren, dass es nicht passt — nicht
        // eine Minute später an einer Fabrik, die stillsteht.
        int size = dev.devpanda.factorynetwork.lang.ProgramSize.of(result.program());
        if (size > diskSpace()) {
            this.diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    new dev.devpanda.factorynetwork.lang.Span(0, 0, 1, 1),
                    "Das Programm ist zu groß: " + size + " Anweisungen, "
                            + diskSpace() + " passen auf die Datenträger.",
                    "Bau größere Datenträger ein oder kürze das Programm. "
                            + "Kommentare und Leerzeilen zählen nicht mit."));
            return false;
        }
        // Wartende Abläufe gehen denselben Weg wie über einen Serverneustart:
        // aufschreiben, neue Maschine bauen, zurücklesen. Passt die Gestalt
        // des Programms noch, laufen sie weiter; passt sie nicht, melden sie
        // sich als STALE. Ein Weg, ein Verhalten — statt zwei, die
        // auseinanderlaufen.
        CompoundTag carried = flows == null ? null : FlowCodec.write(flows);
        this.program = result.program();
        runtime.reset();
        this.flows = null;
        if (!FlowCodec.isEmpty(carried)) {
            this.pendingFlows = carried;
        }
        return true;
    }

    // ---- Strom -------------------------------------------------------------

    public dev.devpanda.factorynetwork.network.NetworkPower power() {
        return power;
    }

    /**
     * Was das Netz gerade zieht, in FE je Tick.
     *
     * <p>Gezählt statt mitgeführt, wie die belegten Rechenplätze: Ein
     * Zähler müsste bei jedem gesetzten und abgerissenen Block nachgeführt
     * werden, und der eine vergessene Ort wäre ein Netz, das für Geräte
     * zahlt, die es nicht mehr gibt.
     */
    public int powerDraw() {
        int total = dev.devpanda.factorynetwork.network.Power.CONTROLLER;
        total += graph.connectorCount() * dev.devpanda.factorynetwork.network.Power.CONNECTOR;
        total += graph.displays().size() * dev.devpanda.factorynetwork.network.Power.DISPLAY;
        total += graph.routers().size() * dev.devpanda.factorynetwork.network.Power.ROUTER;
        for (DriveBlockEntity drive : drives) {
            total += dev.devpanda.factorynetwork.network.Power.DRIVE
                    + drive.usedSlots() * dev.devpanda.factorynetwork.network.Power.PER_CELL;
        }
        for (var rack : racks) {
            total += dev.devpanda.factorynetwork.network.Power.RACK
                    + rack.runningBays() * dev.devpanda.factorynetwork.network.Power.PER_SERVER;
        }
        return total;
    }

    /** Läuft das Netz — genug Strom und ein Serverschrank? */
    public boolean isOnline() {
        return hasServer() && power.isRunning();
    }

    // ---- Rechenleistung ---------------------------------------------------

    /**
     * Was die Server des Netzes zusammen tragen.
     *
     * <p>Die Summe über alle <b>vollständigen</b> Einschübe in allen
     * Schränken. Ein Einschub ohne Datenträger steht nicht darin — er ist
     * kein Server, sondern ein Haufen Bauteile.
     */
    public dev.devpanda.factorynetwork.network.ServerBay capacity() {
        var total = dev.devpanda.factorynetwork.network.ServerBay.EMPTY;
        for (var rack : racks) {
            total = total.plus(rack.capacity());
        }
        return total;
    }

    /**
     * Wie viele Abläufe gleichzeitig laufen dürfen.
     *
     * <p>Die Rechenwerke. Null heißt: Es gibt keinen Server, und dann läuft
     * gar nichts.
     */
    public int threads() {
        return capacity().cpu();
    }

    /** Wie viele Abläufe überhaupt bestehen dürfen — die Speicher. */
    public int memory() {
        return capacity().ram();
    }

    /** Wie groß das Programm sein darf — die Datenträger. */
    public int diskSpace() {
        return capacity().disk();
    }

    /** Wie groß das Programm gerade ist, in Anweisungen. */
    public int programSize() {
        return dev.devpanda.factorynetwork.lang.ProgramSize.of(program);
    }

    /**
     * Passt das Programm auf die Datenträger?
     *
     * <p>Ohne Server ist die Frage keine: Dann läuft ohnehin nichts, und ein
     * zweiter Grund dafür verwirrt nur.
     */
    public boolean programFits() {
        return !hasServer() || programSize() <= diskSpace();
    }

    /** Steht im Netz überhaupt Rechenleistung? */
    public boolean hasServer() {
        return threads() > 0;
    }

    // ---- Netzwerk ---------------------------------------------------------

    public FactoryGraph graph() {
        return graph;
    }

    public NetworkStorage storage() {
        return storage;
    }

    public NetworkFluids fluids() {
        return fluidStorage;
    }

    public WorkerRuntime runtime() {
        return runtime;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ControllerRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            ControllerRegistry.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    public void rebuildNetwork() {
        if (level == null) {
            return;
        }
        Set<String> before = networkKnown ? Set.copyOf(graph.connectorNames()) : null;
        graph = FactoryGraph.build(level, worldPosition);
        lastRebuild = level.getGameTime();
        networkKnown = true;
        // Der Speicher ist die Summe der Laufwerke im Netz. Ohne diesen
        // Schritt lagert der Controller nichts — was auch stimmt: Ohne
        // Laufwerk gibt es keinen Platz.
        List<DriveBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : graph.drives()) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof DriveBlockEntity drive) {
                found.add(drive);
            }
        }
        drives.clear();
        drives.addAll(found);
        storage.setDrives(found);
        // Dieselben Laufwerke tragen die Flüssigkeitszellen. Ein zweites
        // Laufwerk nur dafür wäre ein Block mehr für dieselbe Handlung.
        fluidStorage.setDrives(found);
        racks.clear();
        for (BlockPos pos : graph.racks()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos)
                    instanceof dev.devpanda.factorynetwork.block.entity.RackBlockEntity rack) {
                racks.add(rack);
            }
        }
        announceDeviceChanges(before);
    }

    /**
     * Meldet, welche Geräte gekommen und gegangen sind.
     *
     * <p>Beim allerersten Aufbau bleibt es still: Da ist nichts
     * dazugekommen, es war nur vorher nichts bekannt. Ein Sturm von
     * {@code device_online} bei jedem Serverstart wäre für den Spieler
     * dasselbe wie Rauschen.
     */
    private void announceDeviceChanges(Set<String> before) {
        if (before == null || program.handlers().isEmpty()) {
            return;
        }
        Set<String> now = graph.connectorNames();
        for (String name : now) {
            if (!before.contains(name)) {
                fireEvent("device_online", List.of(new Value.Device(name)));
            }
        }
        for (String name : before) {
            if (!now.contains(name)) {
                // Kein Value.Device: Das Gerät gibt es nicht mehr, und ein
                // Verweis darauf ließe sich nicht mehr auflösen.
                fireEvent("device_offline", List.of(new Value.Text(name)));
            }
        }
    }

    // ---- Tick -------------------------------------------------------------

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (level.getGameTime() - lastRebuild >= REBUILD_INTERVAL) {
            rebuildNetwork();
        }
        power.setDraw(powerDraw());
        power.tick();

        // Vor dem Ausstieg unten: Ein Netz ohne Strom oder ohne Server ist
        // genau das Netz, an dessen Programm man gerade arbeitet.
        checkProgramFile();

        // Ohne Serverschrank oder ohne Strom steht das Netz still — und läuft
        // weiter, wo es war, sobald beides wieder da ist. Nichts wird
        // abgebrochen: Ein Ablauf hält zwischen zwei Schritten keine
        // Gegenstände, also kostet das Einfrieren nichts.
        if (!isOnline()) {
            pushStorageIfDue();
            pushFlowsIfDue();
            setChanged();
            return;
        }
        // Abläufe laufen auch ohne Worker weiter — ein Programm darf allein
        // aus Funktionen bestehen, die auf Ereignisse warten.
        if (!program.workers().isEmpty()) {
            runtime.setConnectorLookup(position ->
                    level.isLoaded(position)
                            && level.getBlockEntity(position) instanceof ConnectorBlockEntity connector
                            ? connector : null);
            runtime.tick(level, program, graph, storage, fluidStorage);
            // Was weder Speicher noch Gerät annahm, fällt auf den Boden.
            // Hässlich, aber die einzige Antwort, die nichts verschwinden
            // lässt.
            for (net.minecraft.world.item.ItemStack stack : runtime.takeDropped()) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack);
            }
        }
        tickFlows();
        fireRedstoneEvents();
        fireInventoryEvents();
        pushStorageIfDue();
        pushFlowsIfDue();
        setChanged();
    }

    /** Unter diesem Namen stehen die Dateien des Projekts. */
    private static final String KEY_FILES = "Files";

    /** Und darunter der Entwurf, der noch nicht übernommen wurde. */
    private static final String KEY_DRAFT = "Draft";

    /**
     * Liest das Projekt aus dem Speicherformat.
     *
     * <p><b>Alte Welten mitgelesen:</b> Dort steht ein einzelner Text unter
     * {@code Source}. Der wird zur {@code main.mf} — ein Projekt aus einer
     * Datei ist genau das, was ein Controller vorher hatte.
     */
    private static dev.devpanda.factorynetwork.lang.Project readProject(CompoundTag tag) {
        if (tag.contains(KEY_FILES)) {
            CompoundTag files = tag.getCompound(KEY_FILES);
            Map<String, String> sources = new HashMap<>();
            files.getAllKeys().forEach(name -> sources.put(name, files.getString(name)));
            return new dev.devpanda.factorynetwork.lang.Project(sources);
        }
        return dev.devpanda.factorynetwork.lang.Project.of(tag.getString(KEY_SOURCE));
    }

    private void writeProject(CompoundTag tag) {
        CompoundTag files = new CompoundTag();
        project.files().forEach(files::putString);
        tag.put(KEY_FILES, files);
        // Der Entwurf nur, wenn er sich vom Laufenden unterscheidet: Sonst
        // stünde jedes Programm zweimal in der Welt.
        if (!draft.files().equals(project.files())) {
            CompoundTag entwurf = new CompoundTag();
            draft.files().forEach(entwurf::putString);
            tag.put(KEY_DRAFT, entwurf);
        }
        // Der alte Schlüssel bleibt beschrieben: Wer die Welt mit einer
        // älteren Fassung der Mod öffnet, findet wenigstens seinen Text
        // wieder, statt ein leeres Terminal.
        tag.putString(KEY_SOURCE, project.joined());
    }

    // ---- Die Datei neben der Welt -----------------------------------------

    /**
     * Sieht nach, ob jemand das Programm von außen geändert hat.
     *
     * <p>Beim allerersten Blick entscheidet, ob es die Datei schon gibt:
     * Gibt es keine, bekommt sie das laufende Programm. Gibt es eine, gilt
     * sie — dann hat jemand sie angelegt oder bei ausgeschaltetem Server
     * bearbeitet, und beides ist eine Absicht.
     *
     * <p><b>Ein Controller, der an dieselbe Stelle zurückkommt, findet sein
     * Programm wieder.</b> Die Datei bleibt beim Abbauen liegen; wer sich
     * verklickt hat, setzt den Block zurück und hat alles.
     */
    private void checkProgramFile() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        if (level.getGameTime() - lastFileCheck
                < dev.devpanda.factorynetwork.lang.ProgramFolder.CHECK_INTERVAL) {
            return;
        }
        lastFileCheck = level.getGameTime();
        if (programFolder == null) {
            programFolder = dev.devpanda.factorynetwork.lang.ProgramFolder.of(
                    server, worldPosition);
            if (programFolder == null) {
                return;
            }
            // Beim ersten Blick: Ist der Ordner leer, bekommt er das
            // laufende Projekt. Steht etwas darin, gilt das — dann hat
            // jemand es angelegt oder bei ausgeschaltetem Server
            // bearbeitet, und beides ist eine Absicht.
            programFolder.write(project);
        }
        dev.devpanda.factorynetwork.lang.Project incoming = programFolder.poll(project);
        if (incoming == null) {
            return;
        }
        String name = programFolder.path().getFileName().toString();
        if (deploy(incoming)) {
            note("Projekt aus " + name + " übernommen: "
                    + String.join(", ", incoming.names()));
        } else {
            // Die Fehler stehen wie immer im Code-Reiter. Hier nur der
            // Hinweis, dass es an den Dateien lag und nicht am Terminal.
            note("Fehler in " + name + " — das laufende Programm läuft weiter.");
        }
    }

    /**
     * Wo das Projekt als Ordner liegt, oder {@code null}, solange noch
     * niemand danach gesehen hat.
     */
    public java.nio.file.Path programFilePath() {
        return programFolder == null ? null : programFolder.path();
    }

    /**
     * Schreibt das Programm in die Datei.
     *
     * <p>Nach <b>jedem</b> Übernehmen, auch nach einem mit Fehlern: Ordner
     * und Terminal müssen denselben Text zeigen. Stünde in den Dateien noch
     * die letzte fehlerfreie Fassung, holte der nächste Blick sie zurück und
     * überschriebe, was gerade eingetippt wurde.
     */
    private void writeProgramFile() {
        if (programFolder != null) {
            programFolder.write(project);
        }
    }

    // ---- Speicheransicht --------------------------------------------------

    /**
     * So oft höchstens, in Ticks. Ein Worker bewegt sonst jeden Tick etwas.
     *
     * <p>Der Anfangswert von {@code lastStoragePush} ist derselbe Wert
     * negativ — als Konstante geschrieben liefe er der Feldreihenfolge
     * voraus.
     */
    private static final int STORAGE_PUSH_INTERVAL = 10;

    /** Das Terminal ist auf, egal welcher Reiter. */
    public void watchTerminal(ServerPlayer player) {
        terminalWatchers.add(player);
        pushProjectTo(player);
        pushFlowsTo(player);
        pushDisplaysTo(player);
    }

    /**
     * Schickt das ganze Projekt.
     *
     * <p>Beim Öffnen und nach jedem Übernehmen — nicht laufend. Ein Projekt
     * ändert sich, wenn jemand es ändert, und dann weiß der Server es.
     */
    public void pushProjectTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                dev.devpanda.factorynetwork.network.packet.ProjectStatePacket
                        .of(worldPosition, project, draft, locksFor(player.getUUID())));
    }

    /**
     * Was das Netz an Namen hergibt — für den Übersetzer.
     *
     * <p>Damit meldet auch das Übernehmen, was der Editor schon beim Tippen
     * sagt. Wichtig für den Weg über den Ordner neben der Welt: Wer dort
     * schreibt, hat keinen Editor, der ihn warnt.
     */
    public dev.devpanda.factorynetwork.lang.NetworkView networkView() {
        List<String> connectorNames = new ArrayList<>(graph.connectorNames());
        List<String> displayNames = displayNames();
        return new dev.devpanda.factorynetwork.lang.NetworkView() {
            @Override
            public boolean knowsNetwork() {
                return !connectorNames.isEmpty() || !displayNames.isEmpty();
            }

            @Override
            public List<String> connectors() {
                return connectorNames;
            }

            @Override
            public List<String> displays() {
                return displayNames;
            }
        };
    }

    /**
     * Die Anzeigewände im Netz, jede einmal, mit ihrer Stelle.
     *
     * <p>Die Namen aus den Blöcken und nicht aus dem Graphen: Der merkt sich
     * Stellen, keine Namen. Eine Wand besteht aus vielen Tafeln mit
     * demselben Namen — genommen wird die erste, und die reicht zum
     * Wiederfinden.
     */
    public List<dev.devpanda.factorynetwork.network.packet.NamedPlace> displayPlaces() {
        if (level == null) {
            return List.of();
        }
        Map<String, BlockPos> byName = new java.util.TreeMap<>();
        for (BlockPos pos : graph.displays()) {
            if (level.getBlockEntity(pos) instanceof DisplayBlockEntity panel) {
                String name = panel.displayName();
                if (name != null && !name.isBlank()) {
                    byName.putIfAbsent(name, pos.immutable());
                }
            }
        }
        return byName.entrySet().stream()
                .map(entry -> new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Nur die Namen — für den Übersetzer, der keine Stellen braucht. */
    public List<String> displayNames() {
        return displayPlaces().stream()
                .map(dev.devpanda.factorynetwork.network.packet.NamedPlace::name)
                .toList();
    }

    /** Die Connectoren im Netz mit ihrer Stelle. */
    public List<dev.devpanda.factorynetwork.network.packet.NamedPlace> connectorPlaces() {
        return graph.connectors().entrySet().stream()
                .map(entry -> new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Die Profile aller Connectoren, für den Editor.
     *
     * <p>Einmal beim Öffnen des Terminals. Wer nicht geladen ist, liefert ein
     * Profil, das nichts über sich sagt — und das ist etwas anderes als eines,
     * das nichts kann.
     */
    public List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat>
            connectorProfiles() {
        List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat> profiles =
                new java.util.ArrayList<>();
        if (level == null) {
            return profiles;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!(level.getBlockEntity(entry.getValue())
                    instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            profiles.add(dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.toFlat(
                    entry.getKey(), DeviceScan.of(connector)));
        }
        return profiles;
    }

    /** Was im Editor steht, auch wenn es noch nicht läuft. */
    public dev.devpanda.factorynetwork.lang.Project draft() {
        return draft;
    }

    /**
     * Nimmt einen Entwurf entgegen.
     *
     * <p><b>Der Grund, warum es ihn gibt:</b> Vorher lebte der Text nur im
     * Client. Ein Absturz, ein Kick, ein versehentliches Verlassen der Welt —
     * und eine halbe Stunde Arbeit war weg, weil nur das Übernehmen sie
     * gesichert hätte. Und Übernehmen geht nur, wenn das Programm fehlerfrei
     * ist; genau in der Mitte einer Änderung ist es das nie.
     *
     * <p>Geschickt wird der ganze Entwurf und keine Änderungsliste. Ein
     * Programm hier ist ein paar Dutzend Zeilen, gedeckelt auf 64 KB je
     * Datei, und eine Änderungsliste mit Positionen ist die Sorte Code, in
     * der sich ein Fehler erst zeigt, wenn der Text schon kaputt ist.
     *
     * <p>Der Absender bekommt nichts zurück: Er hat den Text ja gerade
     * geschickt, und ein Echo träfe ihn mitten im Tippen.
     */
    public void acceptDraft(dev.devpanda.factorynetwork.lang.Project incoming,
                            java.util.UUID author, String authorName) {
        if (incoming.files().equals(draft.files())) {
            return;
        }
        this.draft = author == null ? incoming : merge(incoming, author, authorName);
        setChanged();
        // Der Absender bekommt normalerweise nichts zurück — er hat den Text
        // ja gerade geschickt, und ein Echo träfe ihn mitten im Tippen.
        //
        // <b>Außer, es wurde etwas abgelehnt.</b> Dann ist das Echo die
        // einzige Auskunft darüber, und ohne sie tippt er weiter in eine
        // Datei, die niemand mehr annimmt.
        boolean rejected = !draft.files().equals(incoming.files());
        for (ServerPlayer watcher : terminalWatchers) {
            if (!watcher.getUUID().equals(author) || rejected) {
                pushProjectTo(watcher);
            }
        }
    }

    /**
     * Übernimmt vom eingehenden Entwurf nur, was der Absender schreiben darf.
     *
     * <p><b>Ohne das überschreiben sich zwei Spieler wortlos.</b> Beide
     * schicken den ganzen Entwurf; wer zuletzt tippt, gewinnt — auch über
     * eine Datei, die er gar nicht offen hatte. Der andere merkt es, wenn
     * seine Arbeit weg ist.
     *
     * <p>Eine Datei, die jemand anders hält, bleibt deshalb stehen, wie sie
     * ist. Der Absender bekommt sie beim nächsten Zustand zurückgeschickt
     * und sieht sie im Editor als gesperrt.
     */
    private dev.devpanda.factorynetwork.lang.Project merge(
            dev.devpanda.factorynetwork.lang.Project incoming, java.util.UUID author,
            String authorName) {
        long now = level == null ? 0L : level.getGameTime();
        Map<String, String> merged = new HashMap<>(draft.files());
        for (Map.Entry<String, String> entry : incoming.files().entrySet()) {
            if (draft.files().containsKey(entry.getKey())
                    && entry.getValue().equals(draft.source(entry.getKey()))) {
                // Unverändert mitgeschickt: keine Änderung, also auch kein
                // Anspruch auf die Datei.
                //
                // Die Prüfung auf „gibt es schon" gehört dazu: Eine frisch
                // angelegte Datei ist leer, und source() liefert für eine
                // unbekannte ebenfalls den leeren Text. Ohne sie fiele jede
                // neue Datei hier durch und käme nie beim Server an.
                continue;
            }
            if (locks.claim(entry.getKey(), author, authorName, now)) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        // Gelöscht wird nur, was der Absender auch halten darf.
        merged.keySet().removeIf(name -> !incoming.files().containsKey(name)
                && locks.claim(name, author, authorName, now));
        return new dev.devpanda.factorynetwork.lang.Project(merged);
    }

    /** Wer welche Datei hält, aus Sicht dieses Spielers. */
    public Map<String, String> locksFor(java.util.UUID player) {
        return locks.othersFor(player, level == null ? 0L : level.getGameTime());
    }

    /** Und an alle, die gerade zusehen — nach einer Änderung von außen. */
    private void pushProjectToWatchers() {
        terminalWatchers.forEach(this::pushProjectTo);
    }

    public void unwatchTerminal(ServerPlayer player) {
        // Erst freigeben, dann abmelden: Auf den Zeitablauf zu warten hieße,
        // dass ein anderer eine Minute vor einer Datei steht, die niemand
        // mehr offen hat.
        locks.release(player.getUUID());
        terminalWatchers.stream().filter(other -> other != player)
                .forEach(this::pushProjectTo);
        terminalWatchers.remove(player);
        storageWatchers.remove(player);
    }

    public void watchStorage(ServerPlayer player) {
        storageWatchers.add(player);
        pushStorageTo(player, true);
    }

    public void unwatchStorage(ServerPlayer player) {
        storageWatchers.remove(player);
    }

    /** Merkt vor, dass sich etwas geändert hat — geschickt wird gebündelt. */
    public void markStorageDirty() {
        storageDirty = true;
    }

    private void pushStorageIfDue() {
        if (level == null || storageWatchers.isEmpty()) {
            return;
        }
        if (!storageDirty || level.getGameTime() - lastStoragePush < STORAGE_PUSH_INTERVAL) {
            return;
        }
        lastStoragePush = level.getGameTime();
        storageDirty = false;
        // Abgemeldete Spieler mitnehmen, damit die Menge nicht leckt.
        storageWatchers.removeIf(player -> player.isRemoved()
                || !(player.containerMenu instanceof TerminalMenu));
        storageWatchers.forEach(player -> pushStorageTo(player, true));
    }

    /** Schickt den Bestand an einen Spieler. */
    public void pushStorageTo(ServerPlayer player, boolean replace) {
        Map<Item, Long> contents = storage.contents();
        List<StorageSnapshotPacket.Entry> entries = contents.entrySet().stream()
                .sorted(Map.Entry.<Item, Long>comparingByValue().reversed())
                .limit(StorageSnapshotPacket.MAX_ENTRIES)
                .map(entry -> new StorageSnapshotPacket.Entry(entry.getKey(), entry.getValue()))
                .toList();
        List<StorageSnapshotPacket.FluidEntry> fluidEntries = fluidStorage.contents().entrySet()
                .stream()
                .map(entry -> new StorageSnapshotPacket.FluidEntry(entry.getKey(),
                        entry.getValue()))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new StorageSnapshotPacket(entries, fluidEntries, replace, contents.size(),
                        storage.freeTypes(), fluidStorage.freeTypes()));
    }

    // ---- Abläufe im Terminal ----------------------------------------------

    /** So oft höchstens, in Ticks. */
    private static final int FLOW_PUSH_INTERVAL = 10;

    private long lastFlowPush = -FLOW_PUSH_INTERVAL;

    /**
     * Schickt die Liste der Abläufe an die Zuschauer.
     *
     * <p>Anders als der Bestand wird sie nicht vorgemerkt, sondern regelmäßig
     * geschickt: Ein Ablauf, der schläft, ändert nichts und wäre trotzdem eine
     * Zeile wert, deren Zustand sich jederzeit ändern kann.
     */
    private void pushFlowsIfDue() {
        if (level == null || terminalWatchers.isEmpty()
                || level.getGameTime() - lastFlowPush < FLOW_PUSH_INTERVAL) {
            return;
        }
        lastFlowPush = level.getGameTime();
        terminalWatchers.removeIf(player -> player.isRemoved()
                || player.containerMenu
                        instanceof net.minecraft.world.inventory.InventoryMenu);
        terminalWatchers.forEach(player -> {
            pushFlowsTo(player);
            pushDisplaysTo(player);
        });
    }

    /** Schickt die Abläufe an einen Spieler. */
    public void pushFlowsTo(ServerPlayer player) {
        FlowEngine engine = flowEngine();
        PacketDistributor.sendToPlayer(player, new FlowStatePacket(flowLines(),
                new FlowStatePacket.Compute(threads(),
                        engine == null ? 0 : engine.occupied(),
                        engine == null ? 0 : engine.queued(),
                        memory(), programSize(), diskSpace()),
                new FlowStatePacket.Supply(power.state().ordinal(), power.stored(),
                        power.capacity(), powerDraw())));
    }

    /** Die Abläufe als Zeilen — getrennt vom Senden, damit prüfbar. */
    public List<FlowStatePacket.Line> flowLines() {
        List<FlowStatePacket.Line> lines = new ArrayList<>();
        if (flows != null) {
            flows.flows().values().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
            // Die zuletzt gescheiterten hinten dran, damit ein Fehler von
            // heute Nacht morgen früh noch dasteht.
            flows.failed().forEach(flow -> lines.add(new FlowStatePacket.Line(
                    flow.id(), flow.entryPoint(), flow.status().name(), flow.detail())));
        }
        return lines;
    }

    // ---- Anzeigen im Terminal ---------------------------------------------

    /**
     * Schickt die Anzeigen an einen Spieler.
     *
     * <p>Ausgewertet wird hier, gezeichnet dort — dieselbe Aufteilung wie beim
     * Display an der Wand. Was über die Leitung geht, steht am Ende so da.
     */
    public void pushDisplaysTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DisplayStatePacket(displayPanels()));
    }

    /** Die Anzeigen als fertige Zeilen — getrennt vom Senden, damit prüfbar. */
    public List<DisplayStatePacket.Panel> displayPanels() {
        List<DisplayStatePacket.Panel> panels = new ArrayList<>();
        DisplayValues values = new DisplayValues(graph, storage, runtime);
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Display display)) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            List<Integer> buttons = new ArrayList<>();
            List<DisplayValues.Line> evaluated = values.evaluate(display);
            for (int i = 0; i < evaluated.size(); i++) {
                lines.add(DisplayBlockEntity.format(evaluated.get(i)));
                if (evaluated.get(i).kind() == Decl.Display.Entry.Kind.BUTTON) {
                    buttons.add(i);
                }
            }
            panels.add(new DisplayStatePacket.Panel(display.name(), lines, buttons));
        }
        addUnknownPanels(panels);
        return panels;
    }

    /**
     * Trägt nach, was in der Welt hängt und im Programm fehlt.
     *
     * <p>Bisher listete der Reiter nur die {@code display}-Deklarationen.
     * Eine benannte Tafel ohne passende Deklaration war damit <b>nirgends</b>
     * zu sehen — sie sagte es nur selbst auf ihrer Front, und die hängt
     * womöglich drei Räume weiter. Dasselbe gilt für eine Tafel ohne Namen.
     *
     * <p>Das ist dieselbe Sorte Auskunft wie „unbenannter Connector" oder
     * „Gerät ohne Kanal": etwas hängt im Netz und tut nichts, und der Grund
     * gehört dorthin, wo man nachsieht.
     */
    private void addUnknownPanels(List<DisplayStatePacket.Panel> panels) {
        if (level == null) {
            return;
        }
        Set<String> declared = new HashSet<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.Display display) {
                declared.add(display.name());
            }
        }
        Set<BlockPos> walls = new HashSet<>();
        Set<String> unknown = new java.util.LinkedHashSet<>();
        int nameless = 0;
        for (BlockPos pos : graph.displays()) {
            if (!level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof DisplayBlockEntity panel)) {
                continue;
            }
            // Eine Wand einmal, nicht einmal je Tafel.
            var wall = panel.wall();
            if (!walls.add(wall.anchor())) {
                continue;
            }
            String name = panel.wallName(wall);
            if (name.isBlank()) {
                nameless++;
            } else if (!declared.contains(name)) {
                unknown.add(name);
            }
        }
        for (String name : unknown) {
            panels.add(new DisplayStatePacket.Panel(name,
                    List.of("§eim Netz, aber das Programm kennt kein display " + name),
                    List.of()));
        }
        if (nameless > 0) {
            panels.add(new DisplayStatePacket.Panel("—",
                    List.of("§8" + nameless + " Tafel(n) ohne Namen"), List.of()));
        }
    }

    /**
     * Der Flüssigkeitsbestand, als Zeilen für das Terminal.
     *
     * <p>Als Text und nicht als Symbolraster: Eine Flüssigkeit hat kein
     * Gegenstandsbild, und ein Netz hält selten mehr als eine Handvoll Sorten
     * — dafür lohnt kein zweites Raster.
     */
    public List<String> fluidLines() {
        return fluidStorage.contents().entrySet().stream()
                .sorted(Map.Entry.<net.minecraft.world.level.material.Fluid, Long>
                        comparingByValue().reversed())
                .map(entry -> net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(entry.getKey()).getPath() + ": " + entry.getValue() + " mB")
                .toList();
    }

    /**
     * Die gebauten Anlagen, als Zeilen für das Terminal.
     *
     * <p>Eine unvollständige Anlage bliebe sonst unsichtbar: Sie tut nichts
     * und sagt nichts, und der Spieler sucht den Fehler im Programm statt an
     * der Beschriftung.
     */
    public List<String> plants() {
        return MultiblockInstances.resolve(program, graph.connectorNames()).values().stream()
                .map(instance -> {
                    if (instance.ambiguous()) {
                        return instance.name() + ": mehrere Vorlagen passen";
                    }
                    if (!instance.missing().isEmpty()) {
                        return instance.name() + ": es fehlt "
                                + String.join(", ", instance.missing());
                    }
                    return instance.name() + ": " + instance.template().name();
                })
                .toList();
    }

    /**
     * Löst aus, was hinter einem Knopf steht.
     *
     * <p>Als Ablauf, nicht als gewöhnlicher Aufruf: Ein Knopf soll etwas
     * anstoßen dürfen, das wartet, und einen Rückgabewert braucht niemand.
     */
    public void pressDisplayButton(String displayName, int entryIndex) {
        Decl.Display display = program.declarations().stream()
                .filter(Decl.Display.class::isInstance).map(Decl.Display.class::cast)
                .filter(candidate -> candidate.name().equals(displayName))
                .findFirst().orElse(null);
        if (display == null || entryIndex < 0 || entryIndex >= display.entries().size()) {
            return;
        }
        Decl.Display.Entry entry = display.entries().get(entryIndex);
        if (entry.kind() != Decl.Display.Entry.Kind.BUTTON) {
            // Der Client hat auf eine Zeile gezeigt, die kein Knopf ist.
            return;
        }
        String function = functionNameOf(entry.value());
        if (function == null) {
            note("Der Knopf " + entry.label() + " nennt keine Funktion.");
            return;
        }
        try {
            startFlow(function, List.of());
        } catch (ScriptError error) {
            note(entry.label() + ": " + error);
        }
    }

    /** Welche Funktion hinter einem Knopf steht — Name oder Aufruf. */
    private static String functionNameOf(Expr value) {
        return switch (value) {
            case Expr.Name name -> name.value();
            case Expr.Call call when call.callee() instanceof Expr.Name name -> name.value();
            case null, default -> null;
        };
    }

    /**
     * Sucht nach Änderungen der Redstone-Stärke und löst dafür Ereignisse aus.
     *
     * <p>Abgefragt statt gemeldet, und nur alle zehn Ticks: Das Konzept lässt
     * der Laufzeit ausdrücklich internes Abfragen, solange der Spieler dafür
     * keine Schleife schreiben muss. Bei einigen Dutzend Connectoren ist das
     * billiger als an jedem Block zu horchen.
     */
    /** Der zuletzt gesehene Inhalt je Gerät, als Fingerabdruck. */
    private final Map<String, Integer> lastContents = new HashMap<>();

    /**
     * Meldet, wenn sich der Inhalt eines Geräts geändert hat.
     *
     * <p><b>Nicht {@code device_done}.</b> Ob eine Maschine <em>fertig</em>
     * ist, weiß niemand von außen: Der Ausgang kann von vorher gefüllt sein,
     * und jede Mod zählt anders. Was sich sagen lässt, ist, dass sich etwas
     * geändert hat — was das bedeutet, schreibt der Spieler selbst. Eine
     * Automatisierung, die einmal zu früh weiterschaltet, verliert Gegenstände
     * in einer Kiste, die niemand mehr findet.
     *
     * <p>Abgefragt statt gemeldet und nur alle zehn Ticks, wie beim Redstone —
     * und nur, wenn das Programm überhaupt darauf hört.
     */
    private void fireInventoryEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (program.handlers().stream().noneMatch(h -> h.name().equals("device_changed"))) {
            lastContents.clear();
            return;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())
                    || !(level.getBlockEntity(entry.getValue())
                            instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            int fingerprint = contentsFingerprint(connector);
            Integer previous = lastContents.put(entry.getKey(), fingerprint);
            if (previous == null || previous == fingerprint) {
                // Beim ersten Sehen wird nicht gemeldet: Da hat sich nichts
                // geändert, es war nur nichts bekannt.
                continue;
            }
            fireEvent("device_changed", List.of(new Value.Device(entry.getKey())));
        }
    }

    /**
     * Eine Zahl, die sich ändert, wenn sich der Inhalt ändert.
     *
     * <p>Inventar und Tank zusammen — eine Maschine kann beides haben, und wer
     * auf sie wartet, will von beidem wissen.
     */
    private static int contentsFingerprint(ConnectorBlockEntity connector) {
        int hash = 17;
        var handler = connector.machineInventory();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0
                        : stack.getItem().hashCode() * 31 + stack.getCount());
            }
        }
        var tank = connector.machineTank();
        if (tank != null) {
            for (int slot = 0; slot < tank.getTanks(); slot++) {
                var stack = tank.getFluidInTank(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0
                        : stack.getFluid().hashCode() * 31 + stack.getAmount());
            }
        }
        return hash;
    }

    private void fireRedstoneEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (program.handlers().stream().noneMatch(h -> h.name().equals("redstone_changed"))) {
            return;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())) {
                continue;
            }
            int strength = level.getBestNeighborSignal(entry.getValue());
            Integer previous = lastRedstone.put(entry.getKey(), strength);
            if (previous != null && previous == strength) {
                continue;
            }
            // Über die Ablaufmaschine, damit ein on redstone_changed selbst
            // warten darf — auf eine Maschine, auf einen Zähler, auf Zeit.
            fireEvent("redstone_changed",
                    List.of(new Value.Device(entry.getKey()), new Value.Int(strength)));
        }
    }

    /**
     * Lässt die wartenden Abläufe arbeiten.
     *
     * <p>Die Maschine wird beim ersten Bedarf gebaut und beim Übernehmen
     * neuen Codes verworfen — mit der Einschränkung, die noch fehlt: Abläufe,
     * die gerade warten, müssten den Wechsel überstehen und den Spieler
     * fragen. Das kommt im nächsten Schritt.
     */
    private void tickFlows() {
        if (flows == null && pendingFlows == null) {
            return;
        }
        FlowEngine engine = flowEngine();
        if (engine != null) {
            engine.tick(level.getGameTime());
        }
    }

    /**
     * Die Ablaufmaschine, bei Bedarf gebaut und mit dem Gespeicherten gefüllt.
     *
     * <p>Das Auspacken steht hier und nicht im Tick, damit es nicht darauf
     * ankommt, wer zuerst fragt. Ein Ereignis, das im selben Tick eintrifft,
     * in dem der Chunk geladen wurde, würde sonst auf eine leere Maschine
     * treffen — und die wartenden Abläufe kämen einen Tick zu spät.
     */
    public FlowEngine flowEngine() {
        if (flows == null && level != null) {
            flows = new FlowEngine(program, new Interpreter(program,
                    new WorldHost(level, graph, storage, fluidStorage)));
        }
        if (flows != null && pendingFlows != null) {
            CompoundTag saved = pendingFlows;
            pendingFlows = null;
            FlowCodec.read(saved, flows);
        }
        // Ein frisch geladener Controller kennt sein Netz noch nicht. Das ist
        // etwas anderes als „kein Server, kein Strom" — ohne diesen Aufbau
        // stünde jedes Netz nach dem Laden still, bis der erste Tick kommt.
        if (flows != null && !networkKnown) {
            rebuildNetwork();
        }
        if (flows != null) {
            // Hier und nicht im Tick: Jeder Zugriff auf die Maschine geht
            // durch diese Stelle, und beides kann sich zwischen zwei Ticks
            // ändern — jemand steckt ein Bauteil dazu, reißt den Schrank
            // ab oder der Strom fällt aus. Ein Knopf am Display und ein
            // Ereignis treiben die Maschine unmittelbar an, ohne über den
            // Tick zu gehen; die Sperre muss deshalb hier sitzen.
            flows.setThreadLimit(threads());
            flows.setMemoryLimit(memory());
            // Zieht jemand einen Datenträger heraus, passt das Programm
            // plötzlich nicht mehr. Dann friert das Netz ein, wie bei
            // Stromausfall — nicht abbrechen, nicht kürzen.
            flows.setFrozen(!isOnline() || !programFits());
        }
        return flows;
    }

    /** Beginnt einen wartefähigen Ablauf. */
    public Flow startFlow(String functionName, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            throw new ScriptError("Keine Welt.");
        }
        Flow flow = engine.start(functionName, arguments);
        engine.tick(level.getGameTime());
        return flow;
    }

    /** Weckt wartende Abläufe und löst Ereignisblöcke aus. */
    public void fireEvent(String event, List<Value> arguments) {
        FlowEngine engine = flowEngine();
        if (engine == null) {
            return;
        }
        engine.post(event, arguments);
        engine.tick(level.getGameTime());
    }

    /** Ruft eine Funktion des Programms auf — für Tests und das Terminal. */
    public Value callFunction(String name, List<Value> arguments) {
        if (level == null) {
            throw new ScriptError("Keine Welt.");
        }
        WorldHost host = new WorldHost(level, graph, storage, fluidStorage);
        Interpreter interpreter = new Interpreter(program, host);
        FlowEngine engine = flowEngine();
        if (engine != null) {
            // Auch ein Aufruf aus dem Terminal kann etwas auslösen, worauf
            // ein Ablauf wartet.
            interpreter.setEventSink(engine::post);
        }
        try {
            return interpreter.call(name, arguments);
        } finally {
            host.logs().forEach(this::note);
            if (engine != null) {
                engine.tick(level.getGameTime());
            }
        }
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    private void note(String message) {
        log.add(message);
        if (log.size() > 100) {
            log.remove(0);
        }
    }

    // ---- Speichern --------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        project = readProject(tag);
        // Ohne eigenen Entwurf ist der laufende Stand der Entwurf.
        if (tag.contains(KEY_DRAFT)) {
            CompoundTag entwurf = tag.getCompound(KEY_DRAFT);
            Map<String, String> sources = new HashMap<>();
            entwurf.getAllKeys().forEach(name -> sources.put(name, entwurf.getString(name)));
            draft = new dev.devpanda.factorynetwork.lang.Project(sources);
        } else {
            draft = project;
        }
        storage.load(tag.getCompound(KEY_STORAGE), registries);
        fluidStorage.load(tag.getCompound(KEY_FLUIDS), registries);
        power.load(tag.getCompound(KEY_POWER), registries);
        // Die Abläufe warten auf den ersten Tick: Sie brauchen einen
        // Interpreter, der braucht eine Welt, und die gibt es hier noch nicht
        // verlässlich.
        pendingFlows = tag.contains(KEY_FLOWS) ? tag.getCompound(KEY_FLOWS) : null;
        // Nach dem Laden wird neu übersetzt: Der Baum selbst wird nicht
        // gespeichert, weil sich die Sprache ändern kann, der Quelltext aber
        // gültig bleibt.
        if (!source().isBlank()) {
            Parser.ParseResult result = project.parse();
            diagnostics = new ArrayList<>(result.diagnostics());
            if (!result.hasErrors()) {
                program = result.program();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeProject(tag);
        CompoundTag storageTag = new CompoundTag();
        storage.save(storageTag, registries);
        tag.put(KEY_STORAGE, storageTag);
        CompoundTag fluidTag = new CompoundTag();
        fluidStorage.save(fluidTag, registries);
        tag.put(KEY_FLUIDS, fluidTag);
        CompoundTag powerTag = new CompoundTag();
        power.save(powerTag, registries);
        tag.put(KEY_POWER, powerTag);
        if (flows != null) {
            tag.put(KEY_FLOWS, FlowCodec.write(flows));
        } else if (pendingFlows != null) {
            // Noch nicht ausgepackt — dann unverändert weiterreichen, statt
            // die Abläufe beim ersten Speichern zu verlieren.
            tag.put(KEY_FLOWS, pendingFlows);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeProject(tag);
        return tag;
    }
}
