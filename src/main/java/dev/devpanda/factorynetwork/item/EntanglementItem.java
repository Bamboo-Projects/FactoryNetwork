package dev.devpanda.factorynetwork.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Eine Hälfte einer Verschränkung — das Paar für die Quantum-Brücke.
 *
 * <p><b>Das Paar entsteht beim Bauen, nicht beim Anklicken.</b> Wer zwei
 * Brücken erst hinstellt und sie dann verbindet, muss sich merken, welche
 * wohin gehört — über fünftausend Blöcke hinweg, mit einer Karte in der Hand.
 * Wer zwei Hälften desselben Gegenstands einsetzt, muss gar nichts merken:
 * Sie gehören zusammen, egal wohin man sie trägt.
 *
 * <p>So macht es AE2 mit der Quantum-Entangled Singularity, und der Gedanke
 * ist der bessere von beiden.
 */
public class EntanglementItem extends Item {

    public EntanglementItem(Properties properties) {
        super(properties.stacksTo(2));
    }

    /**
     * Ein frisches Paar — ein Stapel zu zweit.
     *
     * <p><b>Ein Stapel und keine zwei Gegenstände.</b> Ein Rezept gibt es
     * einmal, nicht einmal je Werkbank; wer sich zwischen dem Zusammenbauen
     * und dem Herausnehmen etwas merkt, merkt es für alle Spieler
     * gleichzeitig. Zwei Bauten kreuzten sich dann, und jeder hielte eine
     * Hälfte, deren Partner woanders liegt.
     *
     * <p>Ein Stapel entsteht in einem Zug und trägt seine Nummer selbst.
     *
     * <p>Die Nummer ist zufällig. Zwei Paare aus demselben Rezept dürfen
     * sich nicht kennen, sonst verbänden sich zwei Brücken in derselben Welt
     * zufällig.
     */
    public static ItemStack newPair() {
        ItemStack stack = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.ENTANGLEMENT.get(), 2);
        stack.set(FnComponents.ENTANGLEMENT.get(), UUID.randomUUID());
        return stack;
    }

    /**
     * Welche Verschränkung dieser Stapel trägt, oder {@code null}.
     *
     * <p><b>Nur die Nummer, keine Seite.</b> Welche der beiden Hälften eine
     * ist, muss niemand wissen: Was ein Paar ausmacht, ist dieselbe Nummer
     * an <b>zwei verschiedenen Orten</b> — und das entscheidet der Block,
     * nicht der Gegenstand. Eine Brücke kann sich damit nicht mit sich
     * selbst verbinden, ohne dass es dafür eine Regel bräuchte.
     */
    public static @Nullable UUID idOf(ItemStack stack) {
        return stack.get(FnComponents.ENTANGLEMENT.get());
    }

    /**
     * Im Inventar sieht eine Hälfte aus wie jede andere.
     *
     * <p>Über fünftausend Blöcke will man vor dem Loslaufen wissen, welche
     * man trägt — dieselbe Überlegung wie beim Ferngerät, das sein Netz im
     * Tooltip nennt. Acht Zeichen der Nummer reichen, um zwei Paare
     * auseinanderzuhalten.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        UUID id = idOf(stack);
        if (id == null) {
            // Die Hälfte aus dem Kreativ-Reiter hat keine Nummer. Ohne diese
            // Zeile wirkt sie kaputt statt unfertig.
            lines.add(net.minecraft.network.chat.Component
                    .translatable("item.factorynetwork.entanglement.loose")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        lines.add(net.minecraft.network.chat.Component.translatable(
                        "item.factorynetwork.entanglement.pair",
                        id.toString().substring(0, 8))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Tragen diese beiden Stapel dieselbe Verschränkung? */
    public static boolean matched(ItemStack one, ItemStack other) {
        UUID first = idOf(one);
        return first != null && first.equals(idOf(other));
    }
}
