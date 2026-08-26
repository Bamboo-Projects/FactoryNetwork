package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wertet aus, was ein Display zeigt.
 *
 * <p><b>Ein Display liest ab und rechnet damit — mehr nicht.</b> Ablesbar
 * sind ein Bestand, ein Redstonesignal, ein Workerzustand, ein globaler Wert.
 * Damit darf gerechnet und verglichen werden. Was hier nicht steht, meldet
 * das Display, statt es zu raten: keine Aufrufe, keine Schleifen, keine
 * Zuweisungen. Ein Interpreter ist das nicht.
 *
 * <p>Die Rechnung ist nötig, nicht bequem. Ein Fortschrittsbalken ohne
 * genannte Obergrenze ist geraten — {@code storage.count(item:coal) / 640.0}
 * sagt, worauf sich der Balken bezieht, und nur der Nutzer weiß das. Ohne
 * die Division blieb der Balken leer, während die Doku erklärte, warum der
 * Punkt in {@code 640.0} so wichtig sei.
 *
 * <p>Die Grenze bleibt dieselbe wie bei {@code when}: Nur was das System
 * beobachten kann, kommt hier vor. An einer Wand hängen schnell dreißig
 * Displays, und keines davon darf ein Programm starten.
 */
public final class DisplayValues {

    /** Ein fertig ausgewerteter Eintrag, so wie er gezeigt wird. */
    public record Line(Decl.Display.Entry.Kind kind, String label, String value, double fraction,
                       boolean flag) {

        public static Line text(Decl.Display.Entry.Kind kind, String label, String value) {
            return new Line(kind, label, value, 0, false);
        }
    }

    private final FactoryGraph graph;
    private final NetworkStorage storage;
    private final WorkerRuntime runtime;

    /**
     * Die globalen Werte des Netzes, oder leer.
     *
     * <p><b>Ein globaler Wert ist genau das, was oben steht: ein Wert, den
     * man nennt.</b> Er kostet keine Rechnung, nur einen Blick in eine Karte
     * — deshalb passt er hierher, während {@code storage.count(…) / 640.0}
     * es weiterhin nicht tut.
     */
    private final java.util.Map<String, Value> globals;

    /**
     * Die Welt, um Redstone abzulesen — oder {@code null}.
     *
     * <p>Ein Signal zu lesen ist ein Blick auf die Nachbarblöcke, sonst
     * nichts. Ohne Welt bleibt {@code redstone()} unbeantwortet, statt zu
     * raten: Ein Display, das „0" behauptet, wo es nicht nachsehen konnte,
     * schickt den Spieler zur falschen Maschine.
     */
    private final net.minecraft.world.level.Level level;

    public DisplayValues(FactoryGraph graph, NetworkStorage storage, WorkerRuntime runtime) {
        this(graph, storage, runtime, java.util.Map.of(), null);
    }

    public DisplayValues(FactoryGraph graph, NetworkStorage storage, WorkerRuntime runtime,
                         java.util.Map<String, Value> globals) {
        this(graph, storage, runtime, globals, null);
    }

    public DisplayValues(FactoryGraph graph, NetworkStorage storage, WorkerRuntime runtime,
                         java.util.Map<String, Value> globals,
                         net.minecraft.world.level.Level level) {
        this.graph = graph;
        this.storage = storage;
        this.runtime = runtime;
        this.globals = globals == null ? java.util.Map.of() : globals;
        this.level = level;
    }

    /**
     * Wie viele Posten eine Aufzählung höchstens zeigt.
     *
     * <p>Eine Tafel trägt sechs Zeilen, eine Wand mehr — wie viele, weiß erst
     * der Client, der sie zeichnet. Acht ist die Zahl, die auf einer
     * zweireihigen Wand aufgeht und auf einer einzelnen Tafel ehrlich
     * abgeschnitten wird: Die letzte Zeile sagt dann, wie viele fehlen.
     */
    private static final int LIST_LIMIT = 8;

