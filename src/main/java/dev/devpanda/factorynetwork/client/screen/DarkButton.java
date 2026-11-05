package dev.devpanda.factorynetwork.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button in the terminal's colours.
 *
 * <p>The vanilla button is a light stone slab; on the dark panes of this mod
 * it looked borrowed from another window. Same behaviour, same builder
 * shape, only drawn like the terminal's own buttons.
 */
public class DarkButton extends Button {

    /** Drawn as the chosen one of a row, for tabs made of buttons. */
    private boolean selected;

    protected DarkButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static Builder dark(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public DarkButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean hot = active && isHoveredOrFocused();
        graphics.fill(x0, y0, x1, y1, TerminalScreen.PANE_EDGE);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1,
                hot ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        if (selected) {
            graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, TerminalScreen.ACCENT);
        }
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 4);
        int colour = !active ? TerminalScreen.TEXT_FAINT
                : selected ? TerminalScreen.ACCENT
                : hot ? TerminalScreen.TEXT : TerminalScreen.TEXT_DIM;
        graphics.text(font, label, x0 + (getWidth() - font.width(label)) / 2,
                y0 + (getHeight() - 8) / 2, colour, false);
    }

    /** The same shape as {@link Button.Builder}, so a screen only swaps the class name. */
    public static final class Builder {

        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public DarkButton build() {
            return new DarkButton(x, y, width, height, message, onPress);
        }
    }
}
