package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.registry.FnItems;
import dev.devpanda.factorynetwork.terminal.RemoteAccess;
import dev.devpanda.factorynetwork.terminal.WornDevices;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Wireless devices worn in Curios slots.
 *
 * <p>Two halves, because Curios cannot be driven with a mock player: it
 * syncs every slot change to a client the mock player does not have. So the
 * data half asks Curios whether players get a slot that takes the devices,
 * and the logic half checks the addressing through a stand-in source.
 */
public final class WornDeviceGameTests {

    private WornDeviceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playersGetAslotForWirelessDevices(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        var slots = top.theillusivec4.curios.api.CuriosApi.getPlayerSlots(helper.getLevel());
        helper.assertTrue(slots.containsKey("charm"),
                "Spieler haben keinen Charm-Platz: " + slots.keySet());
        for (var device : java.util.List.of(FnItems.WIRELESS_TERMINAL.get(), FnItems.LAPTOP.get())) {
            var fits = top.theillusivec4.curios.api.CuriosApi.getItemStackSlots(
                    new ItemStack(device), helper.getLevel());
            helper.assertTrue(fits.containsKey("charm"),
                    device + " passt in keinen Charm-Platz: " + fits.keySet());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void awornDeviceIsAddressedBelowZero(GameTestHelper helper) {
        ItemStack worn = new ItemStack(FnItems.WIRELESS_TERMINAL.get());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            WornDevices.install(new WornDevices.Source() {
                @Override
                public int count(Player who) {
                    return 2;
                }

                @Override
                public ItemStack get(Player who, int index) {
                    return index == 1 ? worn : ItemStack.EMPTY;
                }
            });
            helper.assertTrue(RemoteAccess.firstDevice(player) == worn,
                    "Das getragene Terminal wurde nicht gefunden");
            int slot = RemoteAccess.slotOf(player, worn);
            helper.assertValueEqual(slot, -2, "Platz des getragenen Geräts");
            helper.assertTrue(RemoteAccess.deviceAt(player, slot) == worn,
                    "Über den Platz kommt nicht dasselbe Gerät zurück");
            helper.assertTrue(RemoteAccess.deviceAt(player, RemoteAccess.NO_SLOT).isEmpty(),
                    "Kein Platz darf kein Gerät liefern");
        } finally {
            // Back to the real source, or nothing where Curios is missing.
            WornDevices.install(null);
            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                dev.devpanda.factorynetwork.compat.curios.FnCurios.register();
            }
        }
        helper.succeed();
    }
}
