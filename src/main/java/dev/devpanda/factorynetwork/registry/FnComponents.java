package dev.devpanda.factorynetwork.registry;

import com.mojang.serialization.Codec;
import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What items of this mod carry on themselves.
 *
 * <p><b>Why dedicated components and not an NBT blob.</b> The existing code
 * stows odds and ends in {@code CustomData} — a tag where everything ends up.
 * That holds as long as only this mod looks inside. The battery, however, is
 * filled by foreign mods: Powah, Flux Networks and everything that speaks
 * {@code IEnergyStorage} accesses it through {@link net.neoforged.neoforge.energy.ComponentEnergyStorage}
 * — and that demands a <b>registered</b> component, not a subtag.
 *
 * <p>What is registered once is moreover sent over the wire on its own and
 * comparable in the tooltip. Both fall away with {@code CustomData}.
 */
public final class FnComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FactoryNetwork.MOD_ID);

    /**
     * A device's charge level.
     *
     * <p>The number alone — how much fits is told by the device, not by the
     * stack. Otherwise an old item would still have the old capacity after a
     * change.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.register("energy", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The mast a device is registered with — together with its world.
     *
     * <p><b>The mast and not the controller:</b> a network can have several
     * masts, and which of them reaches depends on its cards. Whoever remembered
     * the controller would lose the question of range.
     *
     * <p><b>And the world belongs with it, not just the position.</b>
     * Coordinates repeat in every dimension: a device that only remembers
     * {@code 120, 64, -30} would connect in the Nether to a foreign mast that
     * happens to stand there. Vanilla does the same with the lodestone
     * compass.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> MAST =
            COMPONENTS.register("mast", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    /**
     * The name of the paired network, for display.
     *
     * <p>Lives here so the tooltip can show it without the client knowing the
     * world at the mast position — in the inventory it does not have it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NETWORK_NAME =
            COMPONENTS.register("network_name", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * Which entanglement a half carries.
     *
     * <p>Two halves with the same number belong together — on that hangs which
     * quantum bridge finds which.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<java.util.UUID>> ENTANGLEMENT =
            COMPONENTS.register("entanglement",
                    () -> DataComponentType.<java.util.UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    private FnComponents() {
    }
}
