package dev.devpanda.factorynetwork.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.devpanda.factorynetwork.item.ProgramDiskItem;
import dev.devpanda.factorynetwork.network.packet.DiskSharedPacket;
import dev.devpanda.factorynetwork.network.packet.DiskWritePacket;
import dev.devpanda.factorynetwork.platform.Chat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * {@code /fnshare}: the program share from the chat.
 *
 * <ul>
 *   <li>{@code upload <title>} — the disk in hand goes up, the code comes back</li>
 *   <li>{@code get <code>} — the program behind the code onto the disk in hand</li>
 *   <li>{@code list [new|popular] [search]} — the public list, every entry clickable</li>
 *   <li>{@code report <code>} — flags a program; after a few reports it leaves the list</li>
 * </ul>
 */
public final class ShareCommands {

    private ShareCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fnshare")
                .executes(context -> {
                    if (allowed()) {
                        later(() -> Minecraft.getInstance().setScreen(new dev.devpanda.factorynetwork.client
                                .screen.ShareScreen()));
                    }
                    return 1;
                })
                .then(Commands.literal("upload")
                        .then(Commands.literal("new")
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            upload(StringArgumentType.getString(context, "title"), true, false);
                                            return 1;
                                        })))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            upload(StringArgumentType.getString(context, "title"), false, true);
                                            return 1;
                                        })))
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(context -> {
                                    upload(StringArgumentType.getString(context, "title"), false, false);
                                    return 1;
                                })))
                .then(Commands.literal("update")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            updateBound(StringArgumentType.getString(context, "code"),
                                                    StringArgumentType.getString(context, "title"));
                                            return 1;
                                        }))))
                .then(Commands.literal("get")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> {
                                    get(StringArgumentType.getString(context, "code"));
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            list(false, "", 0);
                            return 1;
                        })
                        .then(Commands.literal("new")
                                .executes(context -> {
                                    list(false, "", 0);
                                    return 1;
                                })
                                .then(Commands.argument("search", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            list(false, StringArgumentType.getString(context, "search"), 0);
                                            return 1;
                                        })))
                        .then(Commands.literal("popular")
                                .executes(context -> {
                                    list(true, "", 0);
                                    return 1;
                                })
                                .then(Commands.argument("search", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            list(true, StringArgumentType.getString(context, "search"), 0);
                                            return 1;
                                        }))))
                .then(Commands.literal("report")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> {
                                    report(StringArgumentType.getString(context, "code"));
                                    return 1;
                                }))));
    }

    /**
     * Uploads the disk in hand. A disk that was shared before updates its
     * code with the key it carries; {@code fresh} forces a new code;
     * {@code bind} ties the code to the Minecraft account, proven with Mojang.
     */
    private static void upload(String title, boolean fresh, boolean bind) {
        if (!allowed()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var disk = player.getMainHandItem();
        var program = ProgramDiskItem.read(disk);
        if (program == null) {
            say(Component.translatable("message.factorynetwork.share.need_disk"));
            return;
        }
        var share = fresh ? null : ProgramDiskItem.shareOf(disk);
        boolean needProof = bind || (share != null && share.key().isEmpty());
        say(Component.translatable("message.factorynetwork.share.uploading"));
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    if (!needProof) {
                        return null;
                    }
                    try {
                        return ShareClient.prove();
                    } catch (Exception error) {
                        throw new IllegalStateException(Component.translatable(
                                "message.factorynetwork.share.no_account").getString(), error);
                    }
                })
                .thenCompose(proof -> share == null
                        ? ShareClient.upload(title, "", program.files(), proof, bind)
                        : ShareClient.update(share.code(), title, "", program.files(), share.key(), proof, bind))
                .whenComplete((uploaded, error) -> later(() -> {
                    if (error != null) {
                        failed(error);
                        return;
                    }
                    ClientPacketDistributor.sendToServer(new DiskSharedPacket(uploaded.code(), uploaded.key()));
                    say(Component.translatable(share == null
                                    ? "message.factorynetwork.share.uploaded"
                                    : "message.factorynetwork.share.updated",
                            codeLink(uploaded.code())));
                    if (uploaded.bound()) {
                        say(Component.translatable("message.factorynetwork.share.bound"));
                    }
                }));
    }

    /** From any disk: a new version under a code bound to this account. */
    private static void updateBound(String code, String title) {
        if (!allowed()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var program = ProgramDiskItem.read(player.getMainHandItem());
        if (program == null) {
            say(Component.translatable("message.factorynetwork.share.need_disk"));
            return;
        }
        say(Component.translatable("message.factorynetwork.share.uploading"));
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return ShareClient.prove();
                    } catch (Exception error) {
                        throw new IllegalStateException(Component.translatable(
                                "message.factorynetwork.share.no_account").getString(), error);
                    }
                })
                .thenCompose(proof -> ShareClient.update(code.toUpperCase(java.util.Locale.ROOT), title, "",
                        program.files(), "", proof, false))
                .whenComplete((uploaded, error) -> later(() -> {
                    if (error != null) {
                        failed(error);
                        return;
                    }
                    ClientPacketDistributor.sendToServer(new DiskSharedPacket(uploaded.code(), ""));
                    say(Component.translatable("message.factorynetwork.share.updated",
                            codeLink(uploaded.code())));
                }));
    }

    private static Component codeLink(String code) {
        return Component.literal(code).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(code))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(
                        "message.factorynetwork.share.copy"))));
    }

    private static void get(String code) {
        if (!allowed()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof ProgramDiskItem)) {
            say(Component.translatable("message.factorynetwork.share.need_disk"));
            return;
        }
        ShareClient.fetch(code).whenComplete((shared, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            ClientPacketDistributor.sendToServer(new DiskWritePacket(shared.title(), shared.files()));
            say(Component.translatable("message.factorynetwork.share.fetched", shared.title(),
                    shared.files().size()));
        }));
    }

    private static void list(boolean popular, String search, int page) {
        if (!allowed()) {
            return;
        }
        ShareClient.list(popular, search, page).whenComplete((found, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            if (found.isEmpty()) {
                say(Component.translatable("message.factorynetwork.share.empty"));
                return;
            }
            say(Component.translatable(popular ? "message.factorynetwork.share.list_popular"
                    : "message.factorynetwork.share.list_new"));
            for (ShareClient.Listed entry : found) {
                MutableComponent line = Component.literal(" " + entry.code() + " ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand("/fnshare get " + entry.code()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(
                                        "message.factorynetwork.share.click_to_get"))));
                line.append(Component.literal(entry.title()).withStyle(ChatFormatting.WHITE));
                line.append(Component.translatable("message.factorynetwork.share.entry",
                        entry.files(), entry.lines(), entry.downloads()).withStyle(ChatFormatting.GRAY));
                say(line);
            }
        }));
    }

    private static void report(String code) {
        if (!allowed()) {
            return;
        }
        ShareClient.report(code).whenComplete((done, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            say(Component.translatable("message.factorynetwork.share.reported"));
        }));
    }

    private static void failed(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        say(Component.translatable("message.factorynetwork.share.failed",
                String.valueOf(cause.getMessage())).withStyle(ChatFormatting.RED));
    }

    /** Whether this server allows the share; says so in the chat if not. */
    private static boolean allowed() {
        if (dev.devpanda.factorynetwork.network.packet.ShareAllowedPacket.allowedHere()) {
            return true;
        }
        say(Component.translatable("message.factorynetwork.share.disabled").withStyle(ChatFormatting.RED));
        return false;
    }

    private static void later(Runnable what) {
        Minecraft.getInstance().execute(what);
    }

    private static void say(Component message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Chat.tell(player, message, false);
        }
    }
}
