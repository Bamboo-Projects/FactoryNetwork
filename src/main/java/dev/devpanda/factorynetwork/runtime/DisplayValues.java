package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public List<Line> evaluate(Decl.Display display) {
        List<Line> lines = new ArrayList<>();
        for (Decl.Display.Entry entry : display.entries()) {
            lines.add(evaluate(entry));
        }
        return lines;
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
                .map(level::getBestNeighborSignal)
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
}