    public List<Line> evaluate(Decl.Display display) {
        List<Line> lines = new ArrayList<>();
        for (Decl.Display.Entry entry : display.entries()) {
            // scale ist keine Zeile, sondern eine Angabe über die Tafel.
            // Sie steht im Block, weil sie zu dieser Anzeige gehört — aber
            // sie schreibt nichts hin.
            if (entry.kind() == Decl.Display.Entry.Kind.SCALE) {
                continue;
            }
            // Eine Aufzählung ist mehr als eine Zeile — das ist ihr ganzer
            // Sinn und der Unterschied zu row.
            if (entry.kind() == Decl.Display.Entry.Kind.LIST) {
                lines.addAll(listing(entry));
            } else {
                lines.add(evaluate(entry));
            }
        }
        return lines;
    }

    /**
     * Eine Aufzählung: Überschrift und je Posten eine Zeile.
     *
     * <p>Absteigend nach Menge, denn wer auf eine Wand sieht, sucht meist,
     * wovon zu viel oder zu wenig da ist. Was über {@link #LIST_LIMIT} hinaus
     * geht, wird gezählt statt weggelassen — eine Liste, die still endet,
     * liest sich wie ein vollständiger Bestand.
     */
    private List<Line> listing(Decl.Display.Entry entry) {
        List<Line> lines = new ArrayList<>();
        List<Map.Entry<String, Long>> posten = entries(entry.value());
        if (posten == null) {
            // Kein Bestand, sondern etwas anderes — dann wie bisher eine
            // Zeile, samt der Meldung, die describe() dafür kennt.
            lines.add(Line.text(entry.kind(), entry.label(), describe(entry.value())));
            return lines;
        }
        posten.sort(java.util.Comparator.comparingLong(
                (Map.Entry<String, Long> item) -> item.getValue()).reversed());
        lines.add(Line.text(entry.kind(), entry.label(),
                posten.isEmpty() ? "leer" : posten.size() + " Arten"));
        for (int i = 0; i < Math.min(posten.size(), LIST_LIMIT); i++) {
            lines.add(Line.text(Decl.Display.Entry.Kind.ROW,
                    posten.get(i).getKey(), shorten(posten.get(i).getValue())));
        }
        if (posten.size() > LIST_LIMIT) {
            lines.add(Line.text(Decl.Display.Entry.Kind.TEXT, null,
                    "… und " + (posten.size() - LIST_LIMIT) + " weitere"));
        }
        return lines;
    }

    /**
     * Die Posten hinter {@code storage.items()} oder {@code brecher.items()},
     * oder {@code null}, wenn dort etwas anderes steht.
     *
     * <p>Nur diese beiden. Ein Display rechnet nicht — es liest ab, und die
     * Grenze ist dieselbe wie überall sonst in dieser Klasse.
     */
    private List<Map.Entry<String, Long>> entries(Expr expr) {
        if (!(expr instanceof Expr.Call call)
                || !(call.callee() instanceof Expr.Member member)
                || !"items".equals(member.name())
                || !call.arguments().isEmpty()) {
            return null;
        }
        if (member.target() instanceof Expr.Builtin builtin
                && builtin.kind() == Expr.Builtin.Kind.STORAGE) {
            return named(storage.contents(), item -> item.getDescription().getString());
        }
        if (!(member.target() instanceof Expr.Name device) || level == null) {
            return null;
        }
        var connector = connectorNamed(device.value());
        if (connector == null) {
            return null;
        }
        var amounts = dev.devpanda.factorynetwork.block.entity.DeviceAmounts.of(connector);
        List<Map.Entry<String, Long>> found =
                named(amounts.items(), item -> item.getDescription().getString());
        // Eine Maschine kann beides führen. Getrennt zu zählen und zusammen
        // zu zeigen ist genau das, was man vor ihr wissen will.
        found.addAll(named(amounts.fluids(), fluid -> fluid.getFluidType()
                .getDescription().getString()));
        return found;
    }

    private static <T> List<Map.Entry<String, Long>> named(
            Map<T, Long> contents, java.util.function.Function<T, String> naming) {
        List<Map.Entry<String, Long>> found = new ArrayList<>(contents.size());
        contents.forEach((key, amount) -> {
            if (amount > 0) {
                found.add(Map.entry(naming.apply(key), amount));
            }
        });
        return found;
    }

