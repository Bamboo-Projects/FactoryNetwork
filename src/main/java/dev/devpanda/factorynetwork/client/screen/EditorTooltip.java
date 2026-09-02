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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What is shown on hover in the editor.
 *
 * <p>In one place, not in every window separately. The occasion was a
 * disparity nobody had noticed: the standalone window explained a name under
 * the cursor — its spot in the network, where it is declared, its references —
 * the terminal's tab did not. The same question, two answers, depending on
 * where you type.
 *
 * <p>The order is deliberate: a name is the most precise thing that can be
 * under the cursor. The signature applies to the whole line, the message too.
 */
public final class EditorTooltip {

    /** More references covered half the screen. */
    private static final int MAX_PLACES = 5;

    /** And more occupied slots did too. */
    private static final int MAX_SLOTS_SHOWN = 6;

    private EditorTooltip() {
    }

    /**
     * Draws the tooltip, if there is one.
     *
     * <p>The checks for buttons and open menus stay with the respective
     * window — they differ, and they know their own areas.
     */
    public static void render(GuiGraphics graphics, Font font, CodeEditor editor,
                              Project project, List<Diagnostic> problems,
                              int mouseX, int mouseY) {
        if (describeName(graphics, font, editor, project, mouseX, mouseY)) {
            return;
        }
        ClientDeviceState.notHovering();

        if (describeSelector(graphics, font, editor, mouseX, mouseY)) {
            return;
        }

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
     * What the selector expression under the cursor currently resolves to.
     *
     * <p><b>A pattern is a search</b>, and a search without a list of matches
     * is a promise into the blue: {@code maintain 64 tag:c/ores} keeps
     * sixty-four of every kind, and how many kinds that is only the pack
     * knows.
     *
     * <p>It is resolved against the client's registry — the same one JEI reads
     * from. What stands here holds for this world and not for the language.
     *
     * @return whether anything was drawn
     */
    private static boolean describeSelector(GuiGraphics graphics, Font font,
                                            CodeEditor editor, int mouseX, int mouseY) {
        var selector = dev.devpanda.factorynetwork.lang.Selectors.parse(
                editor.selectorAt(mouseX, mouseY));
        if (selector == null) {
            return false;
        }
        List<String> summary =
                dev.devpanda.factorynetwork.runtime.SelectionSummary.of(selector);
        if (summary.isEmpty()) {
            return false;
        }
        List<Component> lines = new ArrayList<>();
        // The first line is the number, and it is the answer. Red when there
        // is none: a tag this pack does not know looks in the editor like any
        // other.
        lines.add(Component.literal(("trifft nichts".equals(summary.get(0)) ? "§c" : "§f")
                + summary.get(0)));
        for (int i = 1; i < summary.size(); i++) {
            lines.add(Component.literal("§7" + summary.get(i)));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    /**
     * The name under the cursor, explained.
     *
     * <p>The order in the box follows the question you ask: first what it is,
     * then where it is, then what happens with it in the program.
     *
     * @return whether anything was drawn
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

        // Before the check for a known profile: a device whose chunk was not
        // loaded when the screen opened has none yet — and the reply to the
        // request brings it along. Whoever only asks once something is already
        // known never gets anything for exactly these devices.
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

    /** What the machine is, can do, and currently holds. */
    private static void addDevice(List<Component> lines, String connector) {
        DeviceProfile profile = profileOf(connector);
        if (!profile.reachable()) {
            lines.add(Component.literal("§8Nicht geladen — über die Maschine ist "
                    + "nichts bekannt."));
            return;
        }
        // The connector points into the void. That is a message of its own and
        // not "nichts anzuschließen": there is no machine there at all, and the
        // hint reads differently — turn it around instead of looking for
        // another side.
        if (profile.access().isEmpty() && profile.descriptionId().endsWith(".air")) {
            lines.add(Component.literal("§cZeigt ins Leere — dort steht keine Maschine."));
            lines.add(Component.literal("§7Dreh den Connector auf die Seite, an der sie steht."));
            return;
        }
        lines.add(Component.translatable(profile.descriptionId())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("§7Angeschlossen: "
                + profile.connectedSide().written()));

        // Sides with the same access stand together — a machine that can do
        // the same thing on four sides should not say so four times.
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

        // Nothing works at all on the connected side — the mistake you would
        // otherwise only find by trial and error.
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
     * What is currently inside, once the reply is here.
     *
     * <p>Before that, nothing stands here. The box then jumps by a few lines —
     * better than one that is not there at all for a quarter of a second.
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
        if (snapshot.levels().energyCapacity() > 0) {
            lines.add(Component.literal("§7Strom: " + snapshot.levels().energy()
                    + " / " + snapshot.levels().energyCapacity()));
        }
        for (String tank : snapshot.levels().tanks()) {
            lines.add(Component.literal("§7" + tank));
        }
        addProbes(lines, snapshot);
    }

    /**
     * What the slots can do — and what they would settle for.
     *
     * <p><b>Two different pieces of information on one line</b>, and the
     * difference has to stay legible: "nimmt auf" and "gibt ab" are facts that
     * were checked. What comes after is a <b>sample</b> from what stands in the
     * program — a machine may accept a hundred things, of which only those
     * someone is currently writing about show up here.
     *
     * <p>That is why it says "passt" and not "nimmt an": the one says something
     * about the checked item, the other about the machine.
     */
    private static void addProbes(List<Component> lines, DeviceSnapshotPacket snapshot) {
        int shown = 0;
        for (DeviceSnapshotPacket.SlotProbe probe : snapshot.probes()) {
            if (!probe.takes() && !probe.gives()) {
                continue;
            }
            if (shown == MAX_SLOTS_SHOWN) {
                lines.add(Component.literal("§8…"));
                break;
            }
            StringBuilder text = new StringBuilder("§8Fach " + probe.slot() + ": ");
            text.append(probe.takes() ? "nimmt auf" : "gibt ab");
            if (probe.takes() && probe.gives()) {
                text.append(", gibt ab");
            }
            lines.add(Component.literal(text.toString()));
            if (!probe.accepts().isEmpty()) {
                MutableComponent passt = Component.literal("§8    ");
                for (int i = 0; i < probe.accepts().size(); i++) {
                    if (i > 0) {
                        passt.append(Component.literal("§8, "));
                    }
                    passt.append(Component.translatable(probe.accepts().get(i))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                lines.add(passt.append(Component.literal("§8 passt")));
            }
            shown++;
        }
    }

    /** Where the name is declared and where else it occurs. */
    private static void addDeclaration(List<Component> lines, Project project, String word,
                                       Definitions.Location declared) {
        List<Definitions.Location> places = Definitions.references(project, word);
        lines.add(Component.translatable("screen.factorynetwork.code.declared_in",
                        declared.file(), declared.line())
                .withStyle(ChatFormatting.GRAY));
        // The declaration itself is a reference; what is counted is what else
        // is there.
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
     * The profile, preferably from the latest reply.
     *
     * <p>The reply to a request carries the structure along. Whoever prefers
     * it picks up a swapped-out machine — and a device whose chunk was not
     * loaded when the screen opened, in the first place.
     */
    private static DeviceProfile profileOf(String connector) {
        DeviceSnapshotPacket snapshot = ClientDeviceState.snapshot(connector);
        if (snapshot != null) {
            return DeviceProfileCodec.fromFlat(snapshot.profile());
        }
        return ClientNetworkState.profile(connector);
    }
}
