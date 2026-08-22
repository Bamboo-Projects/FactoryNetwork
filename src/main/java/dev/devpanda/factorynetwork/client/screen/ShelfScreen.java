package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Der Bildschirm eines Regals.
 *
 * <p>Zwei Zuschnitte, ein Bildschirm: Das Laufwerk hat zwei Spalten zu fünf
 * Reihen wie seine Schächte, der Schrank acht Einschübe nebeneinander wie
 * seine Front. Wer den Block ansieht und dann das Fenster, findet dieselbe
 * Anordnung wieder.
 */
public class ShelfScreen extends AbstractContainerScreen<ShelfMenu> {

    private static final ResourceLocation DRIVE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/drive.png");
    private static final ResourceLocation RACK = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/rack.png");

    /** Das Bild liegt auf einem 512er Blatt, wie alle Oberflächen der Mod. */
    private static final int SHEET = 512;

    public ShelfScreen(ShelfMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = menu.layout().height();
        this.inventoryLabelY = menu.layout().inventoryY() - 11;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.layout() == ShelfMenu.DRIVE ? DRIVE : RACK,
                leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
