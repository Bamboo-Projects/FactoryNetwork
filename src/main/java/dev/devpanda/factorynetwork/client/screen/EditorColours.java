package dev.devpanda.factorynetwork.client.screen;

/**
 * The colours of the code screen.
 *
 * <p><b>Careful when filling:</b> the values for text carry no alpha channel.
 * That works fine as long as they end up in {@code drawString} — Minecraft's
 * font renderer adds full opacity when the top bits are zero. A fill does
 * not: there the same value is fully transparent. Anyone using one of these
 * colours in {@code graphics.fill} writes {@code 0xFF000000 |} in front of
 * it. The cursor was transparent without that from the day it existed — you
 * don't see something like this, you go hunting for it.
 *
 * <p>The same values the block textures work with: blue for keywords, green
 * for selector expressions. Anyone who looks at the controller and then at
 * the code should recognise the same colour palette.
 */
public final class EditorColours {

    public static final int TEXT = 0xD8DEE4;
    public static final int MUTED = 0x8A939C;
    public static final int KEYWORD = 0x8AB4F8;
    public static final int SELECTOR = 0xA3D9A5;
    public static final int COMMENT = 0x6B7480;
    public static final int ERROR = 0xE88388;
    public static final int CURSOR = 0xD8DEE4;
    /** Background for a line with an error — at ten pixels of height no squiggle carries. */
    public static final int ERROR_LINE = 0x30E88388;

    /**
     * And the same for a warning.
     *
     * <p>Before, the two looked alike. A warning does not stop the program —
     * colouring it the same as an error means taking both equally seriously,
     * or both equally little.
     */
    public static final int WARNING_LINE = 0x30E8AC3E;

    /**
     * The selection: strong enough to see it, faint enough to still read the
     * text on top of it.
     */
    public static final int SELECTION = 0x664A7BC4;

    /**
     * Search hits.
     *
     * <p>Two shades: all matches dim, the one currently focused strong.
     * Without the distinction you can see that there are matches, but not
     * where you currently are.
     */
    public static final int MATCH = 0x40C8A03C;
    public static final int MATCH_CURRENT = 0x90C8A03C;

    /** The search bar above the text. */
    public static final int SEARCH_BAR = 0xC0202429;

    /**
     * The line the cursor sits on.
     *
     * <p>Very faint. It should guide the eye back when you look up from the
     * keyboard, and not demand to be read along with the rest. With a
     * selection it stays away — two backgrounds on top of each other are none.
     */
    public static final int CURRENT_LINE = 0x18FFFFFF;

    /** The number of the line the cursor sits on. */
    public static final int LINE_NUMBER_ACTIVE = 0xA8B2BC;

    /**
     * The vertical strokes of the indentation.
     *
     * <p>Almost invisible, and that is the point: they answer "which block
     * does this line belong to" at a glance, without getting in the way while
     * reading. In a language with curly braces that is the question asked
     * most often.
     */
    public static final int INDENT_GUIDE = 0x1EFFFFFF;

    /**
     * The bracket pair around the cursor.
     *
     * <p>Backed rather than framed: a frame around a character five pixels
     * wide is a smudge.
     */
    public static final int BRACKET = 0x605A8AC4;

    private EditorColours() {
    }
}
