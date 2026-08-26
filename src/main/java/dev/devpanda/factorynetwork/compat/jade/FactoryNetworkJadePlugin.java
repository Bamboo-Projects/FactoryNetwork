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
 * Anbindung an Jade.
 *
 * <p>Jade zeigt an, was man gerade ansieht — bei uns vor allem, wie viele
 * Kanäle ein Kabelstrang trägt. Die Frage „wie viele sind belegt" beantwortet
 * sich sonst nur durch Ausprobieren, und im Spiel steht der Wert genau da, wo
 * man hinsieht.
 *
 * <p>Die Klasse wird nur geladen, wenn Jade vorhanden ist: Jade sucht selbst
 * nach {@link WailaPlugin}, und ohne Jade findet sie niemand. Deshalb braucht
 * es keine Abfrage — nur den Verzicht darauf, sie von anderswo aufzurufen.
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
        // Das Kabel hat keine BlockEntity. Stand hier die des Connectors,
        // lief der Server-Teil nie, und der Tooltip blieb leer.
        registration.registerBlockDataProvider(CableInfo.INSTANCE, CableBlock.class);
        registration.registerBlockDataProvider(RouterInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.RouterBlockEntity.class);
        registration.registerBlockDataProvider(ControllerInfo.INSTANCE,
                ControllerBlockEntity.class);
        registration.registerBlockDataProvider(PressInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.PressBlockEntity.class);
        registration.registerBlockDataProvider(DriveInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.DriveBlockEntity.class);
        // Am Block und nicht an der BlockEntity: Der Schrank ist zwei hoch,
        // und auf Augenhöhe sieht man die obere Hälfte — die hat keine.
        registration.registerBlockDataProvider(RackInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.RackBlock.class);
        registration.registerBlockDataProvider(BurnerInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity.class);
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
        registration.registerBlockComponent(ConnectorInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.ConnectorBlock.class);
        // Auch am Kabel: Dort sitzen die Anschlüsse seit dem Kabelbus, und
        // ohne diese Zeile sagte Jade über sie kein Wort.
        registration.registerBlockComponent(ConnectorInfo.INSTANCE, CableBlock.class);
        registration.registerBlockComponent(ControllerInfo.INSTANCE,
                dev.devpanda.factorynetwork.block.ControllerBlock.class);
    }
}