    private Line evaluate(Decl.Display.Entry entry) {
        return switch (entry.kind()) {
            case TITLE -> Line.text(entry.kind(), entry.label(), "");
            case TEXT -> Line.text(entry.kind(), null, describe(entry.value()));
            case ROW -> Line.text(entry.kind(), entry.label(), describe(entry.value()));
            case PROGRESS -> new Line(entry.kind(), entry.label(),
                    progressText(entry.value()), fraction(entry.value()), false);
            case INDICATOR -> new Line(entry.kind(), entry.label(), "",
                    0, truth(entry.value()));
            case LIST -> Line.text(entry.kind(), entry.label(), describe(entry.value()));
            case BUTTON -> Line.text(entry.kind(), entry.label(), "");
            // Kommt hier nie an: evaluate(Decl.Display) überspringt es. Der
            // Fall steht trotzdem da, damit der Übersetzer beim nächsten
            // neuen Baustein wieder meckert.
            case SCALE -> Line.text(entry.kind(), null, "");
        };
    }

    /**
     * Was ein Ausdruck anzeigt.
     *
     * <p>Bewusst wenige Fälle. Was hier nicht steht, erscheint als Hinweis
     * auf dem Display selbst — eine leere Zeile wäre schlimmer, weil man dann
     * den Fehler beim Netz sucht statt beim Programm.
     */
    private String describe(Expr expr) {
        if (expr == null) {
            return "";
        }
        if (expr instanceof Expr.StringLit text) {
            return text.value();
        }
        if (expr instanceof Expr.IntLit number) {
            return String.valueOf(number.value());
        }
        if (expr instanceof Expr.FloatLit number) {
            return String.format(Locale.GERMAN, "%.1f", number.value());
        }
        Long count = storageCount(expr);
        if (count == null) {
            count = deviceCount(expr);
        }
        if (count != null) {
            return shorten(count);
        }
        String worker = workerState(expr);
        if (worker != null) {
            return worker;
        }
        if (expr instanceof Expr.Member member && member.target() instanceof Expr.Name device) {
            return switch (member.name()) {
                case "online" -> graph.connector(device.value()).isPresent() ? "an" : "aus";
                case "name" -> device.value();
                default -> "?";
            };
        }
        // Ein globaler Wert steht für sich — kein Aufruf, keine Rechnung.
        if (expr instanceof Expr.Name name) {
            Value value = globals.get(name.value());
            if (value != null) {
                return value.describe();
            }
        }
        Double number = number(expr);
        if (number != null) {
            return round(number);
        }
        return "?";
    }

    /**
     * Was neben dem Balken steht.
     *
     * <p>Bei einem Bestand die Menge, bei einem Anteil der Prozentwert.
     * „0,5" neben einem halb gefüllten Balken sagt dasselbe zweimal und
     * beantwortet die eine Frage nicht, die man an einen Balken hat.
     */
    private String progressText(Expr expr) {
        if (expr == null) {
            return "";
        }
        if (storageCount(expr) != null) {
            return describe(expr);
        }
        Double number = number(expr);
        if (number == null) {
            return describe(expr);
        }
        return Math.round(Math.max(0, Math.min(1, number)) * 100) + " %";
    }

