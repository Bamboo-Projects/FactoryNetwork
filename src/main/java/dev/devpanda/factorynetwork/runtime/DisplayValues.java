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
 * Evaluates what a display shows.
 *
 * <p><b>A display reads values and does arithmetic on them — nothing more.</b>
 * Readable are a stock level, a redstone signal, a worker state, a global
 * value. Those may be computed with and compared. Anything not listed here
 * the display reports instead of guessing: no calls, no loops, no
 * assignments. This is not an interpreter.
 *
 * <p>The arithmetic is necessary, not a convenience. A progress bar without a
 * stated upper bound is a guess — {@code storage.count(item:coal) / 640.0}
 * says what the bar refers to, and only the user knows that. Without the
 * division the bar stayed empty while the docs explained why the dot in
 * {@code 640.0} was so important.
 *
 * <p>The boundary stays the same as for {@code when}: only what the system
 * can observe appears here. A wall quickly holds thirty displays, and none
 * of them may start a program.
 */
public final class DisplayValues {

    /** A fully evaluated entry, as it is shown. */
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
     * The network's global values, or empty.
     *
     * <p><b>A global value is exactly what is stated above: a value one
     * names.</b> It costs no computation, only a lookup in a map — which is
     * why it belongs here, while {@code storage.count(…) / 640.0} still does
     * not.
     */
    private final java.util.Map<String, Value> globals;

    /**
     * The world, for reading redstone — or {@code null}.
     *
     * <p>Reading a signal is a look at the neighbouring blocks, nothing more.
     * Without a world, {@code redstone()} stays unanswered instead of
     * guessing: a display that claims "0" where it could not look sends the
     * player to the wrong machine.
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
     * How many entries a listing shows at most.
     *
     * <p>A panel holds six lines, a wall more — how many, only the client
     * that draws it knows. Eight is the number that fits a two-row wall
     * evenly and is honestly cut off on a single panel: the last line then
     * says how many are missing.
     */
    private static final int LIST_LIMIT = 8;

    public List<Line> evaluate(Decl.Display display) {
        List<Line> lines = new ArrayList<>();
        for (Decl.Display.Entry entry : display.entries()) {
            // scale is not a line but a statement about the panel. It sits
            // in the block because it belongs to this display — but it
            // writes nothing.
            if (entry.kind() == Decl.Display.Entry.Kind.SCALE) {
                continue;
            }
            // A listing is more than one line — that is its whole point and
            // the difference from row.
            if (entry.kind() == Decl.Display.Entry.Kind.LIST) {
                lines.addAll(listing(entry));
            } else {
                lines.add(evaluate(entry));
            }
        }
        return lines;
    }

    /**
     * A listing: a heading and one line per entry.
     *
     * <p>Descending by amount, because whoever looks at a wall is usually
     * looking for what there is too much or too little of. Whatever exceeds
     * {@link #LIST_LIMIT} is counted rather than dropped — a list that ends
     * silently reads like a complete inventory.
     */
    private List<Line> listing(Decl.Display.Entry entry) {
        List<Line> lines = new ArrayList<>();
        List<Map.Entry<String, Long>> posten = entries(entry.value());
        if (posten == null) {
            // Not a stock level but something else — then a single line as
            // before, including the message describe() knows for it.
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
     * The entries behind {@code storage.items()} or {@code brecher.items()},
     * or {@code null} if something else is there.
     *
     * <p>Only these two. A display does not compute — it reads, and the
     * boundary is the same as everywhere else in this class.
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
            return named(storage.byItem(), item -> item.getDescription().getString());
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
        // A machine can hold both. Counting them separately and showing them
        // together is exactly what one wants to know standing in front of it.
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
            // Never reached: evaluate(Decl.Display) skips it. The case is
            // there anyway so that the compiler complains again at the next
            // new building block.
            case SCALE -> Line.text(entry.kind(), null, "");
        };
    }

    /**
     * What an expression displays.
     *
     * <p>Deliberately few cases. Whatever is not listed here appears as a
     * hint on the display itself — an empty line would be worse, because
     * one would then look for the fault in the network instead of the
     * program.
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
        // A global value stands on its own — no call, no computation.
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
     * What is written next to the bar.
     *
     * <p>For a stock level the amount, for a fraction the percentage. "0.5"
     * next to a half-filled bar says the same thing twice and does not answer
     * the one question one has for a bar.
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
     * The number behind an expression, or {@code null}.
     *
     * <p>This is the entire arithmetic of a display: the four basic
     * operations over values that can be read. The recursion runs over the
     * parsed expression and therefore cannot get deeper than what the player
     * wrote.
     *
     * <p><b>{@code null} means "don't know", not "zero".</b> The display
     * relies on the difference: an unknown expression becomes "?", an empty
     * storage becomes "0".
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
                // Dividing by zero is not a number, and "infinity" on a bar
                // is worse than a question mark.
                case DIV -> right == 0 ? null : left / right;
                case MOD -> right == 0 ? null : left % right;
                default -> null;
            };
        }
        return null;
    }

    /** {@code depot.redstone()} — the signal strength at the connector, or {@code null}. */
    private Integer redstone(Expr expr) {
        if (level == null
                || !(expr instanceof Expr.Call call)
                || !(call.callee() instanceof Expr.Member member)
                || !"redstone".equals(member.name())
                || !(member.target() instanceof Expr.Name device)
                || !call.arguments().isEmpty()) {
            return null;
        }
        // Without a connector there is no answer — same as with online.
        return graph.connector(device.value())
                .map(where -> level.getBestNeighborSignal(where.pos()))
                .orElse(null);
    }

    /** Whole numbers without a decimal point, everything else with one digit. */
    private static String round(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.GERMAN, "%.1f", value);
    }

    /** {@code storage.count(item:…)} — the most common case on a display. */
    /**
     * {@code brecher.count(item:iron_ore)} — what is inside the machine.
     *
     * <p>One look into a BlockEntity per panel per second. The display reads
     * the network stock at that rate anyway; a {@code ?} on the panel that
     * nobody can explain would be the worse trade.
     *
     * <p>Without a world — in the tests — it stays at {@code null}: an
     * invented zero would send the player to the wrong machine.
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

    /** {@code ore_import.status} or a worker name on its own. */
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

    /** For progress bars: a value between zero and one. */
    private double fraction(Expr expr) {
        Long count = storageCount(expr);
        if (count != null) {
            // A bare stock level does not state its upper bound. A full
            // stack counts as full — whoever needs it more precise divides
            // themselves, and that is exactly what the arithmetic is for.
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
     * A comparison for an indicator, or {@code null}.
     *
     * <p>Numbers like {@code depot.redstone() > 0}, text like
     * {@code modus == "tag"}. The text comparison is no luxury: a global
     * value usually holds a word, and an indicator that cannot check that
     * word stays dark forever.
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

    /** Text that can be compared against: a literal or a global value. */
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

    /** The connector with this name, or {@code null}. */
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
