package dev.devpanda.factorynetwork.mixin;

import dev.devpanda.factorynetwork.web.api.Overlays;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tasten und Zeichen, bevor Minecraft sie sieht.
 *
 * <p><b>Warum ein Mixin und kein Ereignis.</b> Ohne offenen Bildschirm
 * verarbeitet {@code KeyboardHandler.keyPress} Escape (Pausemenü) und die
 * Bewegungstasten ({@code KeyMapping.set}) selbst, und erst danach kommt
 * NeoForges {@code InputEvent.Key} — nicht abbrechbar. Ein Overlay, das
 * Escape und die Pfeile bekommen soll, während der Spieler weiterläuft,
 * muss davor stehen. Das geht nur hier.
 *
 * <p>Zwei Einhängungen, je eine Zeile, beide nur weiterreichend: Die
 * Entscheidung fällt in {@link Overlays}, wo sie ohne Mixin prüfbar ist.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void factorynetwork$overlayKey(long window, int key, int scanCode, int action,
                                            int modifiers, CallbackInfo info) {
        if (Overlays.keyPress(key, scanCode, action, modifiers)) {
            info.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void factorynetwork$overlayChar(long window, int codePoint, int modifiers,
                                             CallbackInfo info) {
        if (Overlays.charTyped(codePoint, modifiers)) {
            info.cancel();
        }
    }
}
