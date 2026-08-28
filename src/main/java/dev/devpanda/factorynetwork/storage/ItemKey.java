package dev.devpanda.factorynetwork.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Unter welchem Schlüssel ein Gegenstand im Lager liegt.
 *
 * <p><b>Eine Registry-Kennung sagt, was etwas ist, aber nicht, welches.</b>
 * Zwei Spitzhacken sind dasselbe Item und trotzdem verschiedene Dinge, sobald
 * eine davon einen Namen trägt, verzaubert oder halb verbraucht ist. Bis zum
 * 28.08. führte das Lager nur die Kennung — und alles andere fiel beim
 * Einlagern weg, ohne dass es jemand merkte.
 *
 * <p>Abgeschaut bei AE2s {@code AEItemKey}. Zwei Eigenschaften daran sind
 * kein Zufall:
 *
 * <ol>
 *   <li><b>Der Stapel ist privat und immer eine Kopie.</b> Ein Schlüssel,
 *       dessen Hash sich ändert, während er in einer Map liegt, macht seinen
 *       Bestand unauffindbar: Er ist da und wird nie wieder gefunden.</li>
 *   <li><b>Die Stapelgrenze steht am Schlüssel</b>, nicht am Item. Die
 *       Komponente {@code MAX_STACK_SIZE} kann sie ändern, und das Entnehmen
 *       im Terminal rechnet damit.</li>
 * </ol>
 *
 * <p>Die Menge gehört <b>nicht</b> dazu. Ein Schlüssel sagt, was etwas ist;
 * wie viel davon daliegt, steht daneben — sonst wären drei Eisen und fünf
 * Eisen zwei Einträge.
 */
public final class ItemKey {

    /**
     * Wie ein Schlüssel auf der Platte steht.
     *
     * <p>Das Feld {@code components} ist <b>optional</b>: Ein Eintrag ohne
     * es ist ein Gegenstand ohne eigene Daten — genau das, was in jeder
     * heute bestehenden Zelle steht. Alte Zellen bleiben damit lesbar, ohne
     * dass irgendwo ein Migrationslauf nötig wäre.
     */
    public static final MapCodec<ItemKey> MAP_CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("Item")
                            .forGetter(ItemKey::item),
                    DataComponentPatch.CODEC
                            .optionalFieldOf("components", DataComponentPatch.EMPTY)
                            .forGetter(key -> key.stack.getComponentsPatch()))
                    .apply(builder, ItemKey::of));

    public static final Codec<ItemKey> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemKey> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM),
                    ItemKey::item,
                    DataComponentPatch.STREAM_CODEC,
                    key -> key.stack.getComponentsPatch(),
                    ItemKey::of);

    private final ItemStack stack;
    private final int hashCode;
    private final int maxStackSize;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hashCode = ItemStack.hashItemAndComponents(stack);
        this.maxStackSize = stack.getMaxStackSize();
    }

    /**
     * Der Schlüssel zu diesem Stapel, oder {@code null} für einen leeren.
     *
     * <p>Der Stapel wird kopiert — was der Aufrufer danach mit seinem macht,
     * geht den Schlüssel nichts mehr an.
     */
    public static @Nullable ItemKey of(ItemStack stack) {
        return stack.isEmpty() ? null : new ItemKey(stack.copyWithCount(1));
    }

    /** Ein Schlüssel ohne eigene Daten — der Weg, auf dem alte Zellen lesen. */
    public static ItemKey bare(Item item) {
        return new ItemKey(new ItemStack(item));
    }

    /** Aus Kennung und Daten zusammengesetzt, wie es auf der Platte steht. */
    public static ItemKey of(Item item, DataComponentPatch components) {
        return new ItemKey(new ItemStack(item.builtInRegistryHolder(), 1, components));
    }

    public Item item() {
        return stack.getItem();
    }

    /** Wie viele davon auf einen Stapel gehen. */
    public int maxStackSize() {
        return maxStackSize;
    }

    /** Trägt dieser Gegenstand überhaupt eigene Daten? */
    public boolean isBare() {
        return stack.isComponentsPatchEmpty();
    }

    /**
     * Ein echter Stapel daraus, in dieser Menge.
     *
     * <p>Immer neu gebaut: Wer den zurückgegebenen Stapel ändert, darf damit
     * nicht den Schlüssel ändern.
     */
    public ItemStack toStack(int count) {
        return stack.copyWithCount(count);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ItemKey key
                && hashCode == key.hashCode
                && ItemStack.isSameItemSameComponents(stack, key.stack));
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return isBare() ? item().toString() : item() + stack.getComponentsPatch().toString();
    }
}
