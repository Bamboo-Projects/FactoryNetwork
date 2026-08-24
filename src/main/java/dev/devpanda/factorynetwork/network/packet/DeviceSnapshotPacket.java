package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DeviceScan;
import dev.devpanda.factorynetwork.client.ClientDeviceState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Was gerade in einem Gerät liegt.
 *
 * <p>Trägt die Struktur gleich mit: Damit ist der Fall „Maschine wurde
 * ausgetauscht, während das Terminal offen ist" nebenbei erledigt, ohne
 * dafür einen eigenen Mechanismus zu bauen — und ein Gerät, dessen Chunk
 * beim Öffnen nicht geladen war, bekommt überhaupt erst ein Profil.
 *
 * <p><b>Gedeckelt auf 64 Fächer</b>, und wenn gekürzt wurde, steht das
 * dabei. Ein Fassregal mit zweihundert Fächern soll den Tooltip nicht
 * sprengen — aber auch nicht heimlich lügen.
 */
public record DeviceSnapshotPacket(String connector, DeviceProfileCodec.Flat profile,
                                   List<ItemStack> slots, int slotsOmitted,
                                   int energy, int energyCapacity)
        implements CustomPacketPayload {

    /** Mehr passt in keinen Tooltip. */
    public static final int MAX_SLOTS = 64;

    public static final Type<DeviceSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "device_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshotPacket>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), DeviceSnapshotPacket::connector,
                    DeviceProfileCodec.Flat.STREAM_CODEC, DeviceSnapshotPacket::profile,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)),
                    DeviceSnapshotPacket::slots,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::slotsOmitted,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::energy,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::energyCapacity,
                    DeviceSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Liest das Gerät aus, oder liefert {@code null}, wenn es das nicht gibt. */
    public static DeviceSnapshotPacket of(ControllerBlockEntity controller, String connector) {
        if (controller.getLevel() == null) {
            return null;
        }
        BlockPos pos = controller.graph().connectors().get(connector);
        if (pos == null
                || !(controller.getLevel().getBlockEntity(pos)
                        instanceof ConnectorBlockEntity entity)) {
            return null;
        }

        List<ItemStack> stacks = new ArrayList<>();
        int omitted = 0;
        IItemHandler items = entity.machineInventory();
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                if (stacks.size() >= MAX_SLOTS) {
                    omitted = items.getSlots() - MAX_SLOTS;
                    break;
                }
                stacks.add(items.getStackInSlot(slot).copy());
            }
        }

        int energy = 0;
        int capacity = 0;
        IEnergyStorage power = entity.machineEnergy();
        if (power != null) {
            energy = power.getEnergyStored();
            capacity = power.getMaxEnergyStored();
        }

        return new DeviceSnapshotPacket(connector,
                DeviceProfileCodec.toFlat(connector, DeviceScan.of(entity)),
                stacks, omitted, energy, capacity);
    }

    public static void handle(DeviceSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeviceState.accept(packet));
    }
}
