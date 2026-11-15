package dev.yimeng.schematictotem.test;

import com.google.gson.GsonBuilder;
import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.gui.TotemConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Loads the original fixture using real resource models and exercises the configuration preview. */
public final class CompatibilitySmoke implements ClientModInitializer {
    private int ticks;
    private int stage;
    private int shownAt;
    private int action;
    private String info;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (++ticks > 20 * 180) throw new IllegalStateException("Compatibility smoke timed out at stage " + stage);
        try {
            if (stage == 0 && ticks > 40 && client.getOverlay() == null) {
                TotemModels.INSTANCE.use(Path.of(System.getProperty("schematictotem.smoke.fixture")));
                stage = 1;
            } else if (stage == 1) {
                TotemModels.Slot slot = TotemModels.INSTANCE.defaultSlot();
                if (slot != null && !slot.loading) {
                    if (slot.miniature == null) throw new IllegalStateException("Fixture bake failed: " + slot.error);
                    info = TotemModels.describe(slot.miniature).getString();
                    client.setScreen(new TotemConfigScreen(null));
                    shownAt = ticks;
                    stage = 2;
                }
            } else if (stage == 2 && ticks - shownAt > 12) {
                switch (action++) {
                    case 0 -> { capture(client); press(client, "schematictotem.screen.tab.pose"); }
                    case 1 -> press(client, "schematictotem.screen.preview.fixed");
                    case 2 -> press(client, "schematictotem.screen.tab.general");
                    case 3 -> press(client, "schematictotem.screen.preview.pop");
                    case 4 -> capture(client);
                    case 5 -> press(client, "schematictotem.screen.tab.model");
                    case 6 -> press(client, "schematictotem.screen.preview.ground");
                    case 7 -> press(client, "schematictotem.screen.preview.gui");
                    case 8 -> press(client, "schematictotem.screen.choose");
                    case 9 -> capture(client);
                    default -> stage = 3;
                }
                shownAt = ticks;
            } else if (stage == 3 && ticks - shownAt > 40) {
                Path screenshots = Path.of("screenshots");
                boolean captured;
                try (var files = Files.list(screenshots)) { captured = files.anyMatch(p -> p.toString().endsWith(".png")); }
                if (!captured) throw new IllegalStateException("No screenshot was written");
                Files.writeString(Path.of("compatibility-smoke.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "minecraft", System.getProperty("schematictotem.smoke.version"), "status", "passed",
                        "realModels", true, "preview", info, "ticks", ticks, "guiActions", action)));
                stage = 4;
                client.scheduleStop();
            }
        } catch (Exception e) { throw new IllegalStateException("Compatibility smoke failed at stage " + stage, e); }
    }

    private static void capture(MinecraftClient client) {
        ScreenshotRecorder.saveScreenshot(Path.of(".").toFile(), client.getFramebuffer(), message -> {});
    }

    private static void press(MinecraftClient client, String translation) {
        String label = Text.translatable(translation).getString();
        for (var child : client.currentScreen.children()) {
            if (child instanceof ButtonWidget button && button.getMessage().getString().equals(label)) {
                button.onPress();
                return;
            }
        }
        throw new IllegalStateException("Missing GUI button: " + translation);
    }
}
