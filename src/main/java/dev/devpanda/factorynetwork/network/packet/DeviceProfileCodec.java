package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Bringt ein {@link DeviceProfile} über die Leitung.
 *
 * <p>Der Umweg über eine flache Form hat einen Grund: Eine Karte von
 * Aufzählung auf Aufzählung mit einem Verbundwert lässt sich mit
 * {@code StreamCodec.composite} nicht bauen, und von Hand geschriebene
 * Puffercodes sind schwer zu prüfen. Die flache Form ist eine Liste von
 * Vieren und lässt sich ohne Minecraft testen.
 */
public final class DeviceProfileCodec {

    /** Ein Zugang als flache Vier: Seite, Fächer, Behälter, Strom. */
    public record FlatAccess(int side, int slots, int tanks, boolean energy) {

        public static final StreamCodec<RegistryFriendlyByteBuf, FlatAccess> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FlatAccess::side,
                        ByteBufCodecs.VAR_INT, FlatAccess::slots,
                        ByteBufCodecs.VAR_INT, FlatAccess::tanks,
                        ByteBufCodecs.BOOL, FlatAccess::energy,
                        FlatAccess::new);
    }

    /** Ein ganzes Profil, flach. */
    public record Flat(String name, String descriptionId, String namespace,
                       int connectedSide, List<FlatAccess> access) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Flat> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(256), Flat::name,
                        ByteBufCodecs.stringUtf8(256), Flat::descriptionId,
                        ByteBufCodecs.stringUtf8(128), Flat::namespace,
                        ByteBufCodecs.VAR_INT, Flat::connectedSide,
                        FlatAccess.STREAM_CODEC.apply(ByteBufCodecs.list(7)), Flat::access,
                        Flat::new);
    }

    private DeviceProfileCodec() {
    }

    /** Ohne Namen — für den Test und für die Antwort auf eine Anfrage. */
    public static Flat toFlat(DeviceProfile profile) {
        return toFlat("", profile);
    }

    public static Flat toFlat(String name, DeviceProfile profile) {
        List<FlatAccess> flat = new ArrayList<>();
        for (Map.Entry<Side, DeviceProfile.Access> entry : profile.access().entrySet()) {
            flat.add(new FlatAccess(entry.getKey().ordinal(), entry.getValue().slots(),
                    entry.getValue().tanks(), entry.getValue().energy()));
        }
        return new Flat(name, profile.descriptionId(), profile.namespace(),
                profile.connectedSide().ordinal(), flat);
    }

    public static DeviceProfile fromFlat(Flat flat) {
        Map<Side, DeviceProfile.Access> access = new EnumMap<>(Side.class);
        for (FlatAccess entry : flat.access()) {
            access.put(Side.values()[entry.side()],
                    new DeviceProfile.Access(entry.slots(), entry.tanks(), entry.energy()));
        }
        return new DeviceProfile(flat.descriptionId(), flat.namespace(),
                Side.values()[flat.connectedSide()], access);
    }
}
