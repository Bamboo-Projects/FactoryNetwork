package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.lang.TokenType;
import dev.devpanda.factorynetwork.runtime.MultiblockInstances;

/**
 * The script line a Ctrl+click on a numbered slot copies.
 *
 * <p>Plain strings in, a plain string out: the line has to parse, and that is
 * checked against the real parser without a client.
 */
public final class SlotLine {

    /** How much the copied line moves; a stack is what one usually means. */
    public static final int AMOUNT = 64;

    private SlotLine() {
    }

    /**
     * The line for one slot.
     *
     * @param device how the machine is called in code, see {@link #deviceName}
     * @param itemId {@code namespace:path} of what lies in the slot, or
     *               {@code null} for an empty slot
     * @param slot   the number for {@code slots(n)}
     */
    public static String of(String device, String itemId, int slot) {
        return "move " + AMOUNT + " " + selector(itemId) + " from " + device
                + ".slots(" + slot + ") to storage";
    }

    /**
     * How an item is written in the language.
     *
     * <p>The slash form from {@code sprache.md}, without {@code minecraft}:
     * that is how the docs and the editor write it. An empty slot gets
     * {@code item:*}; a placeholder like {@code item:?} would not parse, and
     * "whatever lies there" is what someone who copies an empty slot wants.
     */
    static String selector(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "item:*";
        }
        int colon = itemId.indexOf(':');
        if (colon < 0) {
            return "item:" + itemId;
        }
        String namespace = itemId.substring(0, colon);
        String path = itemId.substring(colon + 1);
        return namespace.equals("minecraft") ? "item:" + path : "item:" + namespace + "/" + path;
    }

    /**
     * A connector label as it has to stand in code.
     *
     * <p>A label like {@code werk_1/eingang} names a role inside an assembly;
     * the template only ever sees the role, so that is what is written. A
     * keyword or anything that is no identifier goes into backticks, the
     * same rule the editor's completion follows.
     */
    public static String deviceName(String label) {
        String name = ConnectorNaming.normalize(label);
        int cut = name.indexOf(MultiblockInstances.SEPARATOR);
        if (cut >= 0 && ConnectorNaming.isValidDeviceName(name)) {
            name = name.substring(cut + 1);
        }
        if (!ConnectorNaming.isValidIdentifier(name) || TokenType.isKeyword(name)) {
            return "`" + name + "`";
        }
        return name;
    }
}
