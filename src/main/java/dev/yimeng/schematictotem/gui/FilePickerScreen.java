package dev.yimeng.schematictotem.gui;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
        super(Text.translatable("schematictotem.screen.files"));
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
            String shown = (file.equals(current) ? "▶ " : "") + textRenderer.trimToWidth(file, w - 16);
            addDrawableChild(ButtonWidget.builder(Text.literal(shown), b -> {
                TotemModels.INSTANCE.use(TotemModels.resolve(file));
                client.setScreen(parent);
            }).dimensions(x, y, w, 20).build());
            y += 22;
        }
        int bottom = height - 28, bw = (w - 6) / 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            page--;
            clearAndInit();
        }).dimensions(x, bottom, bw, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            page++;
            clearAndInit();
        }).dimensions(x + bw + 2, bottom, bw, 20).build()).active = page < pages - 1;
        addDrawableChild(ButtonWidget.builder(Text.translatable("schematictotem.screen.open_folder"), b -> {
            Path dir = TotemModels.gameDir().resolve("schematics");
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {
            }
            Util.getOperatingSystem().open(dir);
        }).dimensions(x + 2 * (bw + 2), bottom, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(x + 3 * (bw + 2), bottom, w - 3 * (bw + 2), 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFD866);
        Text hint = files.isEmpty() ? Text.translatable("schematictotem.list.empty") : Text.translatable("schematictotem.screen.drop_hint");
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 42, 0xFFB0B0B0);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
