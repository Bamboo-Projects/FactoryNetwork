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

    /**
     * Welche Verschränkung, und welche ihrer beiden Hälften.
     *
     * <p><b>Die Seite gehört dazu.</b> Ohne sie wäre eine Hälfte ihr eigener
     * Partner, und eine Brücke könnte sich mit sich selbst verbinden — ein
     * Netz, das durch sich hindurch auf sich selbst zeigt.
     */
    public record Entanglement(UUID id, boolean second) {

        public static final Codec<Entanglement> CODEC = RecordCodecBuilder.create(
                builder -> builder.group(
                        UUIDUtil.CODEC.fieldOf("id").forGetter(Entanglement::id),
                        Codec.BOOL.fieldOf("second").forGetter(Entanglement::second))
                        .apply(builder, Entanglement::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entanglement> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, Entanglement::id,
                        ByteBufCodecs.BOOL, Entanglement::second,
                        Entanglement::new);

        /** Die andere Hälfte derselben Verschränkung. */
        public boolean partnerOf(Entanglement other) {
            return id.equals(other.id) && second != other.second;
        }
    }

    public EntanglementItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Ein frisches Paar.
     *
     * <p>Beide Hälften tragen dieselbe Nummer und verschiedene Seiten. Die
     * Nummer ist zufällig: Zwei Paare aus demselben Rezept dürfen sich nicht
     * kennen, sonst verbänden sich zwei Brücken in derselben Welt zufällig.
     */
    public static Pair<ItemStack, ItemStack> newPair() {
        UUID id = UUID.randomUUID();
        return Pair.of(half(id, false), half(id, true));
    }

    private static ItemStack half(UUID id, boolean second) {
        ItemStack stack = new ItemStack(
                dev.devpanda.factorynetwork.registry.FnItems.ENTANGLEMENT.get());
        stack.set(FnComponents.ENTANGLEMENT.get(), new Entanglement(id, second));
        return stack;
    }

    /** Welche Verschränkung dieser Stapel trägt, oder {@code null}. */
    public static @Nullable Entanglement of(ItemStack stack) {
        return stack.get(FnComponents.ENTANGLEMENT.get());
    }

    /** Die Nummer allein — zwei Brücken mit derselben gehören zusammen. */
    public static @Nullable UUID idOf(ItemStack stack) {
        Entanglement found = of(stack);
        return found == null ? null : found.id();
    }

    /** Sind das die beiden Hälften derselben Verschränkung? */
    public static boolean matched(ItemStack one, ItemStack other) {
        Entanglement first = of(one);
        Entanglement second = of(other);
        return first != null && second != null && first.partnerOf(second);
    }
}
