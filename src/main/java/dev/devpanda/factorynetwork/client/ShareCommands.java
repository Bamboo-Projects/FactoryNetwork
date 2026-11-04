package dev.devpanda.factorynetwork.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.devpanda.factorynetwork.item.ProgramDiskItem;
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
                .then(Commands.literal("upload")
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(context -> {
                                    upload(StringArgumentType.getString(context, "title"));
                                    return 1;
                                })))
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

    private static void upload(String title) {
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
        ShareClient.upload(title, program.files()).whenComplete((code, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            say(Component.translatable("message.factorynetwork.share.uploaded",
                    Component.literal(code).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(code))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable(
                                    "message.factorynetwork.share.copy"))))));
        }));
    }

    private static void get(String code) {
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
