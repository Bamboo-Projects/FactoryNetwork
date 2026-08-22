package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.BurnerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Das Fenster der Brennkammer.
 *
 * <p>Eine Flamme über dem Brennstoff und ein Balken für den Vorrat. Der
 * Balken ist der wichtigere von beiden: Eine Kammer, die brennt, obwohl
 * niemand abnimmt, verheizt Kohle für nichts — und das sieht man nur daran,
 * dass der Vorrat oben ansteht.
 */
public class BurnerScreen extends AbstractContainerScreen<BurnerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/burner.png");

    /** Wo die gefüllten Fassungen im Blatt liegen. */
    private static final int FLAME_U = 180;
    private static final int FLAME_V = 0;
    private static final int ENERGY_U = 180;
    private static final int ENERGY_V = 20;

    private static final int FLAME_X = 80;
    private static final int FLAME_Y = 34;
    private static final int FLAME_W = 14;
    private static final int FLAME_H = 14;

    private static final int BAR_X = 12;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 8;
    private static final int BAR_H = 54;

    public BurnerScreen(BurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);

        // Die Flamme brennt von oben herunter, wie im Ofen.
        int flamme = Math.round(menu.burnFraction() * FLAME_H);
        if (flamme > 0) {
            graphics.blit(TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + (FLAME_H - flamme),
                    FLAME_U, FLAME_V + (FLAME_H - flamme), FLAME_W, flamme, 512, 512);
        }

        // Der Vorrat wächst von unten nach oben, wie ein Tank.
        int hoehe = Math.round(menu.energyFraction() * BAR_H);
        if (hoehe > 0) {
            graphics.blit(TEXTURE, leftPos + BAR_X, topPos + BAR_Y + (BAR_H - hoehe),
                    ENERGY_U, ENERGY_V + (BAR_H - hoehe), BAR_W, hoehe, 512, 512);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Am Balken steht die Zahl: „voll" allein sagt nicht, ob es reicht.
        if (mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + BAR_W
                && mouseY >= topPos + BAR_Y && mouseY < topPos + BAR_Y + BAR_H) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("screen.factorynetwork.burner.energy",
                            String.format(java.util.Locale.GERMANY, "%,d", menu.stored()),
                            String.format(java.util.Locale.GERMANY, "%,d", menu.capacity())),
                    Component.translatable("screen.factorynetwork.burner.rate",
                            dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.PER_TICK)),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }
}
