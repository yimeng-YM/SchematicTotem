package dev.yimeng.schematictotem;

import dev.yimeng.schematictotem.command.TotemCommands;
import dev.yimeng.schematictotem.config.TotemConfig;
import dev.yimeng.schematictotem.gui.MiniaturePreviewRenderer;
import dev.yimeng.schematictotem.gui.TotemConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SchematicTotemClient implements ClientModInitializer {
    public static final String MOD_ID = "schematictotem";
    public static final Logger LOGGER = LoggerFactory.getLogger("SchematicTotem");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> TotemCommands.register(dispatcher));
        SpecialGuiElementRegistry.register(context -> new MiniaturePreviewRenderer(context.vertexConsumers()));

        // Unbound by default (players have many mods); /totemmodel gui and Mod Menu open the same screen.
        KeyBinding openScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.schematictotem.config", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreen.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new TotemConfigScreen(null));
            }
        });

        // Item ids resolve once the registries are frozen; the first bake starts lazily when a totem is drawn.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> TotemModels.INSTANCE.applyConfig(TotemConfig.load(), false));
    }
}
