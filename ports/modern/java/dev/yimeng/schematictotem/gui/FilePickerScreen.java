package dev.yimeng.schematictotem.gui;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Lists the schematic files in the instance (schematics/, WorldEdit, structures) one page at a time. */
final class FilePickerScreen extends Screen {
    private static int page;

    private final Screen parent;
    private List<String> files = List.of();

    FilePickerScreen(Screen parent) {
        super(Component.translatable("schematictotem.screen.files"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        files = TotemModels.findFiles();
        int w = Math.min(360, width - 40), x = (width - w) / 2;
        int rows = Math.max(3, (height - 96) / 22);
        int pages = Math.max(1, (files.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        String current = TotemModels.INSTANCE.config().file;
        int y = 32;
        for (int i = page * rows; i < Math.min(files.size(), (page + 1) * rows); i++) {
            String file = files.get(i);
            String shown = (file.equals(current) ? "▶ " : "") + font.plainSubstrByWidth(file, w - 16);
            addRenderableWidget(Button.builder(Component.literal(shown), b -> {
                TotemModels.INSTANCE.use(TotemModels.resolve(file));
                minecraft.setScreen(parent);
            }).bounds(x, y, w, 20).build());
            y += 22;
        }
        int bottom = height - 28, bw = (w - 6) / 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(x, bottom, bw, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(x + bw + 2, bottom, bw, 20).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.translatable("schematictotem.screen.open_folder"), b -> {
            Path dir = TotemModels.gameDir().resolve("schematics");
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {
            }
            Util.getPlatform().openPath(dir);
        }).bounds(x + 2 * (bw + 2), bottom, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(x + 3 * (bw + 2), bottom, w - 3 * (bw + 2), 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFD866);
        Component hint = files.isEmpty() ? Component.translatable("schematictotem.list.empty") : Component.translatable("schematictotem.screen.drop_hint");
        context.drawCenteredString(font, hint, width / 2, height - 42, 0xFFB0B0B0);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
