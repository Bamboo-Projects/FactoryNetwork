package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.client.menu.NameMenu;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.network.packet.SetBlockNamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Das Fenster, in dem ein Block seinen Namen bekommt.
 *
 * <p>Ein Feld, zwei Knöpfe, eine Zeile Rückmeldung. <b>Jede Höhe ist eine
 * Summe</b> — die Oberfläche der Beschriftungspistole hatte ihre Werte
 * geraten, und die Knöpfe standen über dem Rand hinaus. Wer eine Fläche
 * zeichnet, rechnet sie von einer Kante zur anderen durch.
 */
public class NameScreen extends AbstractContainerScreen<NameMenu> {

    private static final int PAD = 8;
    private static final int FIELD = 20;
    private static final int BUTTON = 20;
    private static final int LINE = 11;

    /** Der Schrägstrich, der eine Anlage von ihrer Rolle trennt. */
    private static final char SEPARATOR =
            dev.devpanda.factorynetwork.runtime.MultiblockInstances.SEPARATOR;

    /**
     * Die Anlage, zu der dieses Gerät gehört — oder leer.
     *
     * <p><b>Ein eigenes Feld und kein Schrägstrich zum Tippen.</b> Eine
     * Anlage entsteht dadurch, dass ihre Geräte {@code werk_1/eingang}
     * heißen; das steht so in {@code anlagen.md}. Nur sah man dem Fenster
     * nicht an, dass es das gibt — und bis heute ließ es sich dort nicht
     * einmal eintippen.
     *
     * <p>Was hinausgeht, ist derselbe Name wie vorher. Die zwei Felder sind
     * eine Lesehilfe und kein zweites Format: Wer den Schrägstrich ins
     * Namensfeld tippt, bekommt dasselbe Ergebnis.
     */
    private EditBox instance;

    private EditBox input;
    private Component note = Component.empty();
    private int noteColour = TerminalScreen.TEXT_DIM;

    public NameScreen(NameMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 192;
        // Rand, Titel, Anlage, Titel, Feld, Hinweiszeile, Knöpfe, Rand.
        this.imageHeight = PAD + LINE + 4 + FIELD + 4
                + LINE + 4 + FIELD + 3 + LINE + 4 + BUTTON + PAD;
        // Am Gateway fehlt das obere Feld samt Beschriftung.
        if (isGateway()) {
            this.imageHeight -= LINE + 4 + FIELD + 4;
        }
    }

    /** Der Teil vor dem Schrägstrich, oder leer. */
    private static String instancePart(String label) {
        int cut = label.indexOf(SEPARATOR);
        return cut <= 0 ? "" : label.substring(0, cut);
    }

    /** Der Teil dahinter — oder der ganze Name, wenn keiner dasteht. */
    private static String rolePart(String label) {
        int cut = label.indexOf(SEPARATOR);
        return cut <= 0 ? label : label.substring(cut + 1);
    }

    /** Was am Ende hinausgeht. */
    private String composed() {
        String rolle = ConnectorNaming.normalize(input.getValue());
        if (instance == null) {
            return rolle;
        }
        String anlage = ConnectorNaming.normalize(instance.getValue());
        return anlage.isEmpty() ? rolle : anlage + SEPARATOR + rolle;
    }

