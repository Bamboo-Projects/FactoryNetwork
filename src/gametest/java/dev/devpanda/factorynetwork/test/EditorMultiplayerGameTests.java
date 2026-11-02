package dev.devpanda.factorynetwork.test;

import static dev.devpanda.factorynetwork.test.FactoryNetworkGameTests.blockEntity;

import com.mojang.authlib.GameProfile;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.FnConfig;
import dev.devpanda.factorynetwork.FnProtection;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.network.Power;
import dev.devpanda.factorynetwork.network.packet.DeployProgramPacket;
import dev.devpanda.factorynetwork.network.packet.SaveDraftPacket;
import dev.devpanda.factorynetwork.registry.FnBlocks;
import dev.devpanda.factorynetwork.registry.FnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

import java.util.Map;
import java.util.UUID;

/**
 * Two independent server identities exercise the real save/apply handlers.
 *
 * <p>These are server integration tests, not two logged-in Minecraft clients:
 * FakePlayer supplies a no-op connection. They prove authoritative project,
 * permission and lock behaviour, not delivery to Chromium or client latency.
 * The codec and client state tests cover their respective boundaries separately.
 */
public final class EditorMultiplayerGameTests {

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void packetSavesRespectForeignLocksAndClosingReleasesThem(GameTestHelper helper) {
        withProtection(helper, FnProtection.Mode.OFF, () -> {
            try (Session session = setup(helper)) {
                Project initial = new Project(Map.of(
                        "main.mf", "fn first() { }", "other.mf", "fn second() { }"));
                deploy(session.first(), session.terminal(), initial);
                helper.assertValueEqual(session.controller().project(), initial, "initial packet deployment");

                Project annasDraft = initial.with("main.mf", "fn anna() { }");
                save(session.first(), session.controller(), annasDraft, 101);
                Project bertsStaleDraft = initial.with("other.mf", "fn bert() { }");
                save(session.second(), session.controller(), bertsStaleDraft, 201);
                Project merged = annasDraft.with("other.mf", "fn bert() { }");
                helper.assertValueEqual(session.controller().draft(), merged,
                        "Bert's stale snapshot must preserve Anna's locked file and save his own");
                helper.assertValueEqual(session.controller().project(), initial,
                        "saving either draft must not deploy it");
                helper.assertValueEqual(session.controller().holderOf("main.mf"),
                        session.first().getUUID(), "Anna owns main.mf");
                helper.assertValueEqual(session.controller().holderOf("other.mf"),
                        session.second().getUUID(), "Bert owns other.mf");

                deploy(session.second(), session.terminal(), bertsStaleDraft);
                helper.assertValueEqual(session.controller().project(), initial,
                        "the packet handler must not bypass Anna's lock when applying");
                helper.assertValueEqual(session.controller().draft(), merged,
                        "rejected application must not replace the merged draft");
                helper.assertTrue(session.controller().diagnostics().stream()
                                .anyMatch(d -> d.message().contains("Anna")),
                        "the lock rejection must identify Anna");

                close(session.first());
                helper.assertTrue(session.controller().holderOf("main.mf") == null,
                        "closing Anna's real menu must release her lock immediately");
                Project afterClose = merged.with("main.mf", "fn released() { }");
                save(session.second(), session.controller(), afterClose, 202);
                deploy(session.second(), session.terminal(), afterClose);
                helper.assertValueEqual(session.controller().project(), afterClose,
                        "Bert can save and apply the released file without a lock timeout");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ownerProtectionRejectsBothPacketsFromTheSecondPlayer(GameTestHelper helper) {
        withProtection(helper, FnProtection.Mode.OWNER, () -> {
            try (Session session = setup(helper)) {
                session.controller().setOwner(session.first().getUUID());
                helper.assertFalse(session.second().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER),
                        "the unauthorized test identity must not be an operator");
                Project running = Project.of("fn owned() { }");
                deploy(session.first(), session.terminal(), running);
                helper.assertValueEqual(session.controller().project(), running,
                        "the authorized owner can deploy through the same handler");

                Project unauthorized = running.with("intruder.mf", "fn intruder() { }");
                save(session.second(), session.controller(), unauthorized, 301);
                helper.assertValueEqual(session.controller().draft(), running,
                        "a denied save must leave the draft untouched");
                helper.assertTrue(session.controller().holderOf("intruder.mf") == null,
                        "a denied save must not acquire a file lock");
                deploy(session.second(), session.terminal(), unauthorized);
                helper.assertValueEqual(session.controller().project(), running,
                        "a denied apply must leave the running project untouched");
                helper.assertValueEqual(session.controller().draft(), running,
                        "a denied apply must not install even a draft");

                Project authorized = running.with("owner.mf", "fn permitted() { }");
                save(session.first(), session.controller(), authorized, 302);
                deploy(session.first(), session.terminal(), authorized);
                helper.assertValueEqual(session.controller().project(), authorized,
                        "owner access still works after the rejected requests");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void failedPacketApplyKeepsSavedDraftAcrossReload(GameTestHelper helper) {
        withProtection(helper, FnProtection.Mode.OFF, () -> {
            try (Session session = setup(helper)) {
                Project running = Project.of("fn running() { }");
                deploy(session.first(), session.terminal(), running);
                helper.assertValueEqual(session.controller().project(), running, "valid initial program");
                Project unfinished = running.with("main.mf", "fn unfinished() { let a =");
                save(session.first(), session.controller(), unfinished, 401);
                helper.assertValueEqual(session.controller().draft(), unfinished,
                        "save accepts unfinished source through the packet handler");
                deploy(session.first(), session.terminal(), unfinished);
                helper.assertValueEqual(session.controller().project(), running,
                        "failed application keeps the running source");
                helper.assertValueEqual(session.controller().draft(), unfinished,
                        "failed application keeps the editable saved text");
                helper.assertFalse(session.controller().diagnostics().isEmpty(),
                        "the invalid source must produce diagnostics");

                var registries = helper.getLevel().registryAccess();
                var saved = session.controller().saveWithoutMetadata(registries);
                var restored = new ControllerBlockEntity(session.controller().getBlockPos(),
                        FnBlocks.CONTROLLER.get().defaultBlockState());
                restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
                helper.assertValueEqual(restored.project(), running, "running project after NBT reload");
                helper.assertValueEqual(restored.draft(), unfinished, "unfinished draft after NBT reload");
            }
        });
        helper.succeed();
    }

    private static Session setup(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        BlockPos rackPos = position.west();
        BlockPos terminalPos = position.east();
        helper.setBlock(position, FnBlocks.CONTROLLER.get());
        helper.setBlock(rackPos, FnBlocks.RACK.get());
        helper.setBlock(rackPos.above(), FnBlocks.RACK.get().defaultBlockState()
                .setValue(RackBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(terminalPos, FnBlocks.TERMINAL.get());
        var rack = (RackBlockEntity) blockEntity(helper, rackPos);
        rack.setItem(RackBlockEntity.chassisSlot(0), new ItemStack(FnItems.SERVER_CHASSIS.get()));
        for (ServerPart part : ServerPart.values()) {
            rack.setItem(RackBlockEntity.slotOf(0, part),
                    new ItemStack(FnItems.SERVER_PARTS.get(part).getFirst().get()));
        }
        var controller = (ControllerBlockEntity) blockEntity(helper, position);
        controller.rebuildNetwork();
        controller.power().fill(Power.CAPACITY);
        controller.power().setDraw(controller.powerDraw());
        for (int i = 0; i <= Power.BOOT_TICKS; i++) {
            controller.power().tick();
        }
        FakePlayer first = player(helper, "Anna", terminalPos);
        FakePlayer second = player(helper, "Bert", terminalPos);
        helper.assertFalse(first.getUUID().equals(second.getUUID()), "independent player identities");
        return new Session(controller, helper.absolutePos(terminalPos), first, second);
    }

    private static FakePlayer player(GameTestHelper helper, String name, BlockPos terminal) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos absolute = helper.absolutePos(terminal);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        player.containerMenu = new TerminalMenu(1, player.getInventory(), absolute);
        return player;
    }

    private static void save(ServerPlayer player, ControllerBlockEntity controller, Project project, long sequence) {
        SaveDraftPacket.handle(SaveDraftPacket.of(controller.getBlockPos(), project, sequence),
                new ServerPayloadContext(player.connection, SaveDraftPacket.TYPE.id()));
    }

    private static void deploy(ServerPlayer player, BlockPos terminal, Project project) {
        DeployProgramPacket.handle(DeployProgramPacket.of(terminal, project),
                new ServerPayloadContext(player.connection, DeployProgramPacket.TYPE.id()));
    }

    private static void close(ServerPlayer player) {
        if (player.containerMenu instanceof TerminalMenu menu) {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
    }

    /** Config changes and handlers run synchronously on the GameTest server thread. */
    private static void withProtection(GameTestHelper helper, FnProtection.Mode mode, Runnable test) {
        helper.assertTrue(FnConfig.SERVER_SPEC.isLoaded(), "the server protection config must be loaded");
        ModConfigSpec.EnumValue<FnProtection.Mode> setting =
                FnConfig.SERVER_SPEC.getValues().get("protection.programs");
        FnProtection.Mode previous = setting.get();
        try {
            setting.set(mode);
            test.run();
        } finally {
            setting.set(previous);
        }
    }

    private record Session(ControllerBlockEntity controller, BlockPos terminal,
                           FakePlayer first, FakePlayer second) implements AutoCloseable {
        @Override public void close() {
            EditorMultiplayerGameTests.close(first);
            EditorMultiplayerGameTests.close(second);
        }
    }

    private EditorMultiplayerGameTests() {
    }
}
