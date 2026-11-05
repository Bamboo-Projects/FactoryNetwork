package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ShareClient;
import dev.devpanda.factorynetwork.item.ProgramDiskItem;
import dev.devpanda.factorynetwork.network.packet.DiskSharedPacket;
import dev.devpanda.factorynetwork.network.packet.DiskWritePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The program share as a window: browse, look, fetch — and upload with a
 * description.
 *
 * <p>Opened by right-clicking a program disk into the air, or with
 * {@code /fnshare}. Everything here goes through the same calls as the
 * chat commands; the window only adds what a chat cannot do well: a list to
 * click through and a description to write.
 */
public class ShareScreen extends Screen {

    private enum Tab { BROWSE, UPLOAD }

    private static final int WIDTH = 360;
    private static final int HEIGHT = 236;
    private static final int PAD = 8;
    private static final int ROW = 13;
    private static final int ROWS = 11;
    private static final int LIST_WIDTH = 150;

    private Tab tab = Tab.BROWSE;
    private int left;
    private int top;

    // Browse
    private EditBox search;
    private boolean popular;
    private int page;
    private List<ShareClient.Listed> entries = List.of();
    private int selected = -1;
    private ShareClient.Shared detail;
    private long typedAt;
    private boolean searchDirty;

    // Upload
    private EditBox title;
    private MultiLineEditBox description;
    private Checkbox fresh;
    private Checkbox bind;
    private String draftTitle = "";
    private String draftDescription = "";

    private Component status = Component.empty();
    private int statusColour = TerminalScreen.TEXT_DIM;
    private boolean busy;