    /**
     * Was hinter einem Ausdruck für eine Zahl steckt, oder {@code null}.
     *
     * <p>Hier steht die ganze Rechenkunst eines Displays: die vier
     * Grundrechenarten über Werten, die man ablesen kann. Die Rekursion läuft
     * über den geparsten Ausdruck und kann deshalb nicht tiefer werden, als
     * der Spieler geschrieben hat.
     *
     * <p><b>{@code null} heißt „weiß ich nicht", nicht „null".</b> Der
     * Unterschied trägt die Anzeige: Ein unbekannter Ausdruck wird zu „?",
     * ein leerer Speicher zu „0".
     */
    private Double number(Expr expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof Expr.IntLit number) {
            return (double) number.value();
        }
        if (expr instanceof Expr.FloatLit number) {
            return number.value();
        }
        Long count = storageCount(expr);
        if (count == null) {
            count = deviceCount(expr);
        }
        if (count != null) {
            return (double) count;
        }
        Integer signal = redstone(expr);
        if (signal != null) {
            return (double) signal;
        }
        if (expr instanceof Expr.Name name
                && globals.get(name.value()) instanceof Value.Int number) {
            return (double) number.value();
        }
        if (expr instanceof Expr.Unary unary && unary.op() == Expr.Unary.Op.NEGATE) {
            Double operand = number(unary.operand());
            return operand == null ? null : -operand;
        }
        if (expr instanceof Expr.Binary binary) {
            Double left = number(binary.left());
            Double right = number(binary.right());
            if (left == null || right == null) {
                return null;
            }
            return switch (binary.op()) {
                case ADD -> left + right;
                case SUB -> left - right;
                case MUL -> left * right;
                // Durch null zu teilen ist keine Zahl, und „Unendlich" auf
                // einem Balken ist schlimmer als ein Fragezeichen.
                case DIV -> right == 0 ? null : left / right;
                case MOD -> right == 0 ? null : left % right;
                default -> null;
            };
        }
        return null;
    }

    /** {@code depot.redstone()} — die Stärke am Connector, oder {@code null}. */
    private Integer redstone(Expr expr) {
        if (level == null
                || !(expr instanceof Expr.Call call)
                || !(call.callee() instanceof Expr.Member member)
                || !"redstone".equals(member.name())
                || !(member.target() instanceof Expr.Name device)
                || !call.arguments().isEmpty()) {
            return null;
        }
        // Ohne Connector gibt es keine Antwort — dasselbe wie bei online.
        return graph.connector(device.value())
                .map(where -> level.getBestNeighborSignal(where.pos()))
                .orElse(null);
    }

    /** Ganze Zahlen ohne Komma, alles andere mit einer Stelle. */
    private static String round(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.GERMAN, "%.1f", value);
    }

    /** {@code storage.count(item:…)} — der häufigste Fall auf einem Display. */
    /**
     * {@code brecher.count(item:iron_ore)} — was in der Maschine liegt.
     *
     * <p>Ein Blick in eine BlockEntity je Tafel und Sekunde. Den Netzbestand
     * liest die Anzeige ohnehin in diesem Takt; ein {@code ?} auf der Tafel,
     * das niemand erklären kann, wäre der schlechtere Tausch.
     *
     * <p>Ohne Welt — in den Prüfungen — bleibt es beim {@code null}: Eine
     * erfundene Null schickte den Spieler zur falschen Maschine.
     */
    private Long deviceCount(Expr expr) {
        if (!(expr instanceof Expr.Call call)
                || !(call.callee() instanceof Expr.Member member)
                || !"count".equals(member.name())
                || !(member.target() instanceof Expr.Name device)
                || level == null) {
            return null;
        }
        var connector = connectorNamed(device.value());
        if (connector == null) {
            return null;
        }
        var amounts = dev.devpanda.factorynetwork.block.entity.DeviceAmounts.of(connector);
        if (call.arguments().isEmpty()) {
            return amounts.items().values().stream().mapToLong(Long::longValue).sum();
        }
        Expr wanted = call.arguments().get(0).value();
        List<Item> items = ItemSelection.resolve(wanted);
        if (!items.isEmpty()) {
            return items.stream()
                    .mapToLong(item -> amounts.items().getOrDefault(item, 0L))
                    .sum();
        }
        return FluidSelection.resolve(wanted).stream()
                .mapToLong(fluid -> amounts.fluids().getOrDefault(fluid, 0L))
                .sum();
    }

    private Long storageCount(Expr expr) {
        if (!(expr instanceof Expr.Call call)
                || !(call.callee() instanceof Expr.Member member)
                || !"count".equals(member.name())
                || !(member.target() instanceof Expr.Builtin builtin)
                || builtin.kind() != Expr.Builtin.Kind.STORAGE
                || call.arguments().size() != 1) {
            return null;
        }
        List<Item> items = ItemSelection.resolve(call.arguments().get(0).value());
        return items.stream().mapToLong(storage::count).sum();
    }

    /** {@code ore_import.status} oder ein Workername für sich. */
    private String workerState(Expr expr) {
        String name = null;
        if (expr instanceof Expr.Name plain) {
            name = plain.value();
        } else if (expr instanceof Expr.Member member
                && member.target() instanceof Expr.Name owner
                && ("status".equals(member.name()) || "progress".equals(member.name()))) {
            name = owner.value();
        }
        if (name == null) {
            return null;
        }
        WorkerRuntime.WorkerState state = runtime.states().get(name);
        if (state == null) {
            return null;
        }
        return switch (state.status) {
            case RUNNING -> "läuft";
            case IDLE -> "wartet";
            case WAITING_TARGET -> "Ziel voll";
            case WAITING_CONDITION -> "schläft";
            case HALTED -> "steht";
        };
    }

    /** Für Fortschrittsbalken: ein Wert zwischen null und eins. */
    private double fraction(Expr expr) {
        Long count = storageCount(expr);
        if (count != null) {
            // Ein nackter Bestand nennt seine Obergrenze nicht. Ein voller
            // Stapel gilt als voll — wer es genauer braucht, teilt selbst,
            // und genau dafür ist die Rechnung da.
            return Math.min(1.0, count / 64.0);
        }
        Double value = number(expr);
        return value == null ? 0 : Math.max(0, Math.min(1, value));
    }

    private boolean truth(Expr expr) {
        if (expr instanceof Expr.BoolLit bool) {
            return bool.value();
        }
        if (expr instanceof Expr.Member member && "online".equals(member.name())
                && member.target() instanceof Expr.Name device) {
            return graph.connector(device.value()).isPresent();
        }
        if (expr instanceof Expr.Unary unary && unary.op() == Expr.Unary.Op.NOT) {
            return !truth(unary.operand());
        }
        if (expr instanceof Expr.Binary binary) {
            Boolean compared = compare(binary);
            if (compared != null) {
                return compared;
            }
        }
        if (expr instanceof Expr.Name name
                && globals.get(name.value()) instanceof Value.Bool flag) {
            return flag.value();
        }
        Long count = storageCount(expr);
        if (count != null) {
            return count > 0;
        }
        Double value = number(expr);
        return value != null && value != 0;
    }

    /**
     * Ein Vergleich für ein Lämpchen, oder {@code null}.
     *
     * <p>Zahlen wie {@code depot.redstone() > 0}, Text wie
     * {@code modus == "tag"}. Der Textvergleich ist kein Luxus: Ein globaler
     * Wert hält meistens ein Wort, und ein Lämpchen, das dieses Wort nicht
     * prüfen kann, bleibt für immer dunkel.
     */
    private Boolean compare(Expr.Binary binary) {
        switch (binary.op()) {
            case AND -> {
                return truth(binary.left()) && truth(binary.right());
            }
            case OR -> {
                return truth(binary.left()) || truth(binary.right());
            }
            case EQ, NEQ, LT, LTE, GT, GTE -> { }
            default -> {
                return null;
            }
        }
        Double left = number(binary.left());
        Double right = number(binary.right());
        if (left != null && right != null) {
            return switch (binary.op()) {
                case EQ -> left.doubleValue() == right.doubleValue();
                case NEQ -> left.doubleValue() != right.doubleValue();
                case LT -> left < right;
                case LTE -> left <= right;
                case GT -> left > right;
                case GTE -> left >= right;
                default -> null;
            };
        }
        if (binary.op() != Expr.Binary.Op.EQ && binary.op() != Expr.Binary.Op.NEQ) {
            return null;
        }
        String leftText = comparableText(binary.left());
        String rightText = comparableText(binary.right());
        if (leftText == null || rightText == null) {
            return null;
        }
        return binary.op() == Expr.Binary.Op.EQ
                ? leftText.equals(rightText)
                : !leftText.equals(rightText);
    }

    /** Text, mit dem sich vergleichen lässt: ein Literal oder ein globaler Wert. */
    private String comparableText(Expr expr) {
        if (expr instanceof Expr.StringLit text) {
            return text.value();
        }
        if (expr instanceof Expr.Name name
                && globals.get(name.value()) instanceof Value.Text text) {
            return text.value();
        }
        return null;
    }

    private static String shorten(long amount) {
        if (amount < 1000) {
            return String.valueOf(amount);
        }
        if (amount < 1_000_000) {
            return String.format(Locale.GERMAN, "%.1fk", amount / 1000.0);
        }
        return String.format(Locale.GERMAN, "%.1fM", amount / 1_000_000.0);
    }

    /** Der Anschluss mit diesem Namen, oder {@code null}. */
    private dev.devpanda.factorynetwork.block.entity.ConnectorPart connectorNamed(
            String device) {
        var where = graph.connector(device).orElse(null);
        if (where == null || where.side() == null || level == null
                || !level.isLoaded(where.pos())) {
            return null;
        }
        return dev.devpanda.factorynetwork.block.entity.Connectors
                .at(level, where.pos(), where.side());
    }
}
