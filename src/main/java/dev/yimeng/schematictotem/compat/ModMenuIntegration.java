package dev.yimeng.schematictotem.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.yimeng.schematictotem.gui.TotemConfigScreen;

/** Mod Menu's "Config" button opens the settings screen. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TotemConfigScreen::new;
    }
}
