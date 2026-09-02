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
 * Under which key an item sits in storage.
 *
 * <p><b>A registry id says what something is, but not which one.</b> Two
 * pickaxes are the same item and yet different things once one of them
 * carries a name, is enchanted or half-used. Until 28 Aug the store held
 * only the id — and everything else fell away on insertion, without anyone
 * noticing.
 *
 * <p>Borrowed from AE2's {@code AEItemKey}. Two of its properties are no
 * accident:
 *
 * <ol>
 *   <li><b>The stack is private and always a copy.</b> A key whose hash
 *       changes while it sits in a map makes its holdings unfindable: it is
 *       there and is never found again.</li>
 *   <li><b>The stack limit sits on the key</b>, not on the item. The
 *       {@code MAX_STACK_SIZE} component can change it, and extracting in the
 *       terminal reckons with that.</li>
 * </ol>
 *
 * <p>The amount does <b>not</b> belong to it. A key says what something is;
 * how much of it sits there stands alongside — otherwise three iron and five
 * iron would be two entries.
 */
public final class ItemKey {

    /**
     * How a key is stored on disk.
     *
     * <p>The field {@code components} is <b>optional</b>: an entry without it
     * is an item without its own data — exactly what sits in every cell that
     * exists today. Old cells stay readable that way, without a migration
     * pass being needed anywhere.
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
     * The key to this stack, or {@code null} for an empty one.
     *
     * <p>The stack is copied — what the caller does with theirs afterwards
     * is no longer any concern of the key.
     */
    public static @Nullable ItemKey of(ItemStack stack) {
        return stack.isEmpty() ? null : new ItemKey(stack.copyWithCount(1));
    }

    /** A key without its own data — the path by which old cells read. */
    public static ItemKey bare(Item item) {
        return new ItemKey(new ItemStack(item));
    }

    /** Assembled from id and data, as it is stored on disk. */
    public static ItemKey of(Item item, DataComponentPatch components) {
        return new ItemKey(new ItemStack(item.builtInRegistryHolder(), 1, components));
    }

    public Item item() {
        return stack.getItem();
    }

    /** How many of these go onto one stack. */
    public int maxStackSize() {
        return maxStackSize;
    }

    /** What sets this item apart from a freshly built one. */
    public net.minecraft.core.component.DataComponentPatch components() {
        return stack.getComponentsPatch();
    }

    /** Does this item carry any data of its own at all? */
    public boolean isBare() {
        return stack.isComponentsPatchEmpty();
    }

    /**
     * A real stack from it, in this amount.
     *
     * <p>Always built anew: whoever changes the returned stack must not
     * thereby change the key.
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
