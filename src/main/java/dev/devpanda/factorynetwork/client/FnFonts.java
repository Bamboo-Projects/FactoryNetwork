package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The terminal's font.
 *
 * <p>Minecraft's default font is eight pixels tall and knows no bold weight.
 * <b>That makes every interface look the same</b>, no matter how carefully the
 * casing is drawn — and a code editor in the font that signs are also lettered
 * with does not look like a tool.
 *
 * <p>JetBrains Mono, because it is a font for code and because its licence
 * allows bundling it. Size nine, because Minecraft's line height is nine: at
 * ten the descenders bump into the next line.
 *
 * <p><b>Only in the terminal.</b> The player inventory, Jade and all messages
 * in the chat stay with vanilla — a mod that forces its own font everywhere
 * does not look better, only alien.
 */
public final class FnFonts {

    /** The font for code and numbers. */
    public static final ResourceLocation MONO =
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "mono");

    /** The same, bold — for headings and the active tab. */
    public static final ResourceLocation MONO_BOLD =
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "mono_bold");

    private static final Style MONO_STYLE = Style.EMPTY.withFont(MONO);
    private static final Style BOLD_STYLE = Style.EMPTY.withFont(MONO_BOLD);

    private FnFonts() {
    }

    public static MutableComponent mono(String text) {
        return Component.literal(text).setStyle(MONO_STYLE);
    }

    public static MutableComponent bold(String text) {
        return Component.literal(text).setStyle(BOLD_STYLE);
    }

    /** Applies the font to a finished text, e.g. from the language file. */
    public static MutableComponent mono(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(MONO));
    }

    public static MutableComponent bold(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(MONO_BOLD));
    }
}
