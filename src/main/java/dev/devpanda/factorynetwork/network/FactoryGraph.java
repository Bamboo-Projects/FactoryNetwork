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
    private final List<DevicePos> starved;
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
    private final Map<Node, Integer> channelLoad;
    /**
     * Wer mit wem verbunden ist.
     *
     * <p>Die Suche kennt das ohnehin — sie merkt sich zu jedem Knoten, woher
     * sie kam. Aufgehoben wird es für den Netzanalysator, der das Netz als
     * Linien in die Welt zeichnet. Ohne die Kanten bliebe ihm nur eine Wolke
     * von Punkten.
     */
    private final List<Edge> edges;
    private final boolean truncated;

    /** Eine Verbindung zwischen zwei Knoten des Netzes. */
    public record Edge(Node from, Node to) {}

    private FactoryGraph(Map<String, List<DevicePos>> connectorsByName,
                         List<DevicePos> unnamed,
                         List<DevicePos> starved, List<BlockPos> displays, Set<BlockPos> cables,
                         Set<BlockPos> routers, Map<Node, Integer> channelLoad, List<Edge> edges,
                         List<BlockPos> drives, List<BlockPos> racks, List<BlockPos> extensions,
                         List<BlockPos> fabricators, Set<BlockPos> contested,
                         boolean truncated) {
        this.contested = contested;
        this.fabricators = fabricators;
        this.extensions = extensions;
        this.routers = routers;
        this.drives = drives;
        this.racks = racks;
        this.displays = displays;
        this.connectorsByName = connectorsByName;
        this.unnamed = unnamed;
        this.starved = starved;
        this.channelLoad = channelLoad;
        this.cables = cables;
        this.edges = edges;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), List.of(), List.of(), List.of(),
                Set.of(), Set.of(), Map.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Set.of(), false);
    }

    /** Die Fabricators am Netz. */
    public List<BlockPos> fabricators() {
        return fabricators;
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
     * Wie viele Kanäle ein Kabelstrang trägt.
     *
     * <p>Jedes Gerät zieht auf seinem ganzen Weg zum Controller einen Kanal
     * ab — weiter hinten fehlt er dann. Wie viele ein Kabel trägt, steht am
     * Kabel selbst: sechzehn beim gewöhnlichen, vierundsechzig beim dichten.
     * Das Vorbild ist Applied Energistics mit acht und zweiunddreißig; bei
     * uns ist es das Doppelte, weil das System ein moderneres sein soll.
     *
     * <p>Diese Zahl hier ist nur der Rückfall für Stellen, an denen kein
     * Kabel liegt — etwa der Controller selbst. Gerechnet wird stets mit
     * {@link #capacityAt}.
     */
    public static final int CHANNELS_PER_STRAND = CableBlock.CHANNELS_THIN;

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
        List<DevicePos> starved = new ArrayList<>();
        List<BlockPos> displays = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> routers = new HashSet<>();
        Set<BlockPos> gateways = new LinkedHashSet<>();
        List<BlockPos> drives = new ArrayList<>();
        List<BlockPos> racks = new ArrayList<>();
        List<BlockPos> fabricators = new ArrayList<>();
        Map<Node, Integer> load = new HashMap<>();
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
            boolean atRouter = current.lane() != RouterBlockEntity.OFF;
            for (Direction direction : Direction.values()) {
                if (atRouter && RouterBlockEntity.laneAt(level, current.pos(), direction)
                        != current.lane()) {
                    continue;
                }
                BlockPos next = current.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    // Ein nicht geladener Chunk beendet den Zweig, ohne Fehler.
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CableBlock) {
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
        assignChannels(level, reachable, kinds, parents, load, connectors, unnamed,
                starved, displays, drives, racks, fabricators, regions);

        Map<String, List<DevicePos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        List<Edge> edges = new ArrayList<>();
        parents.forEach((node, parent) -> {
            if (parent != null) {
                edges.add(new Edge(parent, node));
            }
        });
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                List.copyOf(starved), List.copyOf(displays), Set.copyOf(cables),
                Set.copyOf(routers), Map.copyOf(load), List.copyOf(edges),
                List.copyOf(drives), List.copyOf(racks), List.copyOf(extensions),
                List.copyOf(fabricators), Set.copyOf(regions.contested()), truncated);
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
     * Was am Netz einen Kanal kostet.
     *
     * <p>Die Regel in einem Satz: Was etwas tut, kostet einen Kanal. Eine
     * Anzeige tut weniger — sie liest nur mit — und kostet keinen. Ein Router
     * ist Kabel und kein Gerät.
     */
    private enum Consumer {
        CONNECTOR(Channels.CONNECTOR),
        DRIVE(Channels.DRIVE),
        RACK(Channels.RACK),
        FABRICATOR(Channels.FABRICATOR),
        DISPLAY(Channels.DISPLAY);

        private final int cost;

        Consumer(int cost) {
            this.cost = cost;
        }
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
        int lane = RouterBlockEntity.laneAt(level, pos, direction.getOpposite());
        if (lane == RouterBlockEntity.OFF) {
            return;
        }
        // Farbneutral: Was auf einer Bahn zusammenkommt, ist verbunden, egal
        // in welcher Farbe es ankam. Wer das nicht will, legt zwei Bahnen.
        Node node = new Node(pos.immutable(), CableColour.NONE, lane);
        // Der Router selbst gehört zum Netz, auch wenn diese Bahn schon
        // besucht ist — sonst fehlte er im Analysator, sobald zwei Wege auf
        // dieselbe Bahn führen.
        routers.add(pos.immutable());
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
        }
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
    private static void assignChannels(Level level, Map<DevicePos, List<Node>> reachable,
                                       Map<DevicePos, Consumer> kinds,
                                       Map<Node, Node> parents, Map<Node, Integer> load,
                                       Map<String, List<DevicePos>> connectors,
                                       List<DevicePos> unnamed, List<DevicePos> starved,
                                       List<BlockPos> displays, List<BlockPos> drives,
                                       List<BlockPos> racks, List<BlockPos> fabricators,
                                       GatewayRegions regions) {
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
            // Der Connector nennt seinen Bedarf selbst — ein Gerät mit
            // höherem Bedarf soll später keine Wanderung durch diesen Code
            // nach sich ziehen.
            int cost = kind == Consumer.CONNECTOR
                    ? connector.channelCost() : kind.cost;

            List<Node> chosen = null;
            for (Node entryPoint : entry.getValue()) {
                List<Node> path = pathOf(level, entryPoint, parents);
                // Die Kapazität steht am Kabel: Ein dichtes trägt vierundsechzig,
                // ein gewöhnliches sechzehn. Auf einem gemischten Weg zählt
                // jede Stelle für sich — das schwächste Stück begrenzt.
                boolean room = path.stream().allMatch(node ->
                        load.getOrDefault(node, 0) + cost <= capacityAt(level, node.pos()));
                if (room) {
                    chosen = path;
                    break;
                }
            }
            if (chosen == null) {
                starved.add(device);
                continue;
            }
            for (Node node : chosen) {
                load.merge(node, cost, Integer::sum);
            }

            switch (kind) {
                case DRIVE -> drives.add(pos);
                case RACK -> racks.add(pos);
                case FABRICATOR -> fabricators.add(pos);
                case DISPLAY -> displays.add(pos);
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
            var block = level.getBlockState(node.pos()).getBlock();
            if (block instanceof CableBlock || block instanceof RouterBlock
                    || block instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
                path.add(node);
            }
        }
        return path;
    }

    /**
     * Wie viele Kanäle ein Strang an dieser Stelle trägt.
     *
     * <p>Die Zahlen gehören dem Graphen, nicht den Blöcken. Ein Kabel kann zu
     * zwei Netzen gehören — schriebe jeder Controller seine Zahlen in die
     * BlockEntity, überschriebe einer den anderen.
     */
    public int channelLoad(BlockPos pos, CableColour colour) {
        return channelLoad.getOrDefault(new Node(pos, colour), 0);
    }

    /** Wie viele Kanäle ein Knoten trägt — für Kabel wie für Router. */
    public int channelLoad(Node node) {
        return channelLoad.getOrDefault(node, 0);
    }

    /** Wie viele Kanäle eine Bahn eines Routers trägt. */
    public int laneLoad(BlockPos pos, int lane) {
        return channelLoad.getOrDefault(new Node(pos, CableColour.NONE, lane), 0);
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
     * Wie viele Kanäle das Kabel an dieser Stelle trägt.
     *
     * <p>Der Controller selbst und alles, was kein Kabel ist, gilt als
     * unbegrenzt: Dort wird nichts durchgeleitet, was zu begrenzen wäre.
     */
    public static int capacityAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RouterBlock) {
            // Der Router gehört zum dicken Kabel, also trägt jede seiner
            // Bahnen so viel wie ein dickes Kabel.
            return CableBlock.CHANNELS_DENSE;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
            // Er gehört zum dichten Kabel und trägt so viel wie eines. Ein
            // Kanalvermehrer zum Hinstellen machte die Kanalgrenze
            // bedeutungslos — dieselbe Regel wie beim Controller-Anbau.
            return CableBlock.CHANNELS_DENSE;
        }
        int channels = CableBlock.channelsAt(state);
        return channels > 0 ? channels : Integer.MAX_VALUE;
    }

    /**
     * Wie viel an dieser Stelle noch frei ist.
     *
     * <p>Braucht die Welt, weil die Kapazität am Kabel steht. Ohne sie bleibt
     * nur die Annahme, es sei ein gewöhnliches — was bei einem dichten um das
     * Vierfache danebenläge.
     */
    public int channelsFree(Level level, BlockPos pos, CableColour colour) {
        return capacityAt(level, pos) - channelLoad(pos, colour);
    }

    /** Geräte ohne freien Kanal — im Netz sichtbar, aber nicht ansprechbar. */
    public List<DevicePos> starvedConnectors() {
        return starved;
    }

    /**
     * Hungert an dieser Stelle ein Gerät?
     *
     * <p>Nach dem Ort gefragt und nicht nach der Fläche: Wer davorsteht,
     * sieht einen Block, und Jade schreibt an einen Block. Sitzen sechs
     * Anschlüsse daran und geht einer leer aus, ist der Block der richtige
     * Ort für den Hinweis.
     */
    public boolean isStarved(BlockPos pos) {
        return atPos(starved, pos);
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
                + routers.size() + starved.size();
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
                // Bei den Anschlüssen zählt allein der Ort: Gefragt wird, ob
                // dieser Block zum Netz gehört, nicht welche seiner Flächen.
                || atPos(unnamed, pos)
                || atPos(starved, pos)
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
