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
                                   Power power, List<SlotProbe> probes)
        implements CustomPacketPayload {

    /** Mehr passt in keinen Tooltip. */
    public static final int MAX_SLOTS = 64;

    /**
     * Der Stromstand der Maschine.
     *
     * <p>Als eigener Satz und nicht als zwei weitere Felder oben:
     * {@code StreamCodec.composite} trägt höchstens sechs, und die Probe
     * braucht eines davon.
     */
    public record Power(int stored, int capacity) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Power> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Power::stored,
                        ByteBufCodecs.VAR_INT, Power::capacity,
                        Power::new);
    }

    /**
     * Was ein Fach kann, und womit es sich abfinden würde.
     *
     * <p><b>{@code takes} und {@code gives} sind Tatsachen</b>, ermittelt aus
     * einem simulierten Einfügeversuch und einem simulierten Auszug.
     * {@code accepts} ist dagegen eine <b>Stichprobe</b>: die Gegenstände aus
     * dem Entwurf, die dieses Fach annehmen würde. Sie ist nie vollständig,
     * und sie darf nie so aussehen.
     *
     * @param slot    die laufende Nummer des Fachs
     * @param takes   nimmt es überhaupt etwas an
     * @param gives   lässt sich etwas herausnehmen
     * @param accepts welche der geprüften Gegenstände hineinpassen
     */
    public record SlotProbe(int slot, boolean takes, boolean gives, List<String> accepts) {

        public static final StreamCodec<RegistryFriendlyByteBuf, SlotProbe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, SlotProbe::slot,
                        ByteBufCodecs.BOOL, SlotProbe::takes,
                        ByteBufCodecs.BOOL, SlotProbe::gives,
                        ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(24)),
                        SlotProbe::accepts,
                        SlotProbe::new);
    }

    public static final Type<DeviceSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "device_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshotPacket>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), DeviceSnapshotPacket::connector,
                    DeviceProfileCodec.Flat.STREAM_CODEC, DeviceSnapshotPacket::profile,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)),
                    DeviceSnapshotPacket::slots,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::slotsOmitted,
                    Power.STREAM_CODEC, DeviceSnapshotPacket::power,
                    SlotProbe.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)),
                    DeviceSnapshotPacket::probes,
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
        IEnergyStorage stored = entity.machineEnergy();
        if (stored != null) {
            energy = stored.getEnergyStored();
            capacity = stored.getMaxEnergyStored();
        }

        return new DeviceSnapshotPacket(connector,
                DeviceProfileCodec.toFlat(connector, DeviceScan.of(entity)),
                stacks, omitted, new Power(energy, capacity),
                probe(items, controller.draft()));
    }

    /**
     * Was jedes Fach kann, und womit es sich abfinden würde.
     *
     * <p><b>Warum überhaupt geprobt wird:</b> {@code IItemHandler} kann nicht
     * sagen, was es annimmt — es gibt keine API dafür, und
     * {@code isItemValid} beantwortet nur die Frage nach einem konkreten
     * Gegenstand. Gemoddete Maschinen antworten darauf oft lax: Ein
     * Ofen-Eingang nimmt alles an und prüft das Rezept erst beim Verarbeiten.
     *
     * <p>Deshalb wird die Frage umgedreht — mit den Gegenständen, die im
     * Entwurf stehen. Wer {@code item:iron_ore} tippt, fragt sich über
     * Eisenerz etwas.
     *
     * <p>Alles hier ist <b>simuliert</b>: {@code insertItem} und
     * {@code extractItem} mit {@code simulate = true} bewegen nichts.
     */
    private static List<SlotProbe> probe(IItemHandler items,
                                         dev.devpanda.factorynetwork.lang.Project draft) {
        List<SlotProbe> probes = new ArrayList<>();
        if (items == null) {
            return probes;
        }
        List<ItemStack> candidates = new ArrayList<>();
        for (String name : dev.devpanda.factorynetwork.lang.ItemCandidates.of(draft)) {
            ResourceLocation id = ResourceLocation.tryParse(
                    name.contains(":") ? name : name.replace('/', ':'));
            // „mekanism/steel_dust" meint mekanism:steel_dust — im Programm
            // steht der Schrägstrich, in der Registry der Doppelpunkt.
            if (id == null) {
                continue;
            }
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item != net.minecraft.world.item.Items.AIR) {
                candidates.add(new ItemStack(item));
            }
        }

        for (int slot = 0; slot < items.getSlots() && slot < MAX_SLOTS; slot++) {
            // Nimmt es überhaupt etwas? Geprüft mit dem, was drinliegt —
            // sonst mit einem Kandidaten, sonst gar nicht.
            ItemStack inside = items.getStackInSlot(slot);
            boolean takes = false;
            if (!inside.isEmpty()) {
                ItemStack one = inside.copy();
                one.setCount(1);
                takes = items.insertItem(slot, one, true).isEmpty();
            }
            boolean gives = !items.extractItem(slot, 1, true).isEmpty();

            List<String> accepts = new ArrayList<>();
            for (ItemStack candidate : candidates) {
                if (items.insertItem(slot, candidate.copy(), true).isEmpty()) {
                    accepts.add(candidate.getItem().getDescriptionId());
                    takes = true;
                }
            }
            probes.add(new SlotProbe(slot, takes, gives, accepts));
        }
        return probes;
    }

    public static void handle(DeviceSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeviceState.accept(packet));
    }
}
