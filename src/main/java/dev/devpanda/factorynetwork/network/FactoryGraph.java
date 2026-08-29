package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.ControllerExtensionBlock;
import dev.devpanda.factorynetwork.block.DisplayBlock;
import dev.devpanda.factorynetwork.block.DriveBlock;
import dev.devpanda.factorynetwork.block.FabricatorBlock;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.RouterBlock;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.util.NameDistance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Das Netzwerk eines Controllers: alle Connectoren, die über Kabel mit ihm
 * verbunden sind.
 *
 * <p>Der Graph wird nicht dauernd gepflegt, sondern neu aufgebaut, wenn sich
 * etwas geändert hat. Das ist für die Größenordnung, um die es geht — einige
 * hundert Blöcke — deutlich einfacher als eine mitwachsende Struktur, und ein
 * Kabel wird selten gesetzt.
 *
 * <p>Nicht geladene Chunks werden übersprungen. Ein Connector dort ist nicht
 * weg, sondern vorübergehend nicht erreichbar — der Unterschied steht in
 * {@code sprache.md}, Abschnitt 14.
 */
public final class FactoryGraph {

    /**
     * Weiter als das sucht der Aufbau nicht; schützt vor Endlosnetzen.
     *
     * <p>Einstellbar in der Serverkonfiguration — der Betreiber eines Packs
     * kennt seine Spieler und seine Hardware, die Mod nicht.
     */
    private static int maxNodes() {
        return dev.devpanda.factorynetwork.FnConfig.networkNodes();
    }

    /**
     * Name auf Stellen. Mehr als eine bedeutet: Der Name ist doppelt
     * vergeben und damit unbrauchbar — siehe {@link #isAmbiguous}.
     *
     * <p>Eine Stelle ist Ort <b>und</b> Fläche ({@link DevicePos}): An einem
     * Kabelblock sitzen bis zu sechs Anschlüsse, und zwei davon sind zwei
     * Geräte mit zwei Namen.
     */
    private final Map<String, List<DevicePos>> connectorsByName;
    /**
     * Connectoren ohne Namen.
     *
     * <p>Sie lassen sich nicht ansprechen — dafür braucht es einen Namen —,
     * aber sie hängen im Netz, und der Spieler muss das sehen. Sie hier
     * wegzulassen hieß, dass der Controller „0 Connectoren" meldete, während
     * drei danebenhingen.
     */
    private final List<DevicePos> unnamed;
    /** Geräte, die im Netz hängen, aber keinen freien Kanal bekommen haben. */
    /**
     * Über welche Kabel ein Gerät arbeitet.
     *
     * <p>Bis zum 29.08. stand hier {@code starved}: Geräte, für die kein
     * Kanal mehr frei war. Es gibt sie nicht mehr — wer erreichbar ist, hängt
     * am Netz. Was knapp werden kann, ist der Durchsatz, und der steht am
     * Weg.
     */
    private final Map<DevicePos, List<Node>> paths;
    /** Displays am Netz. Sie zeigen nur an und brauchen keinen Kanal. */
    private final List<BlockPos> displays;
    private final Set<BlockPos> cables;
    /** Router am Netz. Sie leiten weiter und kosten selbst keinen Kanal. */
    private final Set<BlockPos> routers;

    /**
     * Geräte, die zwei Gateways beanspruchen — sie gehören zu keiner Anlage.
     *
     * <p>Welches gewönne, hinge an der Suchreihenfolge. Geraten wird nicht;
     * gemeldet schon.
     */
    private final Set<BlockPos> contested;
    /** Laufwerke am Netz. Sie tragen den Speicher, den das Netz benutzt. */
    private final List<BlockPos> drives;
    /** Serverschränke am Netz. Sie tragen die Rechenleistung. */
    private final List<BlockPos> racks;
    /** Fabricators am Netz. Jeder erlaubt einen Fertigungsschritt je Takt. */
    private final List<BlockPos> fabricators;
    /**
     * Die Anbauten am Controller.
     *
     * <p>Sie hängen nicht am Netz, sie <b>sind</b> das Netz — jeder bringt
     * eigene Seiten mit, an denen ein Strang beginnen kann. Aufgehoben werden
     * sie für den Strombedarf und für den Analysator.
     */
    private final List<BlockPos> extensions;
    /** Wie viele Kanäle jeder Kabelstrang trägt. */

    /**
     * Wer mit wem verbunden ist.
     *
     * <p>Die Suche kennt das ohnehin — sie merkt sich zu jedem Knoten, woher
     * sie kam. Aufgehoben wird es für den Netzanalysator, der das Netz als
     * Linien in die Welt zeichnet. Ohne die Kanten bliebe ihm nur eine Wolke
     * von Punkten.
     */
    private final List<Edge> edges;
    private final List<BlockPos> masts;
    private final Set<BlockPos> bridges;
    private final boolean truncated;

