package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * What is currently in a device.
 *
 * <p>Carries the structure along with it: this handles the "machine was
 * swapped out while the terminal is open" case on the side, without building a
 * dedicated mechanism for it — and a device whose chunk was not loaded when
 * opened gets a profile at all only this way.
 *
 * <p><b>Capped at 64 slots</b>, and if it was truncated, that is noted along
 * with it. A barrel rack with two hundred slots should not blow up the tooltip
 * — but should not silently lie either.
 */
public record DeviceSnapshotPacket(String connector, DeviceProfileCodec.Flat profile,
                                   List<ItemStack> slots, int slotsOmitted,
                                   Levels levels, List<SlotProbe> probes)
        implements CustomPacketPayload {

    /** No tooltip has room for more. */
    public static final int MAX_SLOTS = 64;

    /**
     * The machine's levels: energy and fluids.
     *
     * <p>As its own set and not as more fields above:
     * {@code StreamCodec.composite} carries at most six, and the probe needs
     * one of them.
     *
     * <p><b>The tanks as finished lines</b> and not as fluid identifiers with
     * amounts: a fluid carries its name in the registry, its amount in
     * millibuckets and its capacity per tank — sending that over the wire only
     * to reassemble it on the other side would be three times as much code for
     * the same line. It is still translated correctly: {@code fluidName} yields
     * the fluid's display name in the server's language — the one place where
     * that shows here.
     */
    public record Levels(int energy, int energyCapacity, List<String> tanks) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Levels> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Levels::energy,
                        ByteBufCodecs.VAR_INT, Levels::energyCapacity,
                        ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(32)),
                        Levels::tanks,
                        Levels::new);
    }

    /**
     * What a slot can do, and what it would put up with.
     *
     * <p><b>{@code takes} and {@code gives} are facts</b>, determined from a
     * simulated insertion attempt and a simulated extraction. {@code accepts},
     * by contrast, is a <b>sample</b>: the items from the draft that this slot
     * would accept. It is never complete, and it must never look as if it were.
     *
     * @param slot    the slot's running number
     * @param takes   whether it takes anything at all
     * @param gives   whether something can be taken out
     * @param accepts which of the probed items fit in
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
                    Levels.STREAM_CODEC, DeviceSnapshotPacket::levels,
                    SlotProbe.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)),
                    DeviceSnapshotPacket::probes,
                    DeviceSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Reads the device out, or returns {@code null} if there is none. */
    public static DeviceSnapshotPacket of(ControllerBlockEntity controller, String connector) {
        if (controller.getLevel() == null) {
            return null;
        }
        var where = controller.graph().connectors().get(connector);
        if (where == null || where.side() == null) {
            return null;
        }
        var entity = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                controller.getLevel(), where.pos(), where.side());
        if (entity == null) {
            return null;
        }

        List<ItemStack> stacks = new ArrayList<>();
        int omitted = 0;
        // <b>The undivided inventory, not the side.</b> The numbers here are
        // the ones someone writes in slots(3) — and a side exposes its slots
        // under different numbers. What the tooltip shows must mean the same
        // slot as the program does, otherwise the readout points somewhere
        // other than where it actually reaches.
        IItemHandler items = entity.machineInventoryAll();
        if (items == null) {
            items = entity.machineInventory();
        }
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
                stacks, omitted,
                new Levels(energy, capacity, tanksOf(entity, controller.draft())),
                probe(items, controller.draft()));
    }

    /**
     * The machine's tanks as readable lines.
     *
     * <p>Empty tanks are dropped — except in the case where all are empty:
     * then "leer" says more than nothing at all, because it answers the
     * question whether there is one there in the first place.
     */
    private static List<String> tanksOf(
            dev.devpanda.factorynetwork.block.entity.ConnectorPart entity,
                                       dev.devpanda.factorynetwork.lang.Project draft) {
        IFluidHandler tanks = entity.machineTank();
        List<String> lines = new ArrayList<>();
        if (tanks == null) {
            return lines;
        }
        for (int tank = 0; tank < tanks.getTanks() && lines.size() < 32; tank++) {
            FluidStack inside = tanks.getFluidInTank(tank);
            int capacity = tanks.getTankCapacity(tank);
            if (inside.isEmpty()) {
                continue;
            }
            lines.add(inside.getHoverName().getString() + ": "
                    + inside.getAmount() + " / " + capacity + " mB");
        }
        if (lines.isEmpty() && tanks.getTanks() > 0) {
            lines.add("leer");
        }
        lines.addAll(tankProbe(tanks, draft));
        return lines;
    }

    /**
     * What the tanks would accept.
     *
     * <p>The same question as for the slots, and for the same reason: an
     * {@code IFluidHandler} cannot say what it accepts. So the question is
     * turned around — with the fluids that appear in the draft. Whoever types
     * {@code fluid:water} is asking something about water.
     *
     * <p><b>One bucket per probe.</b> With a single millibucket a tank often
     * says yes when in truth it only takes full buckets; with a bucket it
     * answers the question you actually have.
     *
     * <p>All simulated: {@code fill} with {@code SIMULATE} moves nothing.
     */
    private static List<String> tankProbe(IFluidHandler tanks,
                                          dev.devpanda.factorynetwork.lang.Project draft) {
        List<String> lines = new ArrayList<>();
        List<net.minecraft.world.level.material.Fluid> candidates = new ArrayList<>();
        for (String name : dev.devpanda.factorynetwork.lang.ItemCandidates.fluidsOf(draft)) {
            ResourceLocation id = ResourceLocation.tryParse(
                    name.contains(":") ? name : name.replace('/', ':'));
            if (id == null) {
                continue;
            }
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                candidates.add(fluid);
            }
        }
        for (int tank = 0; tank < tanks.getTanks() && lines.size() < 8; tank++) {
            List<String> accepts = new ArrayList<>();
            for (var fluid : candidates) {
                FluidStack probe = new FluidStack(fluid, 1000);
                if (tanks.fill(probe, IFluidHandler.FluidAction.SIMULATE) > 0) {
                    accepts.add(probe.getHoverName().getString());
                }
            }
            if (!accepts.isEmpty()) {
                lines.add("Behälter " + tank + " nimmt: " + String.join(", ", accepts));
            }
        }
        return lines;
    }

    /**
     * What each slot can do, and what it would put up with.
     *
     * <p><b>Why probing happens at all:</b> {@code IItemHandler} cannot say
     * what it accepts — there is no API for it, and {@code isItemValid} only
     * answers the question about one concrete item. Modded machines often
     * answer that laxly: a furnace input accepts everything and only checks
     * the recipe when it processes.
     *
     * <p>So the question is turned around — with the items that appear in the
     * draft. Whoever types {@code item:iron_ore} is asking something about
     * iron ore.
     *
     * <p>Everything here is <b>simulated</b>: {@code insertItem} and
     * {@code extractItem} with {@code simulate = true} move nothing.
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
            // "mekanism/steel_dust" means mekanism:steel_dust — in the program
            // it is a slash, in the registry a colon.
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
            // Does it take anything at all? Checked with what is inside —
            // otherwise with a candidate, otherwise not at all.
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
