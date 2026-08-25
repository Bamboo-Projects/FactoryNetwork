package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.block.ControllerExtensionBlock;
import dev.devpanda.factorynetwork.block.DisplayBlock;
import dev.devpanda.factorynetwork.block.DriveBlock;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.RouterBlock;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
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
     * Name auf Positionen. Mehr als eine Position bedeutet: Der Name ist
     * doppelt vergeben und damit unbrauchbar — siehe {@link #isAmbiguous}.
     */
    private final Map<String, List<BlockPos>> connectorsByName;
    /**
     * Connectoren ohne Namen.
     *
     * <p>Sie lassen sich nicht ansprechen — dafür braucht es einen Namen —,
     * aber sie hängen im Netz, und der Spieler muss das sehen. Sie hier
     * wegzulassen hieß, dass der Controller „0 Connectoren" meldete, während
     * drei danebenhingen.
     */
    private final List<BlockPos> unnamed;
    /** Geräte, die im Netz hängen, aber keinen freien Kanal bekommen haben. */
    private final List<BlockPos> starved;
    /** Displays am Netz. Sie zeigen nur an und brauchen keinen Kanal. */
    private final List<BlockPos> displays;
    private final Set<BlockPos> cables;
    /** Router am Netz. Sie leiten weiter und kosten selbst keinen Kanal. */
    private final Set<BlockPos> routers;
    /** Laufwerke am Netz. Sie tragen den Speicher, den das Netz benutzt. */
    private final List<BlockPos> drives;
    /** Serverschränke am Netz. Sie tragen die Rechenleistung. */
    private final List<BlockPos> racks;
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

    private FactoryGraph(Map<String, List<BlockPos>> connectorsByName, List<BlockPos> unnamed,
                         List<BlockPos> starved, List<BlockPos> displays, Set<BlockPos> cables,
                         Set<BlockPos> routers, Map<Node, Integer> channelLoad, List<Edge> edges,
                         List<BlockPos> drives, List<BlockPos> racks, List<BlockPos> extensions,
                         boolean truncated) {
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
                List.of(), false);
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
        Map<String, List<BlockPos>> connectors = new LinkedHashMap<>();
        List<BlockPos> unnamed = new ArrayList<>();
        List<BlockPos> starved = new ArrayList<>();
        List<BlockPos> displays = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> routers = new HashSet<>();
        List<BlockPos> drives = new ArrayList<>();
        List<BlockPos> racks = new ArrayList<>();
        Map<Node, Integer> load = new HashMap<>();
        Map<Node, Node> parents = new HashMap<>();
        Set<BlockPos> visitedDevices = new HashSet<>();
        // Gerät auf die Kabelstränge, über die es erreichbar ist — in der
        // Reihenfolge, in der die Suche sie gefunden hat.
        Map<BlockPos, List<Node>> reachable = new LinkedHashMap<>();
        Map<BlockPos, Consumer> kinds = new LinkedHashMap<>();
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
                    visitCable(level, next, current, parents, queue, cables);
                } else if (state.getBlock() instanceof RouterBlock) {
                    visitRouter(level, next, direction, current, parents, queue, routers);
                } else {
                    // Alles, was einen Kanal kostet, geht denselben Weg —
                    // noch nicht zuteilen: Ein Gerät kann an mehreren
                    // Strängen hängen, und welcher es trägt, entscheidet
                    // sich erst, wenn alle Wege bekannt sind.
                    Consumer kind = consumerAt(state);
                    if (kind != null) {
                        BlockPos device = anchorOf(state, next);
                        reachable.computeIfAbsent(device, key -> new ArrayList<>())
                                .add(current);
                        kinds.put(device, kind);
                        visitedDevices.add(device);
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

        assignChannels(level, reachable, kinds, parents, load, connectors, unnamed,
                starved, displays, drives, racks);

        Map<String, List<BlockPos>> frozen = new LinkedHashMap<>();
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
                truncated);
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
     */
    private static BlockPos anchorOf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof RackBlock) {
            return RackBlock.baseOf(state, pos).immutable();
        }
        return pos.immutable();
    }

    private static Consumer consumerAt(BlockState state) {
        if (state.getBlock() instanceof ConnectorBlock) {
            return Consumer.CONNECTOR;
        }
        if (state.getBlock() instanceof DriveBlock) {
            return Consumer.DRIVE;
        }
        if (state.getBlock() instanceof RackBlock) {
            return Consumer.RACK;
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
    private static void visitCable(Level level, BlockPos pos, Node from, Map<Node, Node> parents,
                                   Deque<Node> queue, Set<BlockPos> cables) {
        boolean entered = false;
        CableColour colour = CableBlock.colourOf(level.getBlockState(pos));
        if (from.colour().connectsTo(colour)) {
            Node node = new Node(pos.immutable(), colour);
            if (!parents.containsKey(node)) {
                parents.put(node, from);
                queue.add(node);
                entered = true;
            }
        }
        if (entered) {
            cables.add(pos.immutable());
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
    private static void assignChannels(Level level, Map<BlockPos, List<Node>> reachable,
                                       Map<BlockPos, Consumer> kinds,
                                       Map<Node, Node> parents, Map<Node, Integer> load,
                                       Map<String, List<BlockPos>> connectors,
                                       List<BlockPos> unnamed, List<BlockPos> starved,
                                       List<BlockPos> displays, List<BlockPos> drives,
                                       List<BlockPos> racks) {
        for (Map.Entry<BlockPos, List<Node>> entry : reachable.entrySet()) {
            BlockPos pos = entry.getKey();
            Consumer kind = kinds.get(pos);
            ConnectorBlockEntity connector =
                    level.getBlockEntity(pos) instanceof ConnectorBlockEntity found
                            ? found : null;
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
                starved.add(pos);
                continue;
            }
            for (Node node : chosen) {
                load.merge(node, cost, Integer::sum);
            }

            switch (kind) {
                case DRIVE -> drives.add(pos);
                case RACK -> racks.add(pos);
                case DISPLAY -> displays.add(pos);
                case CONNECTOR -> {
                    String label = connector.label();
                    if (label == null || label.isBlank()) {
                        unnamed.add(pos);
                    } else {
                        // Nicht überschreiben: Zwei Connectoren mit demselben
                        // Namen sind ein Fehler, kein Vorrang.
                        connectors.computeIfAbsent(label, key -> new ArrayList<>()).add(pos);
                    }
                }
                default -> { }
            }
        }
    }

    /** Der Weg eines Geräts zum Controller, als Liste seiner Kabelstränge. */
    private static List<Node> pathOf(Level level, Node from, Map<Node, Node> parents) {
        List<Node> path = new ArrayList<>();
        for (Node node = from; node != null; node = parents.get(node)) {
            // Ein Router trägt Kanäle wie ein Kabel — je Bahn eigene. Ließe
            // man ihn hier aus, wäre eine Kreuzung unbegrenzt, und ein Netz
            // hinter einem Router hätte plötzlich keine Grenze mehr.
            var block = level.getBlockState(node.pos()).getBlock();
            if (block instanceof CableBlock || block instanceof RouterBlock) {
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
    public List<BlockPos> starvedConnectors() {
        return starved;
    }

    public boolean isStarved(BlockPos pos) {
        return starved.contains(pos);
    }

    /**
     * Die Position eines Connectors — leer, wenn es ihn nicht gibt <b>oder</b>
     * wenn der Name doppelt vergeben ist. Ein mehrdeutiger Name darf nicht
     * stillschweigend auf einen der beiden zeigen.
     */
    public Optional<BlockPos> connector(String name) {
        List<BlockPos> positions = connectorsByName.get(name);
        return positions != null && positions.size() == 1
                ? Optional.of(positions.get(0)) : Optional.empty();
    }

    /** Ist dieser Name mehr als einmal vergeben? */
    public boolean isAmbiguous(String name) {
        List<BlockPos> positions = connectorsByName.get(name);
        return positions != null && positions.size() > 1;
    }

    /** Alle Namen, die mehr als einmal vergeben sind. */
    public List<String> ambiguousNames() {
        return connectorsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Alle Positionen zu einem Namen — bei einem Konflikt mehr als eine. */
    public List<BlockPos> positionsOf(String name) {
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
    public Map<String, BlockPos> connectors() {
        Map<String, BlockPos> unique = new LinkedHashMap<>();
        connectorsByName.forEach((name, positions) -> {
            if (positions.size() == 1) {
                unique.put(name, positions.get(0));
            }
        });
        return unique;
    }

    /** Connectoren ohne Namen — im Netz, aber nicht ansprechbar. */
    public List<BlockPos> unnamedConnectors() {
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
                || unnamed.contains(pos)
                || starved.contains(pos)
                || displays.contains(pos)
                || connectorsByName.values().stream().anyMatch(list -> list.contains(pos));
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
