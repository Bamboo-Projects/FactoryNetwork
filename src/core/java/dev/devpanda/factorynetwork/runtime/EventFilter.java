package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Which devices an {@code on} block listens to: {@code on redstone_changed[sensor, pumpen](…)}.
 *
 * <p>Without brackets a block hears every connector, as before. With them it
 * hears the connectors named there, and the members of groups named there.
 * A filtered block may leave out the device in its parameters — it knows
 * which ones it asked for — so {@code (strength)} gets the strength.
 */
public final class EventFilter {

    private EventFilter() {
    }

    /** Whether this block wants an event with these arguments. */
    public static boolean matches(Program program, Decl.On handler, List<Value> arguments) {
        if (handler.devices().isEmpty()) {
            return true;
        }
        String device = deviceOf(arguments);
        return device != null && accepts(program, handler.devices(), device);
    }

    /** Whether the event's device is named in this filter. */
    public static boolean matchesDevices(Program program, List<String> filter, List<Value> arguments) {
        String device = deviceOf(arguments);
        return device != null && accepts(program, filter, device);
    }

    /** Whether a device is named in a filter, directly or through a group. */
    public static boolean accepts(Program program, List<String> filter, String device) {
        for (String wanted : filter) {
            if (wanted.equals(device)) {
                return true;
            }
            for (Decl declaration : program.declarations()) {
                if (declaration instanceof Decl.Group group && group.name().equals(wanted)
                        && inGroup(group, device)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The values bound to the block's parameters.
     *
     * <p>A filtered block with one name fewer than the event brings gets the
     * values without the device in front.
     */
    public static List<Value> arguments(Decl.On handler, List<Value> arguments) {
        if (!handler.devices().isEmpty() && !arguments.isEmpty()
                && handler.parameters().size() == arguments.size() - 1) {
            return arguments.subList(1, arguments.size());
        }
        return arguments;
    }

    /** Whether any block for this event hears every device. */
    public static boolean listensToAll(Program program, String event) {
        return program.handlers().stream()
                .anyMatch(handler -> handler.name().equals(event) && handler.devices().isEmpty());
    }

    /** Whether some block for this event hears this device. */
    public static boolean listensTo(Program program, String event, String device) {
        for (Decl.On handler : program.handlers()) {
            if (handler.name().equals(event)
                    && (handler.devices().isEmpty() || accepts(program, handler.devices(), device))) {
                return true;
            }
        }
        return false;
    }

    private static String deviceOf(List<Value> arguments) {
        if (arguments.isEmpty()) {
            return null;
        }
        return switch (arguments.get(0)) {
            case Value.Device device -> device.name();
            case Value.Text text -> text.value();
            default -> null;
        };
    }

    private static boolean inGroup(Decl.Group group, String device) {
        for (Expr member : group.members()) {
            if (member instanceof Expr.Name name && name.value().equals(device)) {
                return true;
            }
            if (member instanceof Expr.NamePattern pattern && glob(pattern.pattern()).matcher(device).matches()) {
                return true;
            }
        }
        return false;
    }

    /** A name pattern; only {@code *} counts, as in groups. */
    private static Pattern glob(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            regex.append(c == '*' ? ".*" : Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
