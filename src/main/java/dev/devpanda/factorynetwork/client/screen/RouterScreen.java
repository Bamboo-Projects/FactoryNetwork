package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.client.menu.RouterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Der Bildschirm des Routers: sechs Seiten, fünf Knöpfe je Seite.
 *
 * <p>Dieselben Farben wie die Ringe am Block — wer die Front gesehen hat,
 * erkennt hier dieselbe Bahn wieder. Und dieselbe Reihenfolge: aus, dann eins
 * bis vier.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/router.png");

    /**
     * Die Farben der Bahnen, dieselben wie in {@code textures.py}.
     *
     * <p>Doppelt aufgeschrieben, weil das Malskript nicht zur Laufzeit läuft.
     * Laufen sie auseinander, zeigt der Block eine andere Farbe als das
     * Fenster — die Sorte Fehler, die man sieht und nicht benennen kann.
     */
    private static final int[] LANE_COLOURS = {
            0xFF34383C, 0xFFECA830, 0xFF40C4E0, 0xFFD65CC4, 0xFF84D848};

    private static final int ROW_TOP = 32;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_LEFT = 62;
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_STEP = 20;

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 162;
        // Ein Router nimmt nichts auf; die Beschriftung „Inventar" wäre eine
        // Überschrift ohne Inhalt.
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);

        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            graphics.drawString(font, Component.translatable(
                            "side.factorynetwork." + side.getSerializedName()),
                    leftPos + 9, y + 4, 0x404040, false);

            int aktiv = menu.lane(side);
            for (int lane = 0; lane <= RouterBlockEntity.LANES; lane++) {
                int x = leftPos + BUTTON_LEFT + lane * BUTTON_STEP;
                boolean gewaehlt = lane == aktiv;
                // Der gewählte Knopf steht hell umrandet da; die anderen
                // liegen matt, damit die Zeile auf einen Blick zu lesen ist.
                graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE,
                        gewaehlt ? 0xFFFFFFFF : 0xFF555555);
                int inner = gewaehlt ? 1 : 2;
                graphics.fill(x + inner, y + inner, x + BUTTON_SIZE - inner,
                        y + BUTTON_SIZE - inner, LANE_COLOURS[lane]);
                if (lane > 0) {
                    String zahl = String.valueOf(lane);
                    graphics.drawString(font, zahl,
                            x + (BUTTON_SIZE - font.width(zahl)) / 2, y + 4, 0x201810, false);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Über den Knöpfen steht, was die Bahn trägt — die Zahl, für die man
        // sonst jede Seite einzeln ansehen müsste.
        int lane = laneAt(mouseX, mouseY);
        if (lane > 0) {
            graphics.renderTooltip(font, List.of(
                            Component.translatable("screen.factorynetwork.router.lane", lane),
                            Component.translatable("screen.factorynetwork.router.load",
                                    menu.formatLoad(lane))),
                    java.util.Optional.empty(), mouseX, mouseY);
        } else if (lane == 0) {
            graphics.renderTooltip(font,
                    Component.translatable("screen.factorynetwork.router.off"), mouseX, mouseY);
        }
    }

    /** Über welchem Knopf der Zeiger steht, oder -1. */
    private int laneAt(int mouseX, int mouseY) {
        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            if (mouseY < y || mouseY >= y + BUTTON_SIZE) {
                continue;
            }
            for (int lane = 0; lane <= RouterBlockEntity.LANES; lane++) {
                int x = leftPos + BUTTON_LEFT + lane * BUTTON_STEP;
                if (mouseX >= x && mouseX < x + BUTTON_SIZE) {
                    return lane;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            if (mouseY < y || mouseY >= y + BUTTON_SIZE) {
                continue;
            }
            for (int lane = 0; lane <= RouterBlockEntity.LANES; lane++) {
                int x = leftPos + BUTTON_LEFT + lane * BUTTON_STEP;
                if (mouseX >= x && mouseX < x + BUTTON_SIZE) {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            RouterMenu.buttonFor(side, lane));
                    minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
