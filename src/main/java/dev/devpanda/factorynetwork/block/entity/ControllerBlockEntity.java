package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.BuiltinEvents;
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
    private static final String KEY_GLOBALS = "Globals";
    private static final String KEY_LOG = "Log";

    /**
     * So viele Zeilen hält das Protokoll.
     *
     * <p>Es geht mit der Welt auf die Platte, also darf es nicht wachsen.
     * Zweihundert Zeilen sind genug, um zu sehen, was in der letzten Stunde
     * schiefging, und klein genug, dass niemand sie bemerkt.
     */
    private static final int LOG_LIMIT = 200;
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

    /**
     * Die globalen Werte des Programms.
     *
     * <p>Sie gehören zum Netz wie der Stromvorrat und der Speicherinhalt, und
     * sie überleben aus demselben Grund den Serverneustart: Ein Wert, der
     * sagt, in welchem Modus die Fabrik läuft, wäre nach einem Neustart
     * sinnlos, wenn er wieder auf dem Anfangswert stünde.
     */
    private final Map<String, dev.devpanda.factorynetwork.runtime.Value> globals =
            new java.util.LinkedHashMap<>();

    private List<Diagnostic> diagnostics = new ArrayList<>();
    private FactoryGraph graph = FactoryGraph.empty();
    private final NetworkStorage storage = new NetworkStorage();
    private final NetworkFluids fluidStorage = new NetworkFluids();

    /**
     * Der Chemikalienspeicher.
     *
     * <p>Eine Schnittstelle und keine Klasse: Chemikalien gehören Mekanism,
     * und ein Feld mit einem Mekanism-Typ ließe diesen Controller in einem
     * Pack ohne die Mod nicht mehr laden. Ohne Mekanism steht hier der
     * Speicher, der nichts kann — und das ist die Wahrheit über ein solches
     * Pack, keine Notlösung.
     */
    private final dev.devpanda.factorynetwork.network.ChemicalStore chemicalStorage =
            dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.create();
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

    /**
     * Ob sich seit dem letzten Schreiben etwas geändert hat, das nach draußen
     * gehört: Fehler oder Gerätenamen.
     *
     * <p>Ein Merker und kein Schreiben je Tick. {@code ProgramStatus} schreibt
     * zwar nur, wenn sich der Inhalt unterscheidet — aber die Datei jedesmal
     * zu bauen und zu vergleichen wäre Arbeit für nichts, und zwar je
     * Controller und Sekunde.
     */
    private boolean statusStale = true;

    /** Letzte gesehene Redstone-Stärke je Connector, für das Ereignis. */
    private final Map<String, Integer> lastRedstone = new HashMap<>();
    /**
     * Was das Netz zu sagen hatte.
     *
     * <p>Geht mit der Welt auf die Platte: Wer morgens nachsieht, warum die
     * Anlage nachts stehen blieb, findet die Zeile auch dann noch, wenn der
     * Server zwischendurch neu startete.
     */
    private final List<dev.devpanda.factorynetwork.runtime.LogEntry> log = new ArrayList<>();

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
        runtime.setDeviceFilled(this::noteFilled);
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
        // Was der Übersetzer sagt, gehört auch nach draußen.
        statusStale = true;
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
        adoptGlobals(result.program());
        this.program = result.program();
        runtime.reset();
        // Gruppen sofort auflösen und nicht erst im nächsten Tick: Ein
        // Ablauf, der gleich nach dem Übernehmen startet, fragt sonst eine
        // leere Gruppe.
        runtime.resolveGroups(this.program, graph);
        this.flows = null;
        if (!FlowCodec.isEmpty(carried)) {
            this.pendingFlows = carried;
        }
        return true;
    }

    // ---- Globale Werte -----------------------------------------------------

    /** Was das Programm an globalen Werten hält. */
    public Map<String, dev.devpanda.factorynetwork.runtime.Value> globals() {
        return globals;
    }

    /**
     * Stellt die globalen Werte auf ein neues Programm um.
     *
     * <p>Die Regel in vier Fällen:
     *
     * <ul>
     *   <li><b>Gleicher Name, gleiche Art:</b> Der Wert bleibt. Wer den Modus
     *       auf „nacht" gestellt hat und dann einen Worker ändert, will nicht
     *       wieder bei „tag" anfangen.
     *   <li><b>Neuer Name:</b> der Anfangswert aus der Deklaration.
     *   <li><b>Name weg:</b> vergessen.
     *   <li><b>Gleicher Name, andere Art:</b> der neue Anfangswert. Ein Text,
     *       der plötzlich als Zahl gelesen wird, ist kein erhaltenswerter
     *       Zustand.
     * </ul>
     *
     * <p>Dieselbe Haltung wie bei den Worker-Zuständen: Was noch passt,
     * bleibt; was nicht mehr passt, fängt neu an.
     */
    private void adoptGlobals(Program incoming) {
        Map<String, dev.devpanda.factorynetwork.runtime.Value> next =
                new java.util.LinkedHashMap<>();
        for (dev.devpanda.factorynetwork.lang.ast.Decl declaration : incoming.declarations()) {
            if (!(declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Global global)) {
                continue;
            }
            dev.devpanda.factorynetwork.runtime.Value fresh =
                    dev.devpanda.factorynetwork.runtime.Value.ofLiteral(global.value());
            dev.devpanda.factorynetwork.runtime.Value kept = globals.get(global.name());
            boolean sameKind = kept != null && kept.getClass() == fresh.getClass();
            next.put(global.name(), sameKind ? kept : fresh);
        }
        globals.clear();
        globals.putAll(next);
        setChanged();
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
        total += graph.extensions().size() * dev.devpanda.factorynetwork.network.Power.EXTENSION;
        total += graph.fabricators().size()
                * dev.devpanda.factorynetwork.network.Power.FABRICATOR;
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

    /** Der Chemikalienspeicher; ohne Mekanism einer, der nichts kann. */
    public dev.devpanda.factorynetwork.network.ChemicalStore chemicals() {
        return chemicalStorage;
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
        // Die Gerätenamen ändern sich hier, und sie stehen in keiner Datei —
        // ein Editor daneben erfährt sie nur von uns.
        statusStale = true;
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
        // Und dieselben tragen die Chemikalienzellen — ohne Mekanism nimmt
        // dieser Speicher die Liste entgegen und tut nichts damit.
        chemicalStorage.setDrives(found);
        // Und dieselben tragen die Energiezellen. Ohne diese Zeile ist der
        // Vorrat der Puffer im Controller, egal wie viele Akkus im Regal
        // stecken.
        power.setDrives(found);
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
                fireEvent(BuiltinEvents.DEVICE_ONLINE, List.of(new Value.Device(name)));
            }
        }
        for (String name : before) {
            if (!now.contains(name)) {
                // Kein Value.Device: Das Gerät gibt es nicht mehr, und ein
                // Verweis darauf ließe sich nicht mehr auflösen.
                fireEvent(BuiltinEvents.DEVICE_OFFLINE, List.of(new Value.Text(name)));
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
            runtime.setPower(power);
            runtime.setCrafting(craftingForWorkers);
            runtime.tick(level, program, graph, storage, fluidStorage,
                    newHost());
            // Was die Worker zu melden hatten, gehört ins Protokoll. Bisher
            // sammelte die Laufzeit diese Hinweise und niemand las sie.
            runtime.drainNotes().forEach(this::add);
            // Was weder Speicher noch Gerät annahm, fällt auf den Boden.
            // Hässlich, aber die einzige Antwort, die nichts verschwinden
            // lässt.
            for (net.minecraft.world.item.ItemStack stack : runtime.takeDropped()) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack);
            }
        }
        tickFlows();
        tickCrafting();
        fireRedstoneEvents();
        fireInventoryEvents();
        pushStorageIfDue();
        pushFlowsIfDue();
        setChanged();
    }

    // ---- Fertigung --------------------------------------------------------

    /** Alle zwanzig Ticks ein Schritt je Fabricator. */
    private static final int CRAFT_INTERVAL = 20;

    /**
     * Die Fertigungsaufträge des Netzes.
     *
     * <p>Sie leben hier und nicht am Fabricator: Ein Auftrag, der am Gerät
     * hinge, wäre weg, sobald jemand das Gerät abbaut — und das ist genau der
     * Moment, in dem man wissen will, was noch offen war.
     */
    private final List<dev.devpanda.factorynetwork.crafting.CraftingJob> jobs =
            new ArrayList<>();

    private long nextJobId = 1;

    public List<dev.devpanda.factorynetwork.crafting.CraftingJob> craftingJobs() {
        return List.copyOf(jobs);
    }

    /**
     * Nimmt einen Auftrag an, oder lehnt ihn ab.
     *
     * <p><b>Abgelehnt wird nur, was gar kein Rezept hat.</b> Fehlende Zutaten
     * sind kein Grund: Der Auftrag wartet dann und sagt, was fehlt — wer 64
     * Truhen bestellt und danach Bretter einlagert, soll nicht noch einmal
     * bestellen müssen.
     *
     * @return der Auftrag, oder {@code null}, wenn es kein Rezept gibt
     */
    public dev.devpanda.factorynetwork.crafting.CraftingJob requestCraft(
            net.minecraft.world.item.Item target, int amount) {
        if (level == null || amount <= 0) {
            return null;
        }
        if (!recipeKnown(target)) {
            return null;
        }
        var job = new dev.devpanda.factorynetwork.crafting.CraftingJob(
                nextJobId++, target, amount);
        jobs.add(job);
        setChanged();
        return job;
    }

    /**
     * Die Fertigung, wie ein {@code from crafting}-Worker sie sieht.
     *
     * <p>Ein Feld und keine neue Instanz je Tick: Der Worker fragt sie
     * mehrmals je Runde, und ein Objekt, das zwanzigmal je Sekunde entsteht,
     * ist Müll ohne Zweck.
     */
    private final dev.devpanda.factorynetwork.runtime.WorkerRuntime.Crafting craftingForWorkers =
            new dev.devpanda.factorynetwork.runtime.WorkerRuntime.Crafting() {

                @Override
                public long pending(net.minecraft.world.item.Item item) {
                    long open = 0;
                    for (var job : jobs) {
                        if (job.target() == item) {
                            open += job.remaining();
                        }
                    }
                    return open;
                }

                @Override
                public boolean order(net.minecraft.world.item.Item item, int amount) {
                    return requestCraft(item, amount) != null;
                }
            };

    /** Bricht einen Auftrag ab. */
    public boolean cancelCraft(long id) {
        boolean removed = jobs.removeIf(job -> job.id() == id);
        if (removed) {
            setChanged();
        }
        return removed;
    }

    /**
     * Ein Fertigungstakt.
     *
     * <p><b>Ein Schritt je Fabricator</b>, und ein Schritt ist ein Durchlauf
     * eines Rezepts, so oft er in einen Stapel passt. Wer schneller fertigen
     * will, stellt einen zweiten Fabricator hin — das ist dieselbe Antwort
     * wie bei Kanälen und Laufwerken: Mehr Gerät, mehr Durchsatz.
     */
    private void tickCrafting() {
        if (jobs.isEmpty()) {
            return;
        }
        // Erledigte melden sich, bevor sie aus der Liste fallen. Ein Ereignis
        // nach dem Verschwinden hätte niemanden mehr, über den es spricht.
        jobs.removeIf(job -> {
            if (job.status() == dev.devpanda.factorynetwork.crafting.CraftingJob.Status.DONE) {
                fireEvent(dev.devpanda.factorynetwork.lang.BuiltinEvents.CRAFTING_FINISHED,
                        List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(job.id())));
                return true;
            }
            if (job.status() == dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED) {
                fireEvent(dev.devpanda.factorynetwork.lang.BuiltinEvents.CRAFTING_FAILED,
                        List.of(new dev.devpanda.factorynetwork.runtime.Value.Int(job.id()),
                                new dev.devpanda.factorynetwork.runtime.Value.Text(
                                        job.detail())));
                return true;
            }
            return false;
        });
        if (jobs.isEmpty() || level.getGameTime() % CRAFT_INTERVAL != 0) {
            return;
        }
        int steps = graph.fabricators().size();
        if (steps == 0) {
            jobs.forEach(job -> job.note(
                    dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "kein Fabricator im Netz"));
            return;
        }
        for (var job : jobs) {
            if (steps <= 0) {
                break;
            }
            if (craftOnce(job)) {
                steps--;
            }
        }
        setChanged();
    }

    /**
     * Das Rezeptverzeichnis dieses Ticks.
     *
     * <p>Einmal je Tick gebaut und dann für alle Aufträge und alle Knoten
     * ihrer Rezeptbäume benutzt. Länger aufzubewahren hieße, sich darauf zu
     * verlassen, dass ein {@code /reload} den Rezeptverwalter austauscht —
     * und das ist keine Zusage, sondern Innenleben.
     */
    private dev.devpanda.factorynetwork.crafting.RecipeLookup handCache;
    private dev.devpanda.factorynetwork.crafting.MachineRecipes machineCache;
    private long recipeCacheTick = Long.MIN_VALUE;

    private dev.devpanda.factorynetwork.crafting.DeclaredRecipes declaredCache;
    private Program declaredFrom;

    private void refreshRecipes() {
        long now = level.getGameTime();
        if (handCache == null || recipeCacheTick != now) {
            handCache = dev.devpanda.factorynetwork.crafting.RecipeLookup.of(level);
            machineCache = dev.devpanda.factorynetwork.crafting.MachineRecipes.of(level);
            recipeCacheTick = now;
        }
        // Die erklärten Rezepte hängen am Programm und nicht am Tick: Sie
        // ändern sich nur beim Übernehmen. Das laufende Programm ist ohnehin
        // schon übersetzt — es noch einmal zu lesen wäre der teuerste Weg zu
        // einer Auskunft, die danebenliegt.
        if (declaredCache == null || declaredFrom != program()) {
            declaredFrom = program();
            declaredCache =
                    dev.devpanda.factorynetwork.crafting.DeclaredRecipes.of(declaredFrom);
        }
    }

    /**
     * Alle Rezepte, die das Netz lesen kann.
     *
     * <p>Zwei Quellen: was ohne Maschine geht (Werkbank, Steinsäge) und was
     * eine Maschine braucht (Ofenfamilie, Presse). Für einen Gegenstand
     * können beide etwas haben — neun Barren aus einem Block, ein Barren aus
     * Roherz —, und <b>der Bestand entscheidet</b>, wie schon innerhalb einer
     * Quelle.
     */
    private dev.devpanda.factorynetwork.crafting.CraftingPlanner.Recipes<
            net.minecraft.world.item.Item> recipes() {
        refreshRecipes();
        return dev.devpanda.factorynetwork.crafting.CraftingPlanner.anyOf(
                handCache, machineCache, declaredCache);
    }

    /** Ob es für diesen Gegenstand überhaupt ein Rezept gibt — in einer der Quellen. */
    private boolean recipeKnown(net.minecraft.world.item.Item target) {
        return recipes().find(target, item -> 0L) != null;
    }

    /**
     * Ein Durchlauf für einen Auftrag.
     *
     * <p><b>Erst planen, dann einen Schritt.</b> Der Plan zerlegt die
     * Bestellung bis zu dem, was dasteht — acht Bretter, die es nicht gibt,
     * werden zu zwei Stämmen, die es gibt. Ausgeführt wird davon der unterste
     * Schritt, also der, der sofort laufen kann; den nächsten findet der
     * nächste Takt.
     *
     * <p><b>Und der Plan wird jedes Mal neu gerechnet</b>, nicht gemerkt. Ein
     * gemerkter Plan wäre ab dem Moment falsch, in dem ein Worker etwas
     * einlagert — und genau das tun Worker den ganzen Tag. Dafür kostet er
     * nichts an Speicherformat und übersteht jeden Neustart von selbst.
     *
     * <p>Erst prüfen, dann entnehmen, dann einlagern. Anders ginge es nicht:
     * Ein Rezept, dem nach der halben Entnahme etwas fehlt, hätte die andere
     * Hälfte verschwinden lassen.
     *
     * @return ob wirklich gebaut wurde
     */
    private boolean craftOnce(dev.devpanda.factorynetwork.crafting.CraftingJob job) {
        // Liegt schon etwas in einer Maschine, geschieht sonst nichts. Ein
        // neu gerechneter Plan saehe die Zutat verschwunden und das Ergebnis
        // noch nicht da - und legte ein zweites Mal ein.
        if (job.running() != null) {
            return collectRunning(job);
        }
        var recipes = recipes();
        if (!recipeKnown(job.target())) {
            // Ein Rezept, das es nicht mehr gibt: Der Auftrag kann nicht mehr
            // fertig werden, und darauf zu warten hieße, für immer zu warten.
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED,
                    "kein Rezept mehr");
            return false;
        }
        // Höchstens ein Stapel des Ziels je Schritt. Ein Auftrag über
        // zehntausend soll nicht einen Tick lang den Server halten.
        long want = Math.min(job.remaining(), job.target().getDefaultMaxStackSize());
        var plan = dev.devpanda.factorynetwork.crafting.CraftingPlanner.plan(
                recipes, storage::count, job.target(), want,
                dev.devpanda.factorynetwork.FnConfig.craftingDepth(),
                dev.devpanda.factorynetwork.FnConfig.craftingBudget());
        if (!plan.complete()) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    dev.devpanda.factorynetwork.crafting.RecipeLookup.missing(plan.missing()));
            return false;
        }
        var step = plan.steps().get(0);
        // Der Plan hat mit demselben Bestand gerechnet, aus dem gleich
        // entnommen wird. Die Probe kostet nichts und hält die Zusage, dass
        // nie eine halbe Entnahme stehenbleibt.
        for (var entry : step.consumed().entrySet()) {
            if (storage.count(entry.getKey()) < entry.getValue()) {
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        "es fehlt: " + entry.getValue() + " "
                                + entry.getKey().getDescription().getString());
                return false;
            }
        }
        if (!step.station().isEmpty()) {
            return startAtMachine(job, step);
        }
        step.consumed().forEach(storage::extract);
        long left = storage.insert(step.result(), step.yield());
        if (left > 0) {
            // Der Speicher ist voll. Was nicht hineinpasst, fällt zu Boden —
            // dieselbe Antwort wie bei einem Worker, dessen Ziel voll ist.
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(step.result(), (int) left));
        }
        if (step.result() == job.target()) {
            job.produced((int) step.yield());
        } else {
            // Eine Zwischenstufe zählt nicht als Fortschritt der Bestellung —
            // aber sie soll dastehen, sonst sieht ein Auftrag über eine
            // Maschine minutenlang aus, als geschehe nichts.
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    "baut " + step.yield() + " " + step.result().getDescription().getString());
        }
        return true;
    }

    /**
     * Legt die Zutat in eine freie Maschine und merkt sich, worauf gewartet wird.
     *
     * <p><b>Eine Sorte je Durchgang.</b> Der Plan darf mischen — fünf
     * Eisenerze und drei Roheisen für acht Barren —, ein Ofenfach kann das
     * nicht. Genommen wird deshalb die erste Sorte und so viel davon, wie in
     * ein Fach passt; der Rest kommt beim nächsten Takt dran.
     *
     * @return ob wirklich etwas angefangen wurde
     */
    private boolean startAtMachine(dev.devpanda.factorynetwork.crafting.CraftingJob job,
            dev.devpanda.factorynetwork.crafting.CraftingPlanner.Step<
                    net.minecraft.world.item.Item> step) {
        String declared = dev.devpanda.factorynetwork.crafting.DeclaredRecipes
                .deviceOf(step.station());
        if (declared != null) {
            return startAtDeclared(job, step, declared);
        }
        var station = dev.devpanda.factorynetwork.crafting.MachineRecipes
                .stationOf(step.station());
        if (station == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.FAILED,
                    "unbekannte Station " + step.station());
            return false;
        }
        var first = step.consumed().entrySet().iterator().next();
        net.minecraft.world.item.Item ingredient = first.getKey();
        String free = freeMachine(step.station(), station, ingredient);
        if (free == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "wartet auf " + machineName(step.station()) + " im Netz");
            return false;
        }
        var handler = machineInventory(free);
        int room = handler.getSlotLimit(station.inputSlot());
        long take = Math.min(first.getValue(), Math.min(room, step.runs()));
        if (take <= 0) {
            return false;
        }
        long got = storage.extract(ingredient, take);
        if (got <= 0) {
            return false;
        }
        net.minecraft.world.item.ItemStack rest = handler.insertItem(station.inputSlot(),
                new net.minecraft.world.item.ItemStack(ingredient, (int) got), false);
        long placed = got - rest.getCount();
        if (!rest.isEmpty()) {
            // Was die Maschine doch nicht nahm, geht zurück — dasselbe Muster
            // wie überall, wo diese Mod etwas anbietet.
            storage.insert(ingredient, rest.getCount());
        }
        if (placed <= 0) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    machineName(step.station()) + " nimmt gerade nichts an");
            return false;
        }
        job.setRunning(new dev.devpanda.factorynetwork.crafting.CraftingJob.Running(
                step.station(), free, step.result(), placed * step.perCraft(), 0));
        job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                placed + " " + ingredient.getDescription().getString() + " in " + free);
        return true;
    }

    /**
     * Ein Rezept aus dem Programm: die Zutaten in die genannte Maschine.
     *
     * <p><b>Ohne Fachnummern.</b> Wo bei einer fremden Maschine was hingehört,
     * weiß nur sie selbst — und genau dafür gibt es den seitenbezogenen
     * Zugriff, den auch {@code move} benutzt. Der Ofen ist der Sonderfall, bei
     * dem diese Mod die Fächer kennt; eine Maschine aus einer fremden Mod ist
     * es nicht, und sie soll ihre eigenen Regeln behalten.
     *
     * <p>Anders als bei der Ofenfamilie werden <b>alle</b> Zutaten auf einmal
     * eingelegt: Ein erklärtes Rezept darf mehrere haben, und eine Maschine,
     * die nur die halbe Rechnung bekommt, fängt gar nicht erst an.
     */
    private boolean startAtDeclared(dev.devpanda.factorynetwork.crafting.CraftingJob job,
            dev.devpanda.factorynetwork.crafting.CraftingPlanner.Step<
                    net.minecraft.world.item.Item> step, String device) {
        var handler = machineSide(device);
        if (handler == null) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "an " + device + " hängt keine Maschine");
            return false;
        }
        // Erst proben, dann entnehmen: Eine Maschine, die die zweite Zutat
        // ablehnt, hätte sonst die erste geschluckt und den Auftrag mit einem
        // halben Rezept stehenlassen.
        for (var entry : step.consumed().entrySet()) {
            net.minecraft.world.item.ItemStack probe = new net.minecraft.world.item.ItemStack(
                    entry.getKey(), (int) (long) entry.getValue());
            if (!insertAll(handler, probe, true).isEmpty()) {
                job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                        device + " nimmt gerade nicht alles an");
                return false;
            }
        }
        for (var entry : step.consumed().entrySet()) {
            long got = storage.extract(entry.getKey(), entry.getValue());
            net.minecraft.world.item.ItemStack rest = insertAll(handler,
                    new net.minecraft.world.item.ItemStack(entry.getKey(), (int) got), false);
            if (!rest.isEmpty()) {
                storage.insert(entry.getKey(), rest.getCount());
            }
        }
        job.setRunning(new dev.devpanda.factorynetwork.crafting.CraftingJob.Running(
                step.station(), device, step.result(), step.yield(), 0));
        job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                "läuft in " + device);
        return true;
    }

    /**
     * Legt einen Stapel in irgendein Fach, das ihn nimmt.
     *
     * <p>Welches, entscheidet die Maschine über ihre Seitenregeln — dieselbe
     * Auskunft, auf die sich {@code move} verlässt.
     */
    private static net.minecraft.world.item.ItemStack insertAll(
            net.neoforged.neoforge.items.IItemHandler handler,
            net.minecraft.world.item.ItemStack stack, boolean simulate) {
        net.minecraft.world.item.ItemStack rest = stack;
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, simulate);
        }
        return rest;
    }

    /**
     * Zieht ein Ergebnis aus irgendeinem Fach, das es hergibt.
     *
     * <p>Ein Eingangsfach gibt von sich aus nichts heraus — das entscheidet
     * die Maschine, und deshalb holt diese Schleife nie versehentlich die
     * Zutat zurück, die gerade verarbeitet wird.
     */
    private static long extractAll(net.neoforged.neoforge.items.IItemHandler handler,
            net.minecraft.world.item.Item wanted, long limit) {
        long taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < limit; slot++) {
            if (handler.getStackInSlot(slot).getItem() != wanted) {
                continue;
            }
            taken += handler.extractItem(slot, (int) (limit - taken), false).getCount();
        }
        return taken;
    }

    /** Der seitenbezogene Zugriff auf die Maschine hinter einem Connector. */
    private net.neoforged.neoforge.items.IItemHandler machineSide(String device) {
        var position = graph.connector(device).orElse(null);
        if (position == null || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof ConnectorBlockEntity connector)) {
            return null;
        }
        return connector.machineInventory();
    }

    /**
     * Holt ab, was die Maschine fertig hat.
     *
     * <p>Das Netz holt selbst und wartet nicht darauf, dass jemand einen
     * Worker dafür schreibt. Ein Auftrag, der stillsteht, weil eine Zeile im
     * Programm fehlt, die niemand verlangt hat, wäre die unangenehmste Sorte
     * Stillstand.
     */
    private boolean collectRunning(dev.devpanda.factorynetwork.crafting.CraftingJob job) {
        var running = job.running();
        boolean declared = dev.devpanda.factorynetwork.crafting.DeclaredRecipes
                .deviceOf(running.station()) != null;
        var station = dev.devpanda.factorynetwork.crafting.MachineRecipes
                .stationOf(running.station());
        var handler = declared ? machineSide(running.device())
                : machineInventory(running.device());
        if (handler == null || (!declared && station == null)) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    running.device() + " ist nicht erreichbar");
            return false;
        }
        long got = declared
                ? extractAll(handler, running.result(), running.left())
                : handler.extractItem(station.outputSlot(), (int) running.left(), false)
                        .getCount();
        if (got <= 0) {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.WAITING,
                    "wartet auf " + running.device()
                            + (declared ? "" : " — hat er " + station.supply() + "?"));
            return false;
        }
        long rest = storage.insert(running.result(), got);
        if (rest > 0) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(running.result(), (int) rest));
        }
        long done = running.done() + got;
        if (done < running.expected()) {
            job.setRunning(running.withDone(done));
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    done + " von " + running.expected() + " aus " + running.device());
            return true;
        }
        job.setRunning(null);
        if (running.result() == job.target()) {
            job.produced((int) done);
        } else {
            job.note(dev.devpanda.factorynetwork.crafting.CraftingJob.Status.RUNNING,
                    done + " " + running.result().getDescription().getString() + " fertig");
        }
        return true;
    }

    /**
     * Ein Connector, an dem eine passende und freie Maschine hängt.
     *
     * <p><b>Frei heißt: das Ausgangsfach ist leer</b> und ins Eingangsfach
     * passt die Zutat. Wer schon etwas anderes schmilzt, wird nicht
     * angerührt — sonst mischte ein Auftrag sein Erz unter fremde Arbeit.
     */
    private String freeMachine(String station,
            dev.devpanda.factorynetwork.crafting.MachineRecipes.Station shape,
            net.minecraft.world.item.Item ingredient) {
        for (var entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())
                    || !(level.getBlockEntity(entry.getValue())
                            instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            if (!dev.devpanda.factorynetwork.crafting.MachineRecipes.fits(
                    station, connector.machineBlockEntity())) {
                continue;
            }
            var handler = connector.machineInventoryAll();
            if (handler == null || handler.getSlots() <= shape.outputSlot()) {
                continue;
            }
            if (!handler.getStackInSlot(shape.outputSlot()).isEmpty()) {
                continue;
            }
            net.minecraft.world.item.ItemStack probe =
                    new net.minecraft.world.item.ItemStack(ingredient);
            if (handler.insertItem(shape.inputSlot(), probe, true).getCount()
                    < probe.getCount()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private net.neoforged.neoforge.items.IItemHandler machineInventory(String device) {
        var position = graph.connector(device).orElse(null);
        if (position == null || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof ConnectorBlockEntity connector)) {
            return null;
        }
        return connector.machineInventoryAll();
    }

    /** Wie die Maschine einer Station im Klartext heißt. */
    private static String machineName(String station) {
        return dev.devpanda.factorynetwork.crafting.MachineRecipes.machineName(station);
    }

    private static final String KEY_JOBS = "Jobs";
    private static final String KEY_NEXT_JOB = "NextJob";

    /** Unter diesem Namen stehen die Dateien des Projekts. */
    private static final String KEY_FILES = "Files";

    /** Und darunter der Entwurf, der noch nicht übernommen wurde. */
    private static final String KEY_DRAFT = "Draft";
    private static final String KEY_OWNER = "Owner";

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
        writeStatus();
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
            statusStale = true;
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
            note(dev.devpanda.factorynetwork.runtime.LogLevel.ERROR, name,
                    "Fehler beim Übernehmen — das laufende Programm läuft weiter.");
        }
        // In beiden Fällen, und im zweiten erst recht: Wer in VS Code
        // arbeitet, sieht sonst gar nichts — sein Programm läuft still nicht.
        statusStale = true;
    }

    /**
     * Schreibt neben die Programmdateien, was das Spiel über sie weiß.
     *
     * <p>Fehler mit Datei und Zeile, dazu die Namen aus der Welt. Damit
     * bekommt ein Editor daneben beides, was er allein nicht haben kann — und
     * zwar vom Übersetzer, der ohnehin läuft, statt von einer zweiten Fassung
     * derselben Regeln.
     *
     * <p>Gerufen, wenn sich etwas ändert: nach einem Übernehmen, nach einem
     * gescheiterten Übernehmen und nach einem Neuaufbau des Netzes — dort
     * ändern sich die Gerätenamen. Nicht je Tick: {@code ProgramStatus}
     * schreibt zwar nur bei Änderung, aber die Datei jedesmal zu bauen und zu
     * vergleichen wäre Arbeit für nichts.
     */
    private void writeStatus() {
        if (programFolder == null || !statusStale) {
            return;
        }
        statusStale = false;
        dev.devpanda.factorynetwork.lang.ProgramStatus.write(programFolder.path(),
                diagnostics, List.copyOf(graph.connectorNames()), displayNames());
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
        // Sonst bliebe der Reiter leer, bis das nächste Fenster des
        // regelmäßigen Schickens auftut — und wer ihn gezielt aufmacht,
        // hätte den Eindruck, es sei nichts passiert.
        pushLogTo(player);
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
    /** Wer diese Datei gerade hält, oder {@code null}. */
    public java.util.UUID holderOf(String file) {
        return level == null ? null : locks.holderOf(file, level.getGameTime());
    }

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
            pushLogTo(player);
            pushCraftingTo(player);
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
                        power.capacity(), powerDraw(), power.supplied()),
                globalLines()));
    }

    /**
     * Schickt die Fertigungsaufträge an einen Spieler.
     *
     * <p>Der Name des Ziels als fertiger Text: Der Client soll ihn zeigen und
     * nicht auflösen müssen.
     */
    public void pushCraftingTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new dev.devpanda.factorynetwork.network.packet.CraftingStatePacket(
                        craftingLines()));
    }

    /** Die Aufträge als fertige Zeilen — getrennt vom Senden, damit prüfbar. */
    public List<dev.devpanda.factorynetwork.network.packet.CraftingStatePacket.Line>
            craftingLines() {
        List<dev.devpanda.factorynetwork.network.packet.CraftingStatePacket.Line> lines =
                new ArrayList<>(jobs.size());
        for (var job : jobs) {
            lines.add(new dev.devpanda.factorynetwork.network.packet
                    .CraftingStatePacket.Line(job.id(),
                    job.target().getDescription().getString(), job.wanted(), job.done(),
                    job.status().name(), job.detail()));
        }
        return lines;
    }

    /** Schickt das Protokoll an einen Spieler. */
    public void pushLogTo(ServerPlayer player) {
        List<dev.devpanda.factorynetwork.network.packet.LogStatePacket.Line> lines =
                new ArrayList<>(log.size());
        for (dev.devpanda.factorynetwork.runtime.LogEntry entry : log) {
            lines.add(new dev.devpanda.factorynetwork.network.packet.LogStatePacket.Line(
                    entry.level().key(), entry.time(), entry.source(), entry.text()));
        }
        PacketDistributor.sendToPlayer(player,
                new dev.devpanda.factorynetwork.network.packet.LogStatePacket(lines));
    }

    /**
     * Die globalen Werte als Zeilen für das Terminal.
     *
     * <p><b>Ein Wert, den man nicht sehen kann, ist beim Fehlersuchen
     * wertlos.</b> Der Umweg wäre ein {@code log()} in einer Schleife — und
     * das ist die Sorte Notlösung, die man dann vergisst herauszunehmen.
     */
    public List<String> globalLines() {
        List<String> lines = new ArrayList<>();
        globals.forEach((name, value) -> lines.add(name + " = " + value.describe()));
        return lines;
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
        DisplayValues values = new DisplayValues(graph, storage, runtime, globals, level);
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Display display)) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            List<DisplayStatePacket.Button> buttons = new ArrayList<>();
            List<DisplayValues.Line> evaluated = values.evaluate(display);
            // Der Eintrag zählt für sich: Eine Aufzählung bringt mehrere
            // Zeilen mit, und die Zeilennummer läuft danach voraus.
            int entryIndex = -1;
            Decl.Display.Entry.Kind previous = null;
            for (int i = 0; i < evaluated.size(); i++) {
                DisplayValues.Line line = evaluated.get(i);
                if (belongsToPreviousEntry(line, previous)) {
                    lines.add(DisplayBlockEntity.format(line));
                    continue;
                }
                entryIndex++;
                previous = line.kind();
                lines.add(DisplayBlockEntity.format(line));
                if (line.kind() == Decl.Display.Entry.Kind.BUTTON) {
                    buttons.add(new DisplayStatePacket.Button(i, entryIndex));
                }
            }
            panels.add(new DisplayStatePacket.Panel(display.name(), lines, buttons));
        }
        addUnknownPanels(panels);
        return panels;
    }

    /**
     * Gehört diese Zeile noch zum Eintrag davor?
     *
     * <p>Nur eine Aufzählung bringt mehr als eine Zeile mit: Auf ihre
     * Überschrift folgen die Posten als Zeilen, bis der nächste Eintrag
     * beginnt. Alles andere ist eins zu eins.
     */
    private static boolean belongsToPreviousEntry(DisplayValues.Line line,
                                                  Decl.Display.Entry.Kind previous) {
        if (previous != Decl.Display.Entry.Kind.LIST) {
            return false;
        }
        return line.kind() == Decl.Display.Entry.Kind.ROW
                || line.kind() == Decl.Display.Entry.Kind.TEXT;
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
            note(dev.devpanda.factorynetwork.runtime.LogLevel.ERROR, display.name(),
                    "Der Knopf " + entry.label() + " nennt keine Funktion.");
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

    /** Die zuletzt gesehenen Mengen je Gerät — die Grundlinie für device_output. */
    private final Map<String, DeviceAmounts> lastAmounts = new HashMap<>();

    /**
     * Meldet, was an den Geräten geschieht.
     *
     * <p><b>{@code device_changed}: irgendetwas hat sich geregt.</b> Mehr
     * sagt es nicht, und mehr kann es nicht sagen — auch das Verbrennen von
     * Kohle ist eine Änderung. Was das bedeutet, schreibt der Spieler selbst.
     *
     * <p><b>{@code device_output}: es ist etwas dazugekommen.</b> Verglichen
     * werden die Mengen je Art mit denen vom letzten Blick; nur mehr zählt,
     * also lösen Verbrauch und Entnahme nie etwas aus. Was das Netz selbst
     * einlegt, zieht die Grundlinie sofort nach (siehe {@link #noteFilled})
     * und geht deshalb nie als Ausgabe durch. Gemeldet wird jeder Zuwachs
     * und nicht nur der erste: Eine Maschine, die eine Ladung stückweise
     * ausgibt, meldet jedes Stück — sonst bliebe der Rest in ihr stehen.
     *
     * <p><b>Nicht „fertig".</b> Ob eine Maschine ihre Arbeit beendet hat,
     * weiß von außen niemand: Der Ausgang kann von vorher gefüllt sein, und
     * jede Mod zählt anders. Eine Automatisierung, die einmal zu früh
     * weiterschaltet, verliert Gegenstände in einer Kiste, die niemand mehr
     * findet — deshalb sagt der Name, was gemessen wird, und nicht, was der
     * Spieler daraus schließen möchte.
     *
     * <p>Abgefragt statt gemeldet und nur alle zehn Ticks, wie beim Redstone —
     * und je Ereignis nur, wenn das Programm überhaupt darauf hört.
     */
    private void fireInventoryEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        boolean watchChanges = listensTo(BuiltinEvents.DEVICE_CHANGED);
        boolean watchOutput = listensTo(BuiltinEvents.DEVICE_OUTPUT);
        if (!watchChanges) {
            lastContents.clear();
        }
        if (!watchOutput) {
            lastAmounts.clear();
        }
        if (!watchChanges && !watchOutput) {
            return;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!level.isLoaded(entry.getValue())
                    || !(level.getBlockEntity(entry.getValue())
                            instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            // Beim ersten Sehen wird nicht gemeldet: Da hat sich nichts
            // geändert, es war nur nichts bekannt.
            if (watchChanges) {
                int fingerprint = contentsFingerprint(connector);
                Integer previous = lastContents.put(entry.getKey(), fingerprint);
                if (previous != null && previous != fingerprint) {
                    fireEvent(BuiltinEvents.DEVICE_CHANGED,
                            List.of(new Value.Device(entry.getKey())));
                }
            }
            if (watchOutput) {
                DeviceAmounts amounts = DeviceAmounts.of(connector);
                DeviceAmounts previous = lastAmounts.put(entry.getKey(), amounts);
                if (previous != null && amounts.hasMoreThan(previous)) {
                    fireEvent(BuiltinEvents.DEVICE_OUTPUT,
                            List.of(new Value.Device(entry.getKey())));
                }
            }
        }
    }

    /**
     * Das Netz hat gerade selbst etwas in ein Gerät gelegt.
     *
     * <p>Die Grundlinie wird sofort nachgezogen, damit die eigene Lieferung
     * beim nächsten Blick nicht als Ausgabe des Geräts durchgeht. Ohne das
     * weckte ein Ablauf, der einlegt und dann wartet, sich selbst.
     *
     * <p>Nur für Geräte, die schon beobachtet werden. Wer noch keine
     * Grundlinie hat, bekommt sie beim nächsten Blick — und der erste Blick
     * meldet nie.
     */
    public void noteFilled(String device) {
        lastAmounts.computeIfPresent(device, (name, previous) -> {
            ConnectorBlockEntity connector = connectorNamed(name);
            // Ein Gerät, das gerade nicht erreichbar ist, behält seine alte
            // Grundlinie. Eine leere hieße: Beim nächsten Blick ist alles
            // darin neu.
            return connector == null ? previous : DeviceAmounts.of(connector);
        });
    }

    /** Die BlockEntity des Connectors mit diesem Namen, oder {@code null}. */
    private ConnectorBlockEntity connectorNamed(String device) {
        BlockPos position = graph.connectors().get(device);
        if (position == null || level == null || !level.isLoaded(position)) {
            return null;
        }
        return level.getBlockEntity(position) instanceof ConnectorBlockEntity connector
                ? connector : null;
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

    /**
     * Hört irgendwer auf dieses Ereignis?
     *
     * <p><b>Ein wartender Ablauf zählt mit.</b> Gezählt wurden lange nur die
     * {@code on}-Blöcke, und damit stand ein
     *
     * <pre>let gerät = await device_changed</pre>
     *
     * für immer: Ohne Block wurde gar nicht erst hingesehen, das Ereignis
     * fiel nie, und der Ablauf wartete auf etwas, das niemand mehr auslöste.
     * Die Abfrage ist teuer genug, um sie zu sparen — aber nur, wenn wirklich
     * niemand zuhört.
     */
    private boolean listensTo(String event) {
        if (program.handlers().stream().anyMatch(handler -> handler.name().equals(event))) {
            return true;
        }
        return flows != null && flows.awaits(event);
    }

    private void fireRedstoneEvents() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (!listensTo(BuiltinEvents.REDSTONE_CHANGED)) {
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
            fireEvent(BuiltinEvents.REDSTONE_CHANGED,
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
                    newHost()));
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
        WorldHost host = newHost();
        Interpreter interpreter = new Interpreter(program, host);
        FlowEngine engine = flowEngine();
        if (engine != null) {
            // Auch ein Aufruf aus dem Terminal kann etwas auslösen, worauf
            // ein Ablauf wartet.
            interpreter.setEventSink(engine::post);
        }
        interpreter.setLogSource(name);
        try {
            return interpreter.call(name, arguments);
        } finally {
            if (engine != null) {
                engine.tick(level.getGameTime());
            }
        }
    }

    public List<dev.devpanda.factorynetwork.runtime.LogEntry> log() {
        return List.copyOf(log);
    }

    /** Löscht das Protokoll — nur auf ausdrücklichen Wunsch im Terminal. */
    public void clearLog() {
        log.clear();
        setChanged();
    }

    /**
     * Ein Host, der ins Protokoll schreibt.
     *
     * <p>An einer Stelle gebaut, weil es sonst drei sind — für die Worker,
     * für die Ablaufmaschine und für den Aufruf aus dem Terminal. Eine davon
     * wurde vergessen, und die Meldungen der Abläufe kamen nirgends an.
     */
    private WorldHost newHost() {
        WorldHost host = new WorldHost(level, graph, storage, fluidStorage, globals,
                this::setChanged);
        host.setLogSink(this::add);
        host.setChemicals(chemicalStorage);
        host.setDeviceFilled(this::noteFilled);
        // Dieselben Gruppen wie die Worker, samt ihrem Zeiger für round_robin.
        host.setGroups(runtime.groups());
        // Bestellungen gehen an dieselbe Liste, die auch der Reiter zeigt.
        host.setCrafting((item, amount) -> {
            var job = requestCraft(item, amount);
            return job == null ? 0 : job.id();
        });
        return host;
    }

    private void note(String message) {
        note(dev.devpanda.factorynetwork.runtime.LogLevel.INFO, "", message);
    }

    private void note(dev.devpanda.factorynetwork.runtime.LogLevel level,
                      String source, String message) {
        add(new dev.devpanda.factorynetwork.runtime.LogEntry(
                level, System.currentTimeMillis(), source, message));
    }

    private void add(dev.devpanda.factorynetwork.runtime.LogEntry entry) {
        log.add(entry);
        while (log.size() > LOG_LIMIT) {
            log.remove(0);
        }
        setChanged();
    }

    /**
     * Wer den Controller gesetzt hat, oder {@code null}.
     *
     * <p>Gebraucht nur, wenn der Server es verlangt (siehe {@code FnConfig},
     * Abschnitt {@code protection}). Gemerkt wird es trotzdem immer: Wer den
     * Schutz erst später einschaltet, hätte sonst lauter herrenlose Anlagen.
     */
    private java.util.UUID owner;

    public java.util.UUID owner() {
        return owner;
    }

    /** Setzt der Block beim Platzieren. */
    public void setOwner(java.util.UUID who) {
        this.owner = who;
        setChanged();
    }

    // ---- Speichern --------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        jobs.clear();
        nextJobId = Math.max(1, tag.getLong(KEY_NEXT_JOB));
        net.minecraft.nbt.ListTag jobList = tag.getList(KEY_JOBS,
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < jobList.size(); i++) {
            var job = dev.devpanda.factorynetwork.crafting.CraftingJob
                    .load(jobList.getCompound(i));
            if (job != null) {
                jobs.add(job);
            }
        }
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
        globals.clear();
        CompoundTag globalsTag = tag.getCompound(KEY_GLOBALS);
        for (String name : globalsTag.getAllKeys()) {
            globals.put(name, dev.devpanda.factorynetwork.runtime.flow.ValueCodec
                    .read(globalsTag.getCompound(name)));
        }
        log.clear();
        net.minecraft.nbt.ListTag logTag =
                tag.getList(KEY_LOG, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < logTag.size(); i++) {
            log.add(dev.devpanda.factorynetwork.runtime.LogEntry
                    .read(logTag.getCompound(i)));
        }
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
        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
        net.minecraft.nbt.ListTag jobList = new net.minecraft.nbt.ListTag();
        jobs.forEach(job -> jobList.add(job.save()));
        tag.put(KEY_JOBS, jobList);
        tag.putLong(KEY_NEXT_JOB, nextJobId);
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
        CompoundTag globalsTag = new CompoundTag();
        globals.forEach((name, value) -> globalsTag.put(name,
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(value)));
        tag.put(KEY_GLOBALS, globalsTag);
        net.minecraft.nbt.ListTag logTag = new net.minecraft.nbt.ListTag();
        log.forEach(entry -> logTag.add(entry.write()));
        tag.put(KEY_LOG, logTag);
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
