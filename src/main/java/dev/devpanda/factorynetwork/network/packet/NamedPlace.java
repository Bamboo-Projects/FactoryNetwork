package dev.devpanda.factorynetwork.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A name in the network and the place it hangs on.
 *
 * <p><b>The name alone is not enough.</b> Whoever reads {@code crusher_1} in
 * the editor wants to know which machine that is — and in a factory with forty
 * furnaces that cannot be answered without a coordinate. Until now the network
 * state carried only names, and the question "which one of them is it" led
 * down into the basement.
 *
 * @param name what it is called
 * @param pos  where it stands
 */
public record NamedPlace(String name, BlockPos pos) {

    public static final StreamCodec<RegistryFriendlyByteBuf, NamedPlace> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), NamedPlace::name,
                    BlockPos.STREAM_CODEC, NamedPlace::pos,
                    NamedPlace::new);
}
