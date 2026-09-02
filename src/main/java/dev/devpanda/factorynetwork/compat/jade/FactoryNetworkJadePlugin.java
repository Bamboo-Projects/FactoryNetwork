package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
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

    public static final ResourceLocation PRESS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "press");
    public static final ResourceLocation DRIVE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "drive");
    public static final ResourceLocation CABLE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "cable");
    public static final ResourceLocation ROUTER = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "router");
    public static final ResourceLocation RACK = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "server_rack");
    public static final ResourceLocation BURNER = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "burner");
    public static final ResourceLocation CONNECTOR = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "connector");
    public static final ResourceLocation CONTROLLER = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "controller");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // The cable has no block entity. If the connector's stood here, the
        // server part never ran, and the tooltip stayed empty.
        registration.registerBlockDataProvider(CableInfo.INSTANCE, CableBlock.class);
        registration.registerBlockDataProvider(RouterInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.RouterBlockEntity.class);
        registration.registerBlockDataProvider(ControllerInfo.INSTANCE,
                ControllerBlockEntity.class);
        registration.registerBlockDataProvider(PressInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.class);
        registration.registerBlockDataProvider(DriveInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.DriveBlockEntity.class);
        // On the block, not on the block entity: the rack is two high, and at
        // eye level you see the upper half — which has none.
        registration.registerBlockDataProvider(RackInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.RackBlock.class);
        registration.registerBlockDataProvider(BurnerInfo.INSTANCE,
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
        registration.registerBlockDataProvider(ConnectorInfo.INSTANCE, CableBlock.class);
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
}