    public ShareScreen() {
        super(Component.translatable("screen.factorynetwork.share.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.factorynetwork.share.tab.browse"),
                button -> switchTo(Tab.BROWSE)).bounds(left + PAD, top + PAD, 80, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.factorynetwork.share.tab.upload"),
                button -> switchTo(Tab.UPLOAD)).bounds(left + PAD + 84, top + PAD, 80, 16).build());
        if (tab == Tab.BROWSE) {
            initBrowse();
        } else {
            initUpload();
        }
    }

    private void switchTo(Tab next) {
        if (title != null) {
            draftTitle = title.getValue();
        }
        if (description != null) {
            draftDescription = description.getValue();
        }
        tab = next;
        status = Component.empty();
        rebuildWidgets();
    }

    // ---- Browse -------------------------------------------------------------

    private int listTop() {
        return top + PAD + 16 + 6 + 18 + 4;
    }

    private void initBrowse() {
        int y = top + PAD + 16 + 6;
        String before = search == null ? "" : search.getValue();
        search = new EditBox(font, left + PAD, y, 200, 16,
                Component.translatable("screen.factorynetwork.share.search"));
        search.setHint(Component.translatable("screen.factorynetwork.share.search"));
        search.setMaxLength(40);
        search.setValue(before);
        search.setResponder(text -> {
            searchDirty = true;
            typedAt = System.currentTimeMillis();
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable(popular
                        ? "screen.factorynetwork.share.sort.popular" : "screen.factorynetwork.share.sort.new"),
                button -> {
                    popular = !popular;
                    page = 0;
                    rebuildWidgets();
                    load();
                }).bounds(left + PAD + 204, y, 70, 16).build());
        int below = listTop() + ROWS * ROW + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                load();
            }
        }).bounds(left + PAD, below, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (entries.size() >= 20) {
                page++;
                load();
            }
        }).bounds(left + PAD + LIST_WIDTH - 20, below, 20, 16).build());
        int detailLeft = left + PAD + LIST_WIDTH + 10;
        int detailWidth = WIDTH - LIST_WIDTH - 2 * PAD - 10;
        Button fetch = Button.builder(Component.translatable("screen.factorynetwork.share.fetch"),
                button -> fetchSelected()).bounds(detailLeft, below, detailWidth - 64, 16).build();
        fetch.active = detail != null && holdsDisk();
        addRenderableWidget(fetch);
        Button report = Button.builder(Component.translatable("screen.factorynetwork.share.report"),
                button -> reportSelected()).bounds(detailLeft + detailWidth - 60, below, 60, 16).build();
        report.active = detail != null;
        addRenderableWidget(report);
        if (entries.isEmpty() && !busy) {
            load();
        }
    }

    private void load() {
        busy = true;
        say(Component.translatable("screen.factorynetwork.share.loading"), TerminalScreen.TEXT_DIM);
        String query = search == null ? "" : search.getValue();
        ShareClient.list(popular, query, page).whenComplete((found, error) -> later(() -> {
            busy = false;
            if (error != null) {
                failed(error);
                return;
            }
            entries = found;
            selected = -1;
            detail = null;
            say(found.isEmpty() ? Component.translatable("message.factorynetwork.share.empty")
                    : Component.translatable("screen.factorynetwork.share.page", page + 1), TerminalScreen.TEXT_DIM);
            rebuildWidgets();
        }));
    }

    private void select(int index) {
        selected = index;
        detail = null;
        rebuildWidgets();
        String code = entries.get(index).code();
        ShareClient.fetch(code, true).whenComplete((shared, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            if (selected >= 0 && selected < entries.size() && entries.get(selected).code().equals(shared.code())) {
                detail = shared;
                rebuildWidgets();
            }
        }));
    }

    private void fetchSelected() {
        if (detail == null || !holdsDisk()) {
            return;
        }
        String code = detail.code();
        ShareClient.fetch(code).whenComplete((shared, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            ClientPacketDistributor.sendToServer(new DiskWritePacket(shared.title(), shared.files()));
            say(Component.translatable("message.factorynetwork.share.fetched", shared.title(),
                    shared.files().size()), TerminalScreen.GOOD);
        }));
    }

    private void reportSelected() {
        if (detail == null) {
            return;
        }
        ShareClient.report(detail.code()).whenComplete((done, error) -> later(() -> {
            if (error != null) {
                failed(error);
                return;
            }
            say(Component.translatable("message.factorynetwork.share.reported"), TerminalScreen.WARN);
        }));
    }

    // ---- Upload -------------------------------------------------------------

    private void initUpload() {
        int x = left + PAD;
        int fieldWidth = WIDTH - 2 * PAD;
        int y = top + PAD + 16 + 6 + 22;
        title = new EditBox(font, x, y + 10, fieldWidth, 16,
                Component.translatable("screen.factorynetwork.share.upload.title"));
        title.setMaxLength(60);
        title.setValue(draftTitle.isEmpty() ? defaultTitle() : draftTitle);
        addRenderableWidget(title);
        description = MultiLineEditBox.builder()
                .setX(x).setY(y + 10 + 16 + 14)
                .setPlaceholder(Component.translatable("screen.factorynetwork.share.upload.description_hint"))
                .build(font, fieldWidth, 70, Component.translatable("screen.factorynetwork.share.upload.description"));
        description.setCharacterLimit(1000);
        description.setValue(draftDescription);
        addRenderableWidget(description);
        int checks = y + 10 + 16 + 14 + 70 + 6;
        var share = ProgramDiskItem.shareOf(disk());
        fresh = null;
        if (share != null) {
            fresh = Checkbox.builder(Component.translatable("screen.factorynetwork.share.upload.fresh"), font)
                    .pos(x, checks).build();
            addRenderableWidget(fresh);
        }
        bind = Checkbox.builder(Component.translatable("screen.factorynetwork.share.upload.bind"), font)
                .pos(x + (share != null ? fieldWidth / 2 : 0), checks).build();
        addRenderableWidget(bind);
        Button send = Button.builder(Component.translatable(share != null
                        ? "screen.factorynetwork.share.upload.send_update" : "screen.factorynetwork.share.upload.send"),
                button -> upload()).bounds(x, checks + 24, fieldWidth, 18).build();
        send.active = ProgramDiskItem.read(disk()) != null && !busy;
        addRenderableWidget(send);
    }

    private String defaultTitle() {
        var stack = disk();
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                ? stack.getHoverName().getString() : "";
    }

    private void upload() {
        var stack = disk();
        var program = ProgramDiskItem.read(stack);
        String name = title.getValue().trim();
        if (program == null || name.isEmpty()) {
            say(Component.translatable(program == null ? "message.factorynetwork.share.need_disk"
                    : "screen.factorynetwork.share.upload.need_title"), TerminalScreen.BAD);
            return;
        }
        String text = description.getValue();
        boolean asNew = fresh != null && fresh.selected();
        boolean tie = bind.selected();
        var share = asNew ? null : ProgramDiskItem.shareOf(stack);
        boolean needProof = tie || (share != null && share.key().isEmpty());
        busy = true;
        say(Component.translatable("message.factorynetwork.share.uploading"), TerminalScreen.TEXT_DIM);
        CompletableFuture
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
                        ? ShareClient.upload(name, text, program.files(), proof, tie)
                        : ShareClient.update(share.code(), name, text, program.files(), share.key(), proof, tie))
                .whenComplete((uploaded, error) -> later(() -> {
                    busy = false;
                    if (error != null) {
                        failed(error);
                        return;
                    }
                    ClientPacketDistributor.sendToServer(new DiskSharedPacket(uploaded.code(), uploaded.key()));
                    if (minecraft != null) {
                        minecraft.keyboardHandler.setClipboard(uploaded.code());
                    }
                    // The disk now carries the code: the button says "new version" from here on.
                    draftTitle = title.getValue();
                    draftDescription = description.getValue();
                    rebuildWidgets();
                    say(Component.translatable(share == null ? "screen.factorynetwork.share.upload.done"
                            : "screen.factorynetwork.share.upload.updated", uploaded.code()), TerminalScreen.GOOD);
                }));
    }

    // ---- Shared -------------------------------------------------------------

    private net.minecraft.world.item.ItemStack disk() {
        var player = minecraft == null ? null : minecraft.player;
        return player == null ? net.minecraft.world.item.ItemStack.EMPTY : player.getMainHandItem();
    }

    private boolean holdsDisk() {
        return disk().getItem() instanceof ProgramDiskItem;
    }

    private void say(Component message, int colour) {
        status = message;
        statusColour = colour;
    }

    private void failed(Throwable error) {
        busy = false;
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        say(Component.translatable("message.factorynetwork.share.failed", String.valueOf(cause.getMessage())),
                TerminalScreen.BAD);
    }

    private void later(Runnable what) {
        if (minecraft != null) {
            minecraft.execute(what);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // The list follows the search field once typing pauses.
        if (searchDirty && System.currentTimeMillis() - typedAt > 400) {
            searchDirty = false;
            page = 0;
            load();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.BROWSE) {
            double mx = event.x();
            double my = event.y();
            int listLeft = left + PAD;
            int listTop = listTop();
            if (mx >= listLeft && mx < listLeft + LIST_WIDTH && my >= listTop && my < listTop + ROWS * ROW) {
                int index = (int) ((my - listTop) / ROW);
                if (index < entries.size()) {
                    select(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While a field is typing, the keys are its own; otherwise "e" closes the window.
        if (getFocused() instanceof EditBox box && box.canConsumeInput() && event.key() != 256) {
            return box.keyPressed(event) || true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF080A09);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF232B27);
        if (tab == Tab.BROWSE) {
            graphics.fill(left + PAD - 1, listTop() - 1, left + PAD + LIST_WIDTH + 1,
                    listTop() + ROWS * ROW + 1, 0xFF080A09);
            graphics.fill(left + PAD + LIST_WIDTH + 9, listTop() - 1, left + WIDTH - PAD + 1,
                    listTop() + ROWS * ROW + 1, 0xFF080A09);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, getTitle(), left + PAD + 172, top + PAD + 4, TerminalScreen.TEXT, false);
        if (tab == Tab.BROWSE) {
            drawList(graphics, mouseX, mouseY);
            drawDetail(graphics);
        } else {
            drawUploadLabels(graphics);
        }
        graphics.text(font, status, left + PAD, top + HEIGHT - PAD - 9, statusColour, false);
    }

    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + PAD;
        int y = listTop();
        for (int i = 0; i < Math.min(ROWS, entries.size()); i++) {
            var entry = entries.get(i);
            int rowTop = y + i * ROW;
            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= rowTop && mouseY < rowTop + ROW;
            if (i == selected || hovered) {
                graphics.fill(x, rowTop, x + LIST_WIDTH, rowTop + ROW, i == selected ? 0xFF39443D : 0xFF2C3530);
            }
            String downloads = entry.downloads() + "×";
            graphics.text(font, font.plainSubstrByWidth(entry.title(), LIST_WIDTH - 8 - font.width(downloads)),
                    x + 3, rowTop + 3, TerminalScreen.TEXT, false);
            graphics.text(font, downloads, x + LIST_WIDTH - 3 - font.width(downloads), rowTop + 3,
                    TerminalScreen.TEXT_FAINT, false);
        }
    }

    private void drawDetail(GuiGraphicsExtractor graphics) {
        int x = left + PAD + LIST_WIDTH + 13;
        int width = WIDTH - LIST_WIDTH - 2 * PAD - 16;
        int y = listTop() + 3;
        if (selected < 0) {
            graphics.text(font, Component.translatable("screen.factorynetwork.share.pick"), x, y,
                    TerminalScreen.TEXT_FAINT, false);
            return;
        }
        var entry = entries.get(selected);
        graphics.text(font, font.plainSubstrByWidth(entry.title(), width), x, y, TerminalScreen.TEXT, false);
        y += 11;
        graphics.text(font, font.plainSubstrByWidth(Component.translatable("screen.factorynetwork.share.meta",
                entry.code(), entry.files(), entry.lines(), entry.downloads()).getString(), width),
                x, y, TerminalScreen.TEXT_DIM, false);
        y += 14;
        String text = detail != null ? detail.description() : entry.summary();
        if (text.isBlank()) {
            text = Component.translatable("screen.factorynetwork.share.no_description").getString();
        }
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(text), width));
        int bottom = listTop() + ROWS * ROW - 4;
        for (FormattedCharSequence line : lines) {
            if (y > bottom - 10 - 11) {
                break;
            }
            graphics.text(font, line, x, y, TerminalScreen.TEXT, false);
            y += 10;
        }
        if (detail != null) {
            String names = String.join(", ", detail.files().keySet());
            graphics.text(font, font.plainSubstrByWidth(names, width), x, bottom - 9,
                    TerminalScreen.TEXT_FAINT, false);
        }
    }

    private void drawUploadLabels(GuiGraphicsExtractor graphics) {
        int x = left + PAD;
        int y = top + PAD + 16 + 6;
        var stack = disk();
        var share = ProgramDiskItem.shareOf(stack);
        Component about = ProgramDiskItem.read(stack) == null
                ? Component.translatable("message.factorynetwork.share.need_disk")
                : share == null ? Component.translatable("screen.factorynetwork.share.upload.new_code")
                : Component.translatable("screen.factorynetwork.share.upload.replaces", share.code());
        graphics.text(font, about, x, y + 4, share == null ? TerminalScreen.TEXT_DIM : TerminalScreen.GOOD, false);
        graphics.text(font, Component.translatable("screen.factorynetwork.share.upload.title"), x, y + 22,
                TerminalScreen.TEXT_DIM, false);
        graphics.text(font, Component.translatable("screen.factorynetwork.share.upload.description"), x,
                y + 22 + 10 + 16 + 4, TerminalScreen.TEXT_DIM, false);
    }
}
