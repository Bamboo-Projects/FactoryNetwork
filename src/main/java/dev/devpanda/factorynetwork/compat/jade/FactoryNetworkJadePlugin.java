package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Integration with Jade.
 *
 * <p>Jade shows what you are currently looking at — for us above all how many
 * channels a cable strand carries. The question "how many are in use" is
 * otherwise answered only by trial and error, and in-game the value sits
 * exactly where you are looking.
 *
 * <p>The class is loaded only when Jade is present: Jade itself scans for
 * {@link WailaPlugin}, and without Jade no one finds it. So no check is needed
 * — only the discipline not to call it from anywhere else.
 */
@WailaPlugin
public class FactoryNetworkJadePlugin implements IWailaPlugin {

    public static final Identifier PRESS = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "press");
    public static final Identifier DRIVE = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "drive");
    public static final Identifier CABLE = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "cable");
    public static final Identifier ROUTER = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "router");
    public static final Identifier RACK = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "server_rack");
    public static final Identifier BURNER = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "burner");
    public static final Identifier CONNECTOR = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "connector");
    public static final Identifier CONTROLLER = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "controller");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // The cable has no block entity. If the connector's stood here, the
        // server part never ran, and the tooltip stayed empty.
        registration.registerBlockDataProvider(new ServerData(CableInfo.INSTANCE.getUid(), CableInfo.INSTANCE::appendServerData), CableBlock.class);
        registration.registerBlockDataProvider(new ServerData(RouterInfo.INSTANCE.getUid(), RouterInfo.INSTANCE::appendServerData),
                dev.devpanda.factorynetwork.block.entity.RouterBlockEntity.class);
        registration.registerBlockDataProvider(new ServerData(ControllerInfo.INSTANCE.getUid(), ControllerInfo.INSTANCE::appendServerData),
                ControllerBlockEntity.class);
        registration.registerBlockDataProvider(new ServerData(PressInfo.INSTANCE.getUid(), PressInfo.INSTANCE::appendServerData),
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.class);
        registration.registerBlockDataProvider(new ServerData(DriveInfo.INSTANCE.getUid(), DriveInfo.INSTANCE::appendServerData),
                dev.devpanda.factorynetwork.block.entity.DriveBlockEntity.class);
        // On the block, not on the block entity: the rack is two high, and at
        // eye level you see the upper half — which has none.
        registration.registerBlockDataProvider(new ServerData(RackInfo.INSTANCE.getUid(), RackInfo.INSTANCE::appendServerData),
                dev.devpanda.factorynetwork.block.RackBlock.class);
        registration.registerBlockDataProvider(new ServerData(BurnerInfo.INSTANCE.getUid(), BurnerInfo.INSTANCE::appendServerData),
                dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.class);
        // <b>These two lines were missing ever since ConnectorInfo existed.</b>
        // The class was registered only as a client component; its server part
        // never ran, KEY_STATE never made it into the bag, and the tooltip
        // broke off silently. Jade said not a word about a single connector —
        // not even on the dedicated connector block.
        //
        // The same trap as with the cable a handful of lines above. Anyone
        // adding a provider here must register <b>both</b> sides: register for
        // the data, registerClient for the lines.
        registration.registerBlockDataProvider(new ServerData(ConnectorInfo.INSTANCE.getUid(), ConnectorInfo.INSTANCE::appendServerData), CableBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CableInfo.INSTANCE, CableBlock.class);
        registration.registerBlockComponent(RouterInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.RouterBlock.class);
        registration.registerBlockComponent(PressInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.PressBlock.class);
        registration.registerBlockComponent(DriveInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.DriveBlock.class);
        registration.registerBlockComponent(RackInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.RackBlock.class);
        registration.registerBlockComponent(BurnerInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.BurnerBlock.class);
        registration.registerBlockComponent(ConnectorInfo.INSTANCE, CableBlock.class);
        registration.registerBlockComponent(ControllerInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.ControllerBlock.class);
    }

    /**
     * The server half of a provider.
     *
     * <p>Since Minecraft 1.21.6 Jade wants the data provider and the tooltip
     * provider as separate objects. The info classes keep both halves side by
     * side; this wraps the server half.
     */
    private record ServerData(net.minecraft.resources.Identifier uid,
                              java.util.function.BiConsumer<net.minecraft.nbt.CompoundTag,
                                      snownee.jade.api.BlockAccessor> append)
            implements snownee.jade.api.IServerDataProvider<snownee.jade.api.BlockAccessor> {

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data,
                                     snownee.jade.api.BlockAccessor accessor) {
            append.accept(data, accessor);
        }

        @Override
        public net.minecraft.resources.Identifier getUid() {
            return uid;
        }
    }
}
