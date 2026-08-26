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
                                   Levels levels, List<SlotProbe> probes)
        implements CustomPacketPayload {

    /** Mehr passt in keinen Tooltip. */
    public static final int MAX_SLOTS = 64;

    /**
     * Die Füllstände der Maschine: Strom und Flüssigkeiten.
     *
     * <p>Als eigener Satz und nicht als weitere Felder oben:
     * {@code StreamCodec.composite} trägt höchstens sechs, und die Probe
     * braucht eines davon.
     *
     * <p><b>Die Behälter als fertige Zeilen</b> und nicht als Fluid-Kennungen
     * mit Mengen: Eine Flüssigkeit trägt ihren Namen in der Registry, ihre
     * Menge in Millibucket und ihr Fassungsvermögen je Behälter — das über
     * die Leitung zu schicken, um es drüben wieder zusammenzusetzen, wäre
     * dreimal so viel Code für dieselbe Zeile. Übersetzt wird trotzdem
     * richtig: {@code fluidName} liefert den Anzeigenamen der Flüssigkeit in
     * der Sprache des Servers — der einzige Ort, an dem das hier auffällt.
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
                    Levels.STREAM_CODEC, DeviceSnapshotPacket::levels,
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
        // <b>Das ungeteilte Inventar, nicht die Seite.</b> Die Nummern hier
        // sind die, die jemand in slots(3) schreibt — und eine Seite zeigt
        // ihre Fächer unter anderen Nummern. Was im Tooltip steht, muss
        // dasselbe Fach meinen wie das Programm, sonst zeigt die Auskunft
        // woandershin, als sie greift.
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
     * Die Behälter der Maschine als lesbare Zeilen.
     *
     * <p>Leere Behälter fallen weg — bis auf den Fall, dass alle leer sind:
     * Dann sagt „leer" mehr als gar nichts, weil es die Frage beantwortet,
     * ob überhaupt einer da ist.
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
     * Was die Behälter annehmen würden.
     *
     * <p>Dieselbe Frage wie bei den Fächern und aus demselben Grund: Ein
     * {@code IFluidHandler} kann nicht sagen, was er annimmt. Also wird die
     * Frage umgedreht — mit den Flüssigkeiten, die im Entwurf stehen. Wer
     * {@code fluid:water} tippt, fragt sich über Wasser etwas.
     *
     * <p><b>Ein Eimer je Probe.</b> Mit einem Millibucket sagt ein Tank oft
     * ja, der in Wahrheit nur volle Eimer nimmt; mit einem Eimer antwortet
     * er auf die Frage, die man wirklich hat.
     *
     * <p>Alles simuliert: {@code fill} mit {@code SIMULATE} bewegt nichts.
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
