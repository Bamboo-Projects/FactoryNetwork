package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeviceState;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Definitions;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Was beim Zeigen im Editor dasteht.
 *
 * <p>An einer Stelle und nicht in jedem Fenster einzeln. Der Anlass war eine
 * Ungleichheit, die niemandem aufgefallen war: Das eigene Fenster erklärte
 * einen Namen unter dem Zeiger — Stelle im Netz, Erklärungsort, Fundstellen —,
 * der Reiter im Terminal nicht. Dieselbe Frage, zwei Antworten, je nachdem wo
 * man tippt.
 *
 * <p>Die Reihenfolge ist Absicht: Ein Name ist das Genaueste, was unter dem
 * Zeiger stehen kann. Die Signatur gilt für die ganze Zeile, die Meldung auch.
 */
public final class EditorTooltip {

    /** Mehr Fundstellen deckten den halben Bildschirm. */
    private static final int MAX_PLACES = 5;

    /** Und mehr belegte Fächer auch. */
    private static final int MAX_SLOTS_SHOWN = 6;

    private EditorTooltip() {
    }

    /**
     * Zeichnet den Tooltip, wenn es einen gibt.
     *
     * <p>Die Prüfungen auf Knöpfe und offene Menüs bleiben beim jeweiligen
     * Fenster — sie unterscheiden sich, und sie kennen ihre eigenen Flächen.
     */
    public static void render(GuiGraphics graphics, Font font, CodeEditor editor,
                              Project project, List<Diagnostic> problems,
                              int mouseX, int mouseY) {
        if (describeName(graphics, font, editor, project, mouseX, mouseY)) {
            return;
        }
        ClientDeviceState.notHovering();

        var signature = editor.signatureAt(mouseX, mouseY);
        if (signature != null) {
            graphics.renderComponentTooltip(font, List.of(
                    FnFonts.mono(signature.shape()),
                    Component.literal("§7" + signature.help())), mouseX, mouseY);
            return;
        }

        Diagnostic problem = editor.diagnosticAt(problems, mouseX, mouseY);
        if (problem == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(problem.message()));
        if (problem.hint() != null) {
            lines.add(Component.literal("§7" + problem.hint()));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Der Name unter dem Zeiger, erklärt.
     *
     * <p>Die Reihenfolge im Kasten folgt der Frage, die man stellt: erst was
     * es ist, dann wo es steht, dann was im Programm damit passiert.
     *
     * @return ob etwas gezeichnet wurde
     */
    private static boolean describeName(GuiGraphics graphics, Font font, CodeEditor editor,
                                        Project project, int mouseX, int mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        if (word.isEmpty()) {
            return false;
        }
        var declared = Definitions.find(project, word);
        BlockPos inWorld = ClientNetworkState.placeOf(word);
        boolean isConnector = ClientNetworkState.connectors().contains(word);
        if (declared.isEmpty() && inWorld == null) {
            return false;
        }

        // Vor der Prüfung auf ein bekanntes Profil: Ein Gerät, dessen Chunk
        // beim Öffnen nicht geladen war, hat noch keines — und die Antwort
        // auf die Anfrage bringt es mit. Wer hier erst fragt, wenn schon
        // etwas bekannt ist, bekommt für genau diese Geräte nie etwas.
        if (isConnector) {
            ClientDeviceState.hovering(word);
        } else {
            ClientDeviceState.notHovering();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(FnFonts.mono(word));
        if (isConnector) {
            addDevice(lines, word);
        }
        if (inWorld != null) {
            lines.add(Component.translatable("screen.factorynetwork.code.at",
                            inWorld.getX(), inWorld.getY(), inWorld.getZ())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.factorynetwork.code.locate_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (declared.isPresent()) {
            addDeclaration(lines, project, word, declared.get());
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    /** Was die Maschine ist, kann und gerade enthält. */
    private static void addDevice(List<Component> lines, String connector) {
        DeviceProfile profile = profileOf(connector);
        if (!profile.reachable()) {
            lines.add(Component.literal("§8Nicht geladen — über die Maschine ist "
                    + "nichts bekannt."));
            return;
        }
        lines.add(Component.translatable(profile.descriptionId())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("§7Angeschlossen: "
                + profile.connectedSide().written()));

        // Seiten mit gleichem Zugang stehen zusammen — eine Maschine, die an
        // vier Seiten dasselbe kann, soll das nicht viermal sagen.
        for (DeviceProfile.Group group : profile.grouped()) {
            List<String> what = new ArrayList<>();
            if (group.access().slots() > 0) {
                what.add(group.access().slots() + " Fächer");
            }
            if (group.access().tanks() > 0) {
                what.add(group.access().tanks() + " Behälter");
            }
            if (group.access().energy()) {
                what.add("Strom");
            }
            List<String> sides = group.sides().stream().map(Side::written).toList();
            lines.add(Component.literal("§8" + String.join(", ", sides)
                    + ": " + String.join(", ", what)));
        }

        // An der angeschlossenen Seite geht gar nichts — der Fehler, den man
        // sonst nur durch Ausprobieren findet.
        if (profile.accessAt(profile.connectedSide()) == null) {
            List<Side> elsewhere = new ArrayList<>(
                    profile.sidesWith(DeviceProfile.Access.Ability.ITEMS));
            elsewhere.addAll(profile.sidesWith(DeviceProfile.Access.Ability.FLUIDS));
            lines.add(Component.literal(elsewhere.isEmpty()
                    ? "§cDort ist nichts anzuschließen."
                    : "§cDort ist nichts anzuschließen — "
                            + elsewhere.get(0).written() + " ginge."));
        }
        addContents(lines, connector);
    }

    /**
     * Was gerade drin liegt, sobald die Antwort da ist.
     *
     * <p>Vorher steht hier nichts. Der Kasten springt dann um ein paar Zeilen
     * — besser als einer, der eine Viertelsekunde lang gar nicht da ist.
     */
    private static void addContents(List<Component> lines, String connector) {
        DeviceSnapshotPacket snapshot = ClientDeviceState.snapshot(connector);
        if (snapshot == null) {
            return;
        }
        int shown = 0;
        for (int slot = 0; slot < snapshot.slots().size(); slot++) {
            ItemStack stack = snapshot.slots().get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (shown == MAX_SLOTS_SHOWN) {
                lines.add(Component.literal("§8…"));
                break;
            }
            lines.add(Component.literal("§7" + slot + ": ")
                    .append(stack.getHoverName())
                    .append(Component.literal(" §7×" + stack.getCount())));
            shown++;
        }
        if (shown == 0 && !snapshot.slots().isEmpty()) {
            lines.add(Component.literal("§8leer"));
        }
        if (snapshot.slotsOmitted() > 0) {
            lines.add(Component.literal("§8und " + snapshot.slotsOmitted()
                    + " weitere Fächer"));
        }
        if (snapshot.energyCapacity() > 0) {
            lines.add(Component.literal("§7Strom: " + snapshot.energy()
                    + " / " + snapshot.energyCapacity()));
        }
    }

    /** Wo der Name erklärt wird und wo er sonst noch vorkommt. */
    private static void addDeclaration(List<Component> lines, Project project, String word,
                                       Definitions.Location declared) {
        List<Definitions.Location> places = Definitions.references(project, word);
        lines.add(Component.translatable("screen.factorynetwork.code.declared_in",
                        declared.file(), declared.line())
                .withStyle(ChatFormatting.GRAY));
        // Die Erklärung selbst ist eine Fundstelle; gezählt wird, was sonst
        // noch da ist.
        int used = Math.max(0, places.size() - 1);
        lines.add(Component.translatable("screen.factorynetwork.code.used", used)
                .withStyle(ChatFormatting.DARK_GRAY));
        int shown = 0;
        for (Definitions.Location place : places) {
            if (place.line() == declared.line() && place.file().equals(declared.file())) {
                continue;
            }
            if (shown++ >= MAX_PLACES) {
                break;
            }
            lines.add(Component.literal("§8  " + place.file() + ":" + place.line()));
        }
    }

    /**
     * Das Profil, bevorzugt aus der letzten Antwort.
     *
     * <p>Die Antwort auf eine Anfrage trägt die Struktur mit. Wer sie
     * bevorzugt, bekommt eine ausgetauschte Maschine mit — und ein Gerät,
     * dessen Chunk beim Öffnen nicht geladen war, überhaupt erst.
     */
    private static DeviceProfile profileOf(String connector) {
        DeviceSnapshotPacket snapshot = ClientDeviceState.snapshot(connector);
        if (snapshot != null) {
            return DeviceProfileCodec.fromFlat(snapshot.profile());
        }
        return ClientNetworkState.profile(connector);
    }
}
