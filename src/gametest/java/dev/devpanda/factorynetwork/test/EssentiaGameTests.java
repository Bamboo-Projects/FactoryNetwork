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
}