    /** Eine Verbindung zwischen zwei Knoten des Netzes. */
    public record Edge(Node from, Node to) {}

    private FactoryGraph(Map<String, List<DevicePos>> connectorsByName,
                         List<DevicePos> unnamed,
                         Map<DevicePos, List<Node>> paths, List<BlockPos> displays,
                         Set<BlockPos> cables,
                         Set<BlockPos> routers, List<Edge> edges,
                         List<BlockPos> drives, List<BlockPos> racks, List<BlockPos> extensions,
                         List<BlockPos> fabricators, Set<BlockPos> contested,
                         List<BlockPos> masts, Set<BlockPos> bridges,
                         boolean truncated) {
        this.masts = masts;
        this.bridges = bridges;
        this.contested = contested;
        this.fabricators = fabricators;
        this.extensions = extensions;
        this.routers = routers;
        this.drives = drives;
        this.racks = racks;
        this.displays = displays;
        this.connectorsByName = connectorsByName;
        this.unnamed = unnamed;
        this.paths = paths;

        this.cables = cables;
        this.edges = edges;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), List.of(), Map.of(), List.of(),
                Set.of(), Set.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Set.of(), List.of(), Set.of(), false);
    }

    /** Die Fabricators am Netz. */
    public List<BlockPos> fabricators() {
        return fabricators;
    }

    /**
     * Die Sendemasten am Netz.
     *
     * <p>Der Controller braucht sie für die Stromrechnung: Was ein Mast
     * zieht, hängt daran, was in ihm steckt.
     */
    public List<BlockPos> masts() {
        return masts;
    }

    /**
     * Die Quantum-Brücken am Netz.
     *
     * <p>Immer paarweise, wenn beide Enden geladen sind — sonst steht nur
     * das diesseitige darin, und der Weg endet dort.
     */
    public Set<BlockPos> bridges() {
        return bridges;
    }

    /** Die Anbauten am Controller. */
    public List<BlockPos> extensions() {
        return extensions;
    }

    /** Die Laufwerke am Netz. */
    public List<BlockPos> drives() {
        return drives;
    }

    /** Die Serverschränke am Netz. */
    public List<BlockPos> racks() {
        return racks;
    }

    /** Die Verbindungen des Netzes, für die Anzeige im Raum. */
    public List<Edge> edges() {
        return edges;
    }

    /**
     * Ein Knoten der Suche: eine Stelle und ein Strang.
     *
     * <p>Ein Kabelblock ist nicht ein Knoten, sondern bis zu vier — sonst
     * liefe ein grüner Strang über einen Block, in dem auch ein roter liegt,
     * und beide wären plötzlich verbunden.
     */
    public record Node(BlockPos pos, CableColour colour, int lane) {

        /**
         * Ein Knoten ohne Bahn — alles außer einem Router.
         *
         * <p>Die Bahn trennt nur innerhalb eines Routers. Ein Kabel hat
         * keine, also steht dort {@link RouterBlockEntity#OFF}, und die
         * bisherigen Aufrufer bleiben, wie sie sind.
         */
        public Node(BlockPos pos, CableColour colour) {
            this(pos, colour, RouterBlockEntity.OFF);
        }
    }


    /**
     * Baut den Graphen ausgehend vom Controller auf.
     *
     * <p>Die Breitensuche geht in fester Richtungsfolge — {@code Direction}
     * in seiner eigenen Reihenfolge — und sammelt Geräte nach Entfernung.
     * <b>Damit gewinnt bei knappen Kanälen immer das nähere Gerät</b>, und bei
     * gleicher Entfernung das in der früheren Richtung. Diese Regel muss
     * feststehen und erklärbar sein: Sonst ist „warum ist dieses Gerät
     * offline" nicht zu beantworten.
     */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, List<DevicePos>> connectors = new LinkedHashMap<>();
        List<DevicePos> unnamed = new ArrayList<>();
        // Der Weg jedes Geräts zum Controller. Er begrenzt nicht mehr,
        // wer angeschlossen ist — er sagt, über welche Kabel ein Gerät
        // arbeitet, und daran hängt der Durchsatz.
        Map<DevicePos, List<Node>> paths = new java.util.LinkedHashMap<>();
        List<BlockPos> displays = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> routers = new HashSet<>();
        List<BlockPos> masts = new ArrayList<>();
        Set<BlockPos> bridges = new HashSet<>();
        Set<BlockPos> gateways = new LinkedHashSet<>();
        List<BlockPos> drives = new ArrayList<>();
        List<BlockPos> racks = new ArrayList<>();
        List<BlockPos> fabricators = new ArrayList<>();

        Map<Node, Node> parents = new HashMap<>();
        // Gerät auf die Kabelstränge, über die es erreichbar ist — in der
        // Reihenfolge, in der die Suche sie gefunden hat.
        //
        // Der Schlüssel ist Ort und Fläche und nicht nur der Ort: Sechs
        // Anschlüsse an einem Kabelblock sind sechs Geräte, jedes mit
        // eigenem Namen und eigenem Kanalbedarf.
        Map<DevicePos, List<Node>> reachable = new LinkedHashMap<>();
        Map<DevicePos, Consumer> kinds = new LinkedHashMap<>();
        Deque<Node> queue = new ArrayDeque<>();

        // Der Controller und seine Anbauten sind farbneutral: Von jedem von
        // ihnen gehen alle Stränge aus, die an ihm hängen. Mehrere Wurzeln
        // sind genau der Sinn des Anbaus — sechs Seiten je Block.
        List<BlockPos> extensions = extensionsAround(level, controller);
        Node root = new Node(controller, CableColour.NONE);
        queue.add(root);
        parents.put(root, null);
        for (BlockPos extension : extensions) {
            Node node = new Node(extension, CableColour.NONE);
            queue.add(node);
            parents.put(node, null);
        }
        boolean truncated = false;

        while (!queue.isEmpty()) {
            if (parents.size() > maxNodes()) {
                truncated = true;
                break;
            }
            Node current = queue.poll();
            // Steht die Suche in einem Router, gilt sie nur für eine Bahn.
            // Der Filter muss vor allem anderen stehen, nicht nur vor dem
            // Kabelzweig: Auch ein Gerät an einer fremden Bahn gehört nicht
            // dazu.
            // Am Block gefragt und nicht am Knoten: Der Knoten trug früher
            // die Bahnnummer, und die gibt es nicht mehr — ein Router ist
            // jetzt ein Knoten, nicht vier.
            boolean atRouter = level.getBlockState(current.pos()).getBlock()
                    instanceof RouterBlock;
            for (Direction direction : Direction.values()) {
                // Aus einem Router hinaus nur, wo die Seite es zulässt.
                // Vorher stand hier ein Bahnvergleich; jetzt entscheidet der
                // Filter der Austrittsseite — siehe mayLeaveRouter.
                if (atRouter && !mayLeaveRouter(level, current.pos(), direction,
                        current.colour())) {
                    continue;
                }
                BlockPos next = current.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    // Ein nicht geladener Chunk beendet den Zweig, ohne Fehler.
                    continue;
                }
                BlockState state = level.getBlockState(next);
                // Ein Halter ohne Kabel leitet nicht. Sein Anschluss sitzt
                // darin und wartet auf ein Kabel — bis dahin gehört er zu
                // keinem Netz.
                if (state.getBlock() instanceof CableBlock
                        && dev.devpanda.factorynetwork.block.CableBlock.carries(state)) {
                    Node node = visitCable(level, next, current, parents, queue, cables);
                    // Jeder Anschluss an einer Fläche dieses Kabels ist ein
                    // eigenes Gerät — und sein Weg zum Controller führt über
                    // das Kabel selbst, nicht über das davor.
                    if (node != null) {
                        for (Direction side : partsAt(level, next)) {
                            DevicePos device = DevicePos.of(next, side);
                            reachable.computeIfAbsent(device, key -> new ArrayList<>())
                                    .add(node);
                            kinds.put(device, Consumer.CONNECTOR);
                        }
                    }
                } else if (state.getBlock()
                        instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
                    // Er reicht den Strang durch wie ein Kabel und vermehrt
                    // dabei nichts: Seine Kanalgrenze steht in capacityAt.
                    // Was er zusätzlich tut — der Umgebung einen
                    // Anlagennamen geben —, entscheidet die Nachbarschaft am
                    // Kabel und nicht der Weg zum Controller; deshalb steht
                    // es in GatewayRegions und nicht hier.
                    visitCable(level, next, current, parents, queue, cables);
                    gateways.add(next.immutable());
                } else if (state.getBlock()
                        instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
                    visitBridge(level, next, current, parents, queue, bridges);
                } else if (state.getBlock() instanceof RouterBlock) {
                    visitRouter(level, next, direction, current, parents, queue, routers);
                } else {
                    // Alles, was einen Kanal kostet, geht denselben Weg —
                    // noch nicht zuteilen: Ein Gerät kann an mehreren
                    // Strängen hängen, und welcher es trägt, entscheidet
                    // sich erst, wenn alle Wege bekannt sind.
                    Consumer kind = consumerAt(state);
                    if (kind != null) {
                        DevicePos device = deviceAt(state, next);
                        reachable.computeIfAbsent(device, key -> new ArrayList<>())
                                .add(current);
                        kinds.put(device, kind);
                        // Anzeigen leiten weiter. Eine Wand aus sechs Tafeln
                        // ist ein Bild und keine sechs Geräte, und hinter
                        // jede ein Kabel zu legen ist Arbeit ohne
                        // Entscheidung. Alle anderen Geräte enden hier: Wer
                        // zwei Laufwerke aneinanderstellt, meint zwei
                        // Laufwerke und keine Leitung.
                        if (kind == Consumer.DISPLAY) {
                            visitDisplay(next, current, parents, queue);
                        }
                    }
                }
            }
        }

        // Erst jetzt, weil erst jetzt feststeht, welche Gateways im Netz
        // hängen. Ein Gateway ohne Kabel zum Controller gehört nicht dazu
        // und benennt deshalb auch nichts.
        GatewayRegions regions = gateways.isEmpty()
                ? GatewayRegions.EMPTY : GatewayRegions.of(level, gateways, maxNodes());
        assignPaths(level, reachable, kinds, parents, connectors, unnamed,
                paths, displays, drives, racks, fabricators, masts, regions);

        Map<String, List<DevicePos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        List<Edge> edges = new ArrayList<>();
        parents.forEach((node, parent) -> {
            if (parent != null) {
                edges.add(new Edge(parent, node));
            }
        });
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                Map.copyOf(paths), List.copyOf(displays), Set.copyOf(cables),
                Set.copyOf(routers), List.copyOf(edges),
                List.copyOf(drives), List.copyOf(racks), List.copyOf(extensions),
                List.copyOf(fabricators), Set.copyOf(regions.contested()),
                List.copyOf(masts), Set.copyOf(bridges), truncated);
    }

    /**
     * Die Anbauten, die den Controller berühren — unmittelbar oder über
     * andere Anbauten.
     *
     * <p><b>Nur über Flächen, nie über ein Kabel.</b> Ließe sich ein Anbau
     * ankabeln, wäre er ein beliebig oft setzbarer Kanalvermehrer: sechs neue
     * Seiten für einen Block, irgendwo im Gelände, und die Kanalgrenze
     * bedeutete nichts mehr. Wer ausbauen will, baut am Controller.
     *
     * <p>Eine Obergrenze gibt es nicht. Sechs Seiten je Block, und jeder
     * Anbau bringt neue — begrenzt wird das von der Bauform und vom
     * Strombedarf, nicht von einer Zahl im Code.
     */
    private static List<BlockPos> extensionsAround(Level level, BlockPos controller) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(controller.immutable());
        queue.add(controller.immutable());
        while (!queue.isEmpty() && found.size() < maxNodes()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof ControllerExtensionBlock) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * Was am Netz ein Gerät ist.
     *
     * <p><b>Ohne Kosten seit dem 29.08.</b> Jede dieser Arten kostete
     * einen Kanal oder keinen; Kanäle gibt es nicht mehr. Was bleibt, ist
     * die Art selbst — der Suchlauf muss wissen, in welche Liste ein Fund
     * gehört.
     */
    private enum Consumer {
        CONNECTOR,
        DRIVE,
        RACK,
        FABRICATOR,
        DISPLAY,
        MAST
    }

    /**
     * Wo ein Gerät zählt, wenn es mehr als einen Block belegt.
     *
     * <p>Der Serverschrank ist zwei Blöcke hoch und trotzdem ein Gerät: ein
     * Kanal, eine BlockEntity, ein Eintrag in der Liste. Wer oben ankabelt,
     * kabelt denselben Schrank an — deshalb steht die obere Hälfte hier auf
     * die untere um, statt ein zweites Mal zu zählen.
     *
     * <p>Eine Fläche bekommt nur ein Anschluss. Alles andere ist ein ganzer
     * Block: Wer ein Laufwerk von zwei Seiten ankabelt, hat ein Laufwerk und
     * nicht zwei.
     */
    private static DevicePos deviceAt(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof RackBlock) {
            return DevicePos.of(RackBlock.baseOf(state, pos));
        }
        return DevicePos.of(pos);
    }

    /**
     * Die Flächen eines Kabelblocks, an denen ein Anschluss sitzt.
     *
     * <p>Leer bei jedem gewöhnlichen Kabel — und das ist der Normalfall, den
     * die Suche tausendfach durchläuft.
     */
    private static List<Direction> partsAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus
                ? List.copyOf(bus.parts().keySet()) : List.of();
    }

    private static Consumer consumerAt(BlockState state) {
        // Ein Anschluss steht hier nicht mehr: Er ist kein Block, sondern
        // ein Teil an einer Kabelfläche, und den findet die Suche im
        // Kabelzweig.
        if (state.getBlock() instanceof DriveBlock) {
            return Consumer.DRIVE;
        }
        if (state.getBlock() instanceof RackBlock) {
            return Consumer.RACK;
        }
        if (state.getBlock() instanceof FabricatorBlock) {
            return Consumer.FABRICATOR;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.MastBlock) {
            return Consumer.MAST;
        }
        if (state.getBlock() instanceof DisplayBlock) {
            return Consumer.DISPLAY;
        }
        return null;
    }

    /**
     * Geht in einen Kabelblock hinein — aber nur, wenn seine Farbe zu der
     * passt, aus der man kommt.
     */
    private static Node visitCable(Level level, BlockPos pos, Node from,
                                   Map<Node, Node> parents,
                                   Deque<Node> queue, Set<BlockPos> cables) {
        CableColour colour = CableBlock.colourOf(level.getBlockState(pos));
        if (!from.colour().connectsTo(colour)) {
            return null;
        }
        Node node = new Node(pos.immutable(), colour);
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
            cables.add(pos.immutable());
        }
        // Auch ein schon besuchtes Kabel darf seine Teile beisteuern: Der
        // Knoten ist derselbe, und über ihn läuft ihr Weg zum Controller.
        return node;
    }

    /**
     * Springt durch eine Quantum-Brücke zu ihrer Gegenstelle.
     *
     * <p>Die Brücke ist ein Stück Leitung, das an zwei Orten liegt. Wer sie
     * betritt, steht danach am anderen Ende — mit der Farbe, mit der er sie
     * erreicht hat, denn eine Leitung ändert ihre Farbe nicht unterwegs.
     *
     * <p><b>Ist die Gegenstelle nicht geladen, endet der Weg hier.</b>
     * Dieselbe Regel wie beim Sendemast: {@code getBlockEntity} lüde sonst
     * nach, und diese Frage fällt bei jedem Neuaufbau des Netzes.
     *
     * <p>Das Ping-Pong zwischen den beiden Enden fängt {@code parents}: Ein
     * Knoten, der schon einen Weg hat, bekommt keinen zweiten.
     */
    private static void visitBridge(Level level, BlockPos pos, Node from,
                                    Map<Node, Node> parents, Deque<Node> queue,
                                    Set<BlockPos> bridges) {
        Node here = new Node(pos.immutable(), from.colour());
        if (!parents.containsKey(here)) {
            parents.put(here, from);
            bridges.add(pos.immutable());
        }
        BlockPos partner = dev.devpanda.factorynetwork.network.BridgeRegistry
                .partnerOf(level, pos);
        if (partner == null || !level.isLoaded(partner)) {
            return;
        }
        Node there = new Node(partner.immutable(), from.colour());
        if (!parents.containsKey(there)) {
            parents.put(there, here);
            queue.add(there);
            bridges.add(partner.immutable());
        }
    }

    /**
     * Geht durch eine Anzeige hindurch.
     *
     * <p>Eine Anzeige führt den Strang weiter, mit der Farbe, mit der sie
     * erreicht wurde. Sie ist damit ein Stück derselben Leitung: Eine rote
     * Wand bleibt rot, und zwei verschiedenfarbige Netze wachsen nicht über
     * eine Anzeige zusammen.
     *
     * <p>Sie trägt dabei keine eigene Kanalgrenze — {@code capacityAt} gibt
     * für alles, was kein Kabel ist, unbegrenzt zurück. Was eine Wand
     * kostet, begrenzt das Kabel, an dem sie hängt, und das ist die Stelle,
     * an der man es auch nachsieht.
     */
    private static void visitDisplay(BlockPos pos, Node from, Map<Node, Node> parents,
                                     Deque<Node> queue) {
        Node node = new Node(pos.immutable(), from.colour());
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
        }
    }

    /**
     * Geht in einen Router hinein — auf der Bahn, die die berührte Seite
     * führt.
     *
     * <p>Die berührte Seite ist die dem Weg zugewandte, also die Gegenseite
     * der Richtung, in die man gegangen ist. Ist sie abgeklemmt, endet der
     * Zweig; das ist der ganze Zweck einer abgeklemmten Seite.
     */
    private static void visitRouter(Level level, BlockPos pos, Direction direction, Node from,
                                    Map<Node, Node> parents, Deque<Node> queue,
                                    Set<BlockPos> routers) {
        Direction eintritt = direction.getOpposite();
        int lane = RouterBlockEntity.laneAt(level, pos, eintritt);
        if (lane == RouterBlockEntity.OFF) {
            return;
        }
        // <b>Der Router filtert, er mischt nicht.</b> Bis zum 29.08. war der
        // Knoten farbneutral: Was auf einer Bahn zusammenkam, galt als
        // verbunden — zwei getrennte Teilnetze wuchsen darüber zusammen.
        //
        // Jetzt trägt der Knoten eine Farbe. Eine Seite ohne Filter reicht
        // die Farbe durch, mit der es ankam; eine mit Filter lässt nur diese
        // eine durch. Damit treffen sich zwei Farben im selben Block, ohne
        // einander zu berühren — das Glasfaser-Bild.
        CableColour filter = RouterBlockEntity.filterAt(level, pos, eintritt);
        if (filter != null && !filter.connectsTo(from.colour())) {
            return;
        }
        // <b>Ein Router ist ein Knoten, nicht vier.</b> Vorher trug der
        // Knoten die Bahnnummer der Eintrittsseite — und reichte damit nie
        // zu einer Seite mit anderer Nummer hinaus. Vier Bahnen waren vier
        // Router im selben Block.
        //
        // Jetzt ist er ein Durchgang, und die Filter sitzen an seinen
        // Seiten: Was hereindarf, entscheidet der Eintritt; was hinausdarf,
        // der Austritt. Ein roter Ausgang lässt nur Rotes hinaus.
        //
        // Die Farbe des Knotens ist die, mit der es ankam — der Filter
        // entscheidet, ob etwas hindurchdarf, nicht, was es danach ist.
        Node node = new Node(pos.immutable(), from.colour());
        routers.add(pos.immutable());
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
        }
    }

    /**
     * Darf diese Farbe hier hinaus?
     *
     * <p>Die Gegenprobe zum Eintritt: Der Router hat je Seite einen Filter,
     * und beide Richtungen zählen. Ohne diese Frage wäre ein roter Ausgang
     * eine Beschriftung ohne Wirkung — es käme heraus, was hereinkam.
     */
    private static boolean mayLeaveRouter(Level level, BlockPos pos, Direction side,
                                          CableColour colour) {
        if (RouterBlockEntity.laneAt(level, pos, side) == RouterBlockEntity.OFF) {
            return false;
        }
        CableColour filter = RouterBlockEntity.filterAt(level, pos, side);
        if (filter == null) {
            return true;
        }
        // <b>Die Farbe des Kabels dahinter entscheidet, nicht die des
        // Strangs davor.</b> Ein roter Ausgang führt in ein rotes Kabel;
        // liegt dort ein blaues, geht nichts hinaus.
        //
        // connectsTo allein reichte nicht: Neutral verbindet sich mit allem,
        // und ein neutrales Kabel käme durch jeden Filter — ein Filter, den
        // alles passiert, ist keiner.
        CableColour dahinter = CableBlock.colourOf(
                level.getBlockState(pos.relative(side)));
        return filter == dahinter;
    }

    /**
     * Teilt den Geräten ihre Kanäle zu.
     *
     * <p>Läuft erst, wenn die Suche durch ist — vorher weiß man nicht, über
     * welche Stränge ein Gerät überhaupt erreichbar ist. <b>Ein Gerät nimmt
     * den ersten Strang, auf dessen Weg noch Platz ist</b>, nicht einfach den
     * erstgefundenen: Sonst bliebe ein Gerät hungrig, während im selben Block
     * ein freier Strang liegt. Genau das heißt „Kanäle je Kabel, nicht je
     * Bündel".
     *
     * <p>Die Reihenfolge ist die der Suche, also nach Entfernung: Bei knappen
     * Kanälen gewinnt das nähere Gerät.
     */
    private static void assignPaths(Level level, Map<DevicePos, List<Node>> reachable,
                                       Map<DevicePos, Consumer> kinds,
                                    Map<Node, Node> parents,
                                       Map<String, List<DevicePos>> connectors,
                                       List<DevicePos> unnamed,
                                       Map<DevicePos, List<Node>> paths,
                                       List<BlockPos> displays, List<BlockPos> drives,
                                       List<BlockPos> racks, List<BlockPos> fabricators,
                                       List<BlockPos> masts, GatewayRegions regions) {
        for (Map.Entry<DevicePos, List<Node>> entry : reachable.entrySet()) {
            DevicePos device = entry.getKey();
            BlockPos pos = device.pos();
            Consumer kind = kinds.get(device);
            // Über Connectors und nicht über die BlockEntity: Ein Anschluss
            // sitzt entweder allein in einem Connectorblock oder an einer
            // Fläche eines Kabels. Welche Bauform, geht die Zuteilung nichts
            // an — die Fläche schon.
            dev.devpanda.factorynetwork.block.entity.ConnectorPart connector =
                    device.side() == null ? null
                            : dev.devpanda.factorynetwork.block.entity.Connectors
                                    .at(level, pos, device.side());
            if (kind == null || (kind == Consumer.CONNECTOR && connector == null)) {
                continue;
            }
            // <b>Der kürzeste Weg, ohne Kapazitätsfrage.</b> Bis zum
            // 29.08. stand hier eine Prüfung: Passt dieses Gerät noch auf
            // diesen Strang, oder muss es ausweichen? Sie ist mit den
            // Kanälen gefallen.
            //
            // Was bleibt, ist die Wegewahl selbst — ohne Weg gibt es kein
            // "hängt am Netz". Der erste gefundene ist der kürzeste: Die
            // Suche läuft in die Breite.
            List<Node> chosen = entry.getValue().isEmpty() ? null
                    : pathOf(level, entry.getValue().get(0), parents);
            if (chosen == null) {
                continue;
            }
            paths.put(device, chosen);

            switch (kind) {
                case DRIVE -> drives.add(pos);
                case RACK -> racks.add(pos);
                case FABRICATOR -> fabricators.add(pos);
                case DISPLAY -> displays.add(pos);
                case MAST -> masts.add(pos);
                case CONNECTOR -> {
                    // Die Anlage steht am Ort und nicht an der Fläche: Ein
                    // Gateway benennt, was um es herum hängt, und ein
                    // Kabelblock hängt mit allen seinen Anschlüssen dort.
                    String label = effectiveLabel(connector.label(), regions.instanceAt(pos));
                    if (label == null || label.isBlank()) {
                        unnamed.add(device);
                    } else {
                        // Nicht überschreiben: Zwei Connectoren mit demselben
                        // Namen sind ein Fehler, kein Vorrang.
                        connectors.computeIfAbsent(label, key -> new ArrayList<>()).add(device);
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Der Name, unter dem ein Gerät im Netz steht.
     *
     * <p><b>Die Beschriftung gewinnt.</b> Steht ein Schrägstrich darin, ist
     * die Anlage schon gesagt, und ein Gateway ändert daran nichts — sonst
     * hätte ein hingestellter Block still verschoben, was ein Programm über
     * ein Gerät sagt.
     *
     * <p>Sonst setzt das Gateway seinen Namen davor. Für alles dahinter ist
     * das Gerät damit {@code werk_1/eingang}, ohne dass es an einem einzigen
     * Connector dasteht: Der Interpreter, die Anlagenerkennung und beide
     * Editoren sehen denselben Namen wie vorher.
     */
    static String effectiveLabel(String label, String instance) {
        if (label == null || label.isBlank() || instance == null || instance.isBlank()) {
            return label;
        }
        return label.indexOf(dev.devpanda.factorynetwork.runtime
                .MultiblockInstances.SEPARATOR) >= 0
                ? label
                : instance + dev.devpanda.factorynetwork.runtime
                        .MultiblockInstances.SEPARATOR + label;
    }

    /** Der Weg eines Geräts zum Controller, als Liste seiner Kabelstränge. */
    private static List<Node> pathOf(Level level, Node from, Map<Node, Node> parents) {
        List<Node> path = new ArrayList<>();
        for (Node node = from; node != null; node = parents.get(node)) {
            // Ein Router trägt Kanäle wie ein Kabel — je Bahn eigene. Ließe
            // man ihn hier aus, wäre eine Kreuzung unbegrenzt, und ein Netz
            // hinter einem Router hätte plötzlich keine Grenze mehr.
            var state = level.getBlockState(node.pos());
            var block = state.getBlock();
            if ((block instanceof CableBlock
                    && dev.devpanda.factorynetwork.block.CableBlock.carries(state))
                    || block instanceof RouterBlock
                    || block instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
                path.add(node);
            }
        }
        return path;
    }

    /**
     * Geräte, die von zwei Gateways beansprucht werden.
     *
     * <p>Sie gehören zu keiner Anlage. Der Reiter <i>Netz</i> nennt sie,
     * statt still eines der beiden zu wählen.
     */
    public Set<BlockPos> contested() {
        return contested;
    }

    /** Die Router am Netz. */
    public Set<BlockPos> routers() {
        return routers;
    }


    /**
     * Wie viel an dieser Stelle noch frei ist.
     *
     * <p>Braucht die Welt, weil die Kapazität am Kabel steht. Ohne sie bleibt
     * nur die Annahme, es sei ein gewöhnliches — was bei einem dichten um das
     * Vierfache danebenläge.
     */
    public int throughputAt(Level level, BlockPos pos) {
        return Bandwidth.at(level, pos);
    }

    /**
     * Über welche Kabel dieses Gerät arbeitet.
     *
     * <p>Der Weg zum Controller, Stelle für Stelle. Daran hängt der
     * Durchsatz: Was ein Worker bewegt, belegt jedes Stück des Weges.
     */
    public List<Node> pathTo(DevicePos device) {
        return paths.getOrDefault(device, List.of());
    }

    /**
     * Der schwächste Punkt auf dem Weg dieses Geräts, je Tick.
     *
     * <p>Ein Weg ist so gut wie sein engstes Stück: Ein dichtes Kabel hinter
     * einem gewöhnlichen bringt nichts.
     */
    public int throughputTo(Level level, DevicePos device) {
        int least = Bandwidth.UNLIMITED;
        for (Node node : pathTo(device)) {
            least = Math.min(least, Bandwidth.at(level, node.pos()));
        }
        return least;
    }

    /**
     * Die Position eines Connectors — leer, wenn es ihn nicht gibt <b>oder</b>
     * wenn der Name doppelt vergeben ist. Ein mehrdeutiger Name darf nicht
     * stillschweigend auf einen der beiden zeigen.
     */
    public Optional<DevicePos> connector(String name) {
        List<DevicePos> found = connectorsByName.get(name);
        return found != null && found.size() == 1
                ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** Ist dieser Name mehr als einmal vergeben? */
    public boolean isAmbiguous(String name) {
        List<DevicePos> found = connectorsByName.get(name);
        return found != null && found.size() > 1;
    }

    /** Alle Namen, die mehr als einmal vergeben sind. */
    public List<String> ambiguousNames() {
        return connectorsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Alle Stellen zu einem Namen — bei einem Konflikt mehr als eine. */
    public List<DevicePos> positionsOf(String name) {
        return connectorsByName.getOrDefault(name, List.of());
    }

    /** Ist dieser Name im Netz schon vergeben? */
    public boolean isTaken(String name) {
        return connectorsByName.containsKey(name);
    }

    public Set<String> connectorNames() {
        return connectorsByName.keySet();
    }

    /** Nur die eindeutigen Connectoren — die, mit denen sich arbeiten lässt. */
    public Map<String, DevicePos> connectors() {
        Map<String, DevicePos> unique = new LinkedHashMap<>();
        connectorsByName.forEach((name, found) -> {
            if (found.size() == 1) {
                unique.put(name, found.get(0));
            }
        });
        return unique;
    }

    /** Connectoren ohne Namen — im Netz, aber nicht ansprechbar. */
    public List<DevicePos> unnamedConnectors() {
        return unnamed;
    }

    /** Alle Connectoren im Netz, benannt oder nicht. */
    public int connectorCount() {
        return connectorsByName.values().stream().mapToInt(List::size).sum() + unnamed.size();
    }

    /**
     * Alles im Netz, das kein Kabel ist.
     *
     * <p><b>Ein Gerät ist mehr als ein Connector.</b> Der Tooltip am
     * Controller schrieb „Geräte" und zählte {@link #connectorCount()} — ein
     * Netz aus Serverschrank, Laufwerk, Router und Anzeigewand meldete
     * „0 Geräte". Das stimmte für den Code, der nur Connectoren ansprechen
     * kann, aber nicht für die Frage, die der Tooltip beantwortet: wie groß
     * ist dieses Netz.
     *
     * <p>Die ohne freien Kanal zählen mit. Sie stehen im Netz, sie ziehen
     * Strom, sie sind nur nicht erreichbar — und dass eines fehlt, sagt die
     * eigene Zeile darunter.
     */
    public int deviceCount() {
        return connectorCount() + drives.size() + racks.size() + displays.size()
                + routers.size();
    }

    /** Displays am Netz. */
    public List<BlockPos> displays() {
        return displays;
    }

    /** Gehört diese Stelle zu diesem Netz — als Kabel, Gerät oder Display? */
    public boolean contains(BlockPos pos) {
        return cables.contains(pos)
                || racks.contains(pos)
                // Der Schrank steht nur mit seiner unteren Hälfte in der
                // Liste; die obere gehört genauso dazu.
                || racks.contains(pos.below())
                || drives.contains(pos)
                || routers.contains(pos)
                || displays.contains(pos)
                // Was der Graph als Verbraucher einsammelt, gehört auch dazu.
                // Fehlte hier eines, wäre es zwar im Netz, aber niemand fände
                // es: ControllerRegistry.owning geht über diese Frage, und
                // daran hängen das Anmelden eines Ferngeräts, das Öffnen des
                // Fensters und jede Anzeige. Im Spiel sah der Mast deshalb
                // aus, als hinge er an gar nichts.
                || masts.contains(pos)
                || fabricators.contains(pos)
                || bridges.contains(pos)
                // Bei den Anschlüssen zählt allein der Ort: Gefragt wird, ob
                // dieser Block zum Netz gehört, nicht welche seiner Flächen.
                || atPos(unnamed, pos)
                || connectorsByName.values().stream()
                        .anyMatch(found -> atPos(found, pos));
    }

    private static boolean atPos(List<DevicePos> devices, BlockPos pos) {
        return devices.stream().anyMatch(device -> device.pos().equals(pos));
    }

    public int cableCount() {
        return cables.size();
    }

    /** Wurde der Aufbau abgebrochen, weil das Netz zu groß ist? */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Findet Namen, die einem gesuchten ähneln — für die Meldung
     * „Unbekannter Connector `cruhser_1` — meintest du `crusher_1`?".
     */
    public Optional<String> closestName(String wanted) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : connectorsByName.keySet()) {
            int distance = NameDistance.between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // Bei zu großem Abstand ist ein Vorschlag eher verwirrend als hilfreich.
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }

    @Override
    public String toString() {
        return "FactoryGraph[" + connectorsByName.size() + " Connectoren, "
                + cables.size() + " Kabel]";
    }
}
