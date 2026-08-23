package dev.devpanda.factorynetwork.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ein Name im Netz und die Stelle, an der er hängt.
 *
 * <p><b>Der Name allein reicht nicht.</b> Wer im Editor {@code crusher_1}
 * liest, will wissen, welche Maschine das ist — und in einer Fabrik mit
 * vierzig Öfen ist das ohne Koordinate nicht zu beantworten. Bisher trug der
 * Netzzustand nur Namen, und die Frage „welcher davon ist es" führte in den
 * Keller.
 *
 * @param name wie er heißt
 * @param pos  wo er steht
 */
public record NamedPlace(String name, BlockPos pos) {

    public static final StreamCodec<RegistryFriendlyByteBuf, NamedPlace> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), NamedPlace::name,
                    BlockPos.STREAM_CODEC, NamedPlace::pos,
                    NamedPlace::new);
}
