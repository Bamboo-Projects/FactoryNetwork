package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Die Schrift des Terminals.
 *
 * <p>Minecrafts Standardschrift ist acht Pixel hoch und kennt kein
 * Fettgewicht. <b>Damit sieht jede Oberfläche gleich aus</b>, egal wie sorgfältig
 * das Gehäuse gezeichnet ist — und ein Code-Editor in der Schrift, mit der
 * auch Schilder beschriftet werden, sieht nicht nach Werkzeug aus.
 *
 * <p>JetBrains Mono, weil es eine Schrift für Code ist und weil ihre Lizenz
 * das Mitliefern erlaubt. Größe neun, weil Minecrafts Zeilenhöhe neun ist:
 * Bei zehn stoßen die Unterlängen in die nächste Zeile.
 *
 * <p><b>Nur im Terminal.</b> Das Spielerinventar, Jade und alle Meldungen im
 * Chat bleiben bei Vanilla — eine Mod, die überall ihre eigene Schrift
 * durchsetzt, sieht nicht besser aus, sondern fremd.
 */
public final class FnFonts {

    /** Die Schrift für Code und Zahlen. */
    public static final ResourceLocation MONO =
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "mono");

    /** Dieselbe, fett — für Überschriften und den aktiven Reiter. */
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

    /** Legt die Schrift auf einen fertigen Text, etwa aus der Sprachdatei. */
    public static MutableComponent mono(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(MONO));
    }

    public static MutableComponent bold(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(MONO_BOLD));
    }
}
