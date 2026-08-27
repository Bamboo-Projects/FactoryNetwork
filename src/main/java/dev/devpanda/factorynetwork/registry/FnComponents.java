package dev.devpanda.factorynetwork.registry;

import com.mojang.serialization.Codec;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Was Gegenstände dieser Mod an sich tragen.
 *
 * <p><b>Warum eigene Komponenten und kein NBT-Klumpen.</b> Der Bestand legt
 * Kleinkram über {@code CustomData} ab — ein Tag, in dem alles landet. Das
 * trägt, solange nur diese Mod hineinschaut. Der Akku aber wird von fremden
 * Mods gefüllt: Powah, Flux Networks und alles, was {@code IEnergyStorage}
 * spricht, greift über {@link net.neoforged.neoforge.energy.ComponentEnergyStorage}
 * zu — und die verlangt eine <b>angemeldete</b> Komponente, keinen Untertag.
 *
 * <p>Was einmal angemeldet ist, wird zudem von selbst über die Leitung
 * geschickt und im Tooltip vergleichbar. Beides fällt bei {@code CustomData}
 * aus.
 */
public final class FnComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FactoryNetwork.MOD_ID);

    /**
     * Der Ladestand eines Geräts.
     *
     * <p>Die Zahl allein — wie viel hineinpasst, sagt das Gerät, nicht der
     * Stapel. Sonst hätte ein alter Gegenstand nach einer Änderung noch die
     * alte Kapazität.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.register("energy", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Der Sendemast, an dem ein Gerät angemeldet ist.
     *
     * <p><b>Der Mast und nicht der Controller:</b> Ein Netz kann mehrere
     * Masten haben, und welcher davon reicht, hängt an seinen Karten. Wer den
     * Controller merkte, verlöre die Frage nach der Reichweite.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> MAST =
            COMPONENTS.register("mast", () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .build());

    /**
     * Der Name des gekoppelten Netzes, für die Anzeige.
     *
     * <p>Steht hier, damit der Tooltip ihn zeigen kann, ohne dass der Client
     * die Welt an der Mastposition kennt — im Inventar hat er sie nicht.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NETWORK_NAME =
            COMPONENTS.register("network_name", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private FnComponents() {
    }
}
