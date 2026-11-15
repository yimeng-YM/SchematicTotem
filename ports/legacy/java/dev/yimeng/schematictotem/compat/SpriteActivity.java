package dev.yimeng.schematictotem.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.Sprite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Report custom-drawn animated sprites to the Sodium versions used by the supported games. */
public final class SpriteActivity {
    private static final MethodHandle MARK = findHook();
    private SpriteActivity() { }
    public static void mark(Sprite[] sprites) {
        if (MARK == null) return;
        for (Sprite sprite : sprites) try { MARK.invoke(sprite); } catch (Throwable ignored) { }
    }
    private static MethodHandle findHook() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return null;
        for (String name : new String[]{"net.caffeinemc.mods.sodium.api.texture.SpriteUtil",
                "net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil", "me.jellysquid.mods.sodium.client.render.texture.SpriteUtil"}) {
            try {
                Class<?> type = Class.forName(name);
                try {
                    Object instance = type.getField("INSTANCE").get(null);
                    return MethodHandles.publicLookup().findVirtual(type, "markSpriteActive", MethodType.methodType(void.class, Sprite.class)).bindTo(instance);
                } catch (ReflectiveOperationException ignored) {
                    return MethodHandles.publicLookup().findStatic(type, "markSpriteActive", MethodType.methodType(void.class, Sprite.class));
                }
            } catch (Throwable ignored) { }
        }
        return null;
    }
}