    /** Was gerade dransteht — aus dem Block, nicht aus dem Paket. */
    private String currentName() {
        var level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return "";
        }
        var entity = level.getBlockEntity(menu.position());
        if (entity instanceof DisplayBlockEntity display) {
            return display.displayName();
        }
        if (entity instanceof ConnectorBlockEntity connector) {
            return connector.label();
        }
        if (entity instanceof dev.devpanda.factorynetwork.block.entity
                .GatewayBlockEntity gateway) {
            return gateway.instance();
        }
        return "";
    }

    /**
     * Ob am anderen Ende ein Gateway hängt.
     *
     * <p>Dann steht dort <b>nur</b> die Anlage: Ein Gateway hat keine Rolle,
     * und ein zweites Feld dafür wäre eine Frage ohne Antwort.
     */
    private boolean isGateway() {
        var level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.position())
                instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity;
    }

    /** Ob am anderen Ende eine Anzeige hängt — nur für die Überschrift. */
    private boolean isDisplay() {
        var level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.position())
                instanceof DisplayBlockEntity;
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos + PAD;
        int width = imageWidth - 2 * PAD;
        int top = topPos + PAD + LINE + 4;
        String label = currentName();

        // Am Gateway gibt es nur die Anlage: Es hat keine Rolle, und ein
        // leeres zweites Feld wäre eine Frage, auf die es keine Antwort gibt.
        if (!isGateway()) {
            instance = new EditBox(font, left, top, width, FIELD,
                    Component.translatable("screen.factorynetwork.name.instance"));
            instance.setMaxLength(64);
            instance.setValue(instancePart(label));
            instance.setResponder(text -> onTyped(input == null ? "" : input.getValue()));
            addRenderableWidget(instance);
        }

        int y = isGateway() ? top : top + FIELD + 4 + LINE + 4;
        input = new EditBox(font, left, y, width, FIELD,
                Component.translatable("screen.factorynetwork.name.field"));
        input.setMaxLength(64);
        input.setValue(isGateway() ? label : rolePart(label));
        input.setResponder(this::onTyped);
        addRenderableWidget(input);
        setInitialFocus(input);

        int buttons = y + FIELD + 3 + LINE + 4;
        int half = (width - 4) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.factorynetwork.name.apply"),
                button -> confirm()).bounds(left, buttons, half, BUTTON).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                button -> onClose()).bounds(left + width - half, buttons, half, BUTTON).build());
        onTyped(input.getValue());
    }

    /**
     * Beim Tippen dieselben Regeln wie beim Übernehmen.
     *
     * <p>Ob der Name schon vergeben ist, weiß der Client nicht — das prüft
     * der Server. Hier steht nur, was sich ohne Netzwissen sagen lässt.
     */
    private void onTyped(String text) {
        // Geprüft wird der zusammengesetzte Name und nicht das Feld: Was
        // hinausgeht, ist werk_1/eingang, und daran hängen die Regeln.
        ConnectorNaming.Warning result = ConnectorNaming.check(composed(), null);
        switch (result.kind()) {
            case EMPTY -> {
                note = Component.translatable("screen.factorynetwork.name.empty");
                noteColour = TerminalScreen.TEXT_FAINT;
            }
            case NOT_AN_IDENTIFIER -> {
                note = Component.translatable("screen.factorynetwork.name.invalid");
                noteColour = TerminalScreen.BAD;
            }
            case KEYWORD -> {
                note = Component.translatable("screen.factorynetwork.name.keyword", text);
                noteColour = TerminalScreen.WARN;
            }
            default -> {
                // Der volle Name als Rückmeldung: In einer Anlage steht am
                // Ende etwas anderes da als das, was man gerade tippt.
                note = FnFonts.mono(Component.literal(composed()));
                noteColour = TerminalScreen.GOOD;
            }
        }
    }

    private void confirm() {
        String name = composed();
        if (!name.isEmpty() && !ConnectorNaming.isValidDeviceName(name)) {
            return;
        }
        PacketDistributor.sendToServer(new SetBlockNamePacket(menu.position(), name));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Eingabetaste übernimmt — sonst muss man mit der Maus zurück.
        if (key == 257 || key == 335) {
            confirm();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        // <b>Solange das Feld tippt, gehören ihm alle übrigen Tasten.</b>
        // AbstractContainerScreen.keyPressed schließt das Fenster, wenn die
        // Taste die Inventartaste ist — und die ist standardmäßig „e". Wer
        // einen Connector „extruder_1" nennen wollte, stand nach dem zweiten
        // Buchstaben wieder in der Welt. Vanilla löst es im Amboss genauso:
        // erst das Feld fragen, dann canConsumeInput, und nur wenn beides
        // verneint, geht die Taste weiter.
        if (input.keyPressed(key, scanCode, modifiers) || input.canConsumeInput()
                || (instance != null
                        && (instance.keyPressed(key, scanCode, modifiers)
                                || instance.canConsumeInput()))) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1,
                topPos + imageHeight + 1, 0xFF080A09);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight,
                0xFF232B27);
        int first = topPos + PAD + LINE + 3;
        graphics.fill(leftPos + PAD - 1, first,
                leftPos + imageWidth - PAD + 1, first + FIELD + 1, 0xFF080A09);
        if (instance != null) {
            int second = first + FIELD + 4 + LINE + 4;
            graphics.fill(leftPos + PAD - 1, second,
                    leftPos + imageWidth - PAD + 1, second + FIELD + 1, 0xFF080A09);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int second = PAD;
        if (instance != null) {
            graphics.drawString(font, FnFonts.mono(Component.translatable(
                            "screen.factorynetwork.name.instance")),
                    PAD, PAD, TerminalScreen.TEXT_DIM, false);
            second = PAD + LINE + 4 + FIELD + 4;
        }
        graphics.drawString(font, FnFonts.mono(Component.translatable(
                        isGateway() ? "screen.factorynetwork.name.title.gateway"
                                : isDisplay() ? "screen.factorynetwork.name.title.display"
                                        : "screen.factorynetwork.name.title.connector")),
                PAD, second, TerminalScreen.TEXT, false);
        graphics.drawString(font, note, PAD, second + LINE + 4 + FIELD + 3,
                noteColour, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
