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
 * <p><b>Ein Display rechnet nicht, es nennt Werte.</b> Deshalb steht hier
 * kein Interpreter, sondern eine kurze Liste dessen, was ablesbar ist: ein
 * Bestand, ein Workerzustand, eine Zahl. Alles andere meldet das Display,
 * statt es zu raten.
 *
 * <p>Der Grund ist derselbe wie bei {@code when}: Nur was das System
 * beobachten kann, muss es nicht in jedem Tick neu ausrechnen. An einer Wand
 * hängen schnell dreißig Displays.
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

    public DisplayValues(FactoryGraph graph, NetworkStorage storage, WorkerRuntime runtime) {
        this.graph = graph;
        this.storage = storage;
        this.runtime = runtime;
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
                    describe(entry.value()), fraction(entry.value()), false);
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
        return "?";
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
            // Ohne bekannte Obergrenze ist ein Balken nur ein Gefühl: Ein
            // voller Stapel gilt als voll.
            return Math.min(1.0, count / 64.0);
        }
        if (expr instanceof Expr.FloatLit number) {
            return Math.max(0, Math.min(1, number.value()));
        }
        return 0;
    }

    private boolean truth(Expr expr) {
        if (expr instanceof Expr.BoolLit bool) {
            return bool.value();
        }
        if (expr instanceof Expr.Member member && "online".equals(member.name())
                && member.target() instanceof Expr.Name device) {
            return graph.connector(device.value()).isPresent();
        }
        Long count = storageCount(expr);
        return count != null && count > 0;
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
