package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.buildSetup;
import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.controllerAt;

/**
 * Essentia from Thaumaturge: two workers move it from jar to jar through the
 * network's store. The connectors sit beside the jars, which open only at
 * the top: the access falls back to that side. Compiled and run only where
 * Thaumaturge exists (26.1.2).
 */
public final class EssentiaGameTests {

    private EssentiaGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void essentiaGoesFromJarToJar(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("thaumaturge")) {
            helper.succeed();
            return;
        }
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos source = controller.east().north().north();
        BlockPos target = controller.east().south().south();
        var jar = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("thaumaturge", "jar_normal"));
        helper.setBlock(source, jar);
        helper.setBlock(target, jar);

        var registry = helper.getLevel().registryAccess().lookupOrThrow(
                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY);
        var ignis = registry.get(net.minecraft.resources.ResourceKey.create(
                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY,
                Identifier.fromNamespaceAndPath("thaumaturge", "ignis"))).orElseThrow();
        var storage = helper.getLevel().getCapability(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                helper.absolutePos(source), Direction.UP);
        if (storage == null) {
            StringBuilder found = new StringBuilder();
            for (Direction side : Direction.values()) {
                found.append(side).append('=')
                        .append(helper.getLevel().getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE, helper.absolutePos(source), side) != null)
                        .append('/')
                        .append(helper.getLevel().getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.TRANSPORT, helper.absolutePos(source), side) != null)
                        .append(' ');
            }
            found.append("null=").append(helper.getLevel().getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE, helper.absolutePos(source), null) != null);
            helper.fail("Das Glas bietet keine Essenz an: " + found + " be=" + helper.getLevel().getBlockEntity(helper.absolutePos(source)));
        }
        int put;
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            put = storage.insert(ignis, 100, transaction);
            transaction.commit();
        }
        helper.assertValueEqual(put, 100, "Ignis ins Glas");

        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                worker hinein {
                    from quarry_output
                    to storage
                    filter essentia:ignis
                }
                worker hinaus {
                    from storage
                    to depot
                    filter essentia:ignis
                }"""), "Das Programm wurde nicht übernommen: " + entity.diagnostics());

        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    var out = helper.getLevel().getCapability(
                            com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                            helper.absolutePos(target), Direction.UP);
                    helper.assertTrue(out != null, "Das Zielglas bietet keine Essenz an");
                    int arrived = out.amount(ignis);
                    int left = storage.amount(ignis);
                    long inStore = entity.store(dev.devpanda.factorynetwork.compat.thaumaturge.Essentia.INSTANCE)
                            .count("thaumaturge:ignis");
                    helper.assertTrue(arrived > 0, "Keine Essenz im Zielglas angekommen: "
                            + entity.runtime().states());
                    helper.assertValueEqual(arrived + left + inStore, 100L, "Essenz ging verloren oder entstand");
                })
                .thenSucceed();
    }

    /** The aura at a device's place, as a measured value. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theAuraIsMeasuredAtTheDevice(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("thaumaturge")) {
            helper.succeed();
            return;
        }
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                fn vis() {
                    return quarry_output.measure("vis")
                }
                fn basis() {
                    return quarry_output.measure("aura")
                }"""), "Das Programm wurde nicht übernommen: " + entity.diagnostics());
        var vis = entity.callFunction("vis", java.util.List.of());
        var base = entity.callFunction("basis", java.util.List.of());
        helper.assertTrue(vis instanceof dev.devpanda.factorynetwork.runtime.Value.Decimal found
                        && found.value() >= 0, "Vis kam nicht als Zahl: " + vis);
        // The value Thaumaturge itself gives at the machine: the aura base
        // depends on where the test happens to run and may be zero there, so
        // the measure is compared with the mod and not with a fixed bound.
        BlockPos machine = helper.absolutePos(controller.east().north().north());
        double expected = com.leclowndu93150.thaumaturge.api.aura.AuraHelper
                .getAuraBase(helper.getLevel(), machine);
        helper.assertTrue(base instanceof dev.devpanda.factorynetwork.runtime.Value.Decimal found
                        && Math.abs(found.value() - expected) < 1e-6,
                "Die Aura-Basis muss der von Thaumaturge entsprechen (" + expected + "): " + base);
        helper.succeed();
    }

    /** Essentia in the network is kept in an essentia cell, not in the buffer. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void essentiaIsKeptInAnEssentiaCell(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("thaumaturge")) {
            helper.succeed();
            return;
        }
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        BlockPos source = controller.east().north().north();
        helper.setBlock(source, BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath("thaumaturge", "jar_normal")));
        var drive = (dev.devpanda.factorynetwork.block.entity.DriveBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(controller.above()));
        drive.setCell(1, new net.minecraft.world.item.ItemStack(dev.devpanda.factorynetwork.registry.FnItems
                .ESSENTIA_CELLS.get(dev.devpanda.factorynetwork.storage.EssentiaCellTier.K4).get()));

        var registry = helper.getLevel().registryAccess().lookupOrThrow(
                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY);
        var aqua = registry.get(net.minecraft.resources.ResourceKey.create(
                com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY,
                Identifier.fromNamespaceAndPath("thaumaturge", "aqua"))).orElseThrow();
        var storage = helper.getLevel().getCapability(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                helper.absolutePos(source), Direction.UP);
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            storage.insert(aqua, 200, transaction);
            transaction.commit();
        }
        entity.rebuildNetwork();
        helper.assertTrue(entity.deploy("""
                worker hinein {
                    from quarry_output
                    to storage
                    filter essentia:aqua
                }"""), "Das Programm wurde nicht übernommen: " + entity.diagnostics());

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    long inCells = 0;
                    for (var cell : drive.essentiaCells()) {
                        inCells += cell.count("thaumaturge:aqua");
                    }
                    helper.assertValueEqual(inCells, 200L, "Aqua in der Essenzzelle");
                    helper.assertValueEqual(storage.amount(aqua), 0, "Aqua im Glas");
                })
                .thenSucceed();
    }
}
