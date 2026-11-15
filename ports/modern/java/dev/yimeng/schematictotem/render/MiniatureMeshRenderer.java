package dev.yimeng.schematictotem.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yimeng.schematictotem.mesh.Mesh;
import dev.yimeng.schematictotem.mesh.Miniature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * Draws one detail level of a miniature as an item layer: its meshes go out as custom render commands on the
 * miniature layers (written straight from flat arrays, see {@link Mesh#emit}), its block entities through their
 * item-form renderers.
 */
public final class MiniatureMeshRenderer implements SpecialModelRenderer<MiniatureMeshRenderer.Data> {
    public static final MiniatureMeshRenderer INSTANCE = new MiniatureMeshRenderer();

    public record Data(Miniature.Level level, int[] tints, int tintVersion) {
    }

    /** Sodium only animates sprites it saw drawn; its own item path reports them, so ours does too. */
    private static final @Nullable MethodHandle MARK_SPRITE_ACTIVE = sodiumSpriteHook();

    private MiniatureMeshRenderer() {
    }

    @Override
    public void render(@Nullable Data data, ItemDisplayContext displayContext, PoseStack matrices, SubmitNodeCollector queue, int light,
                       int overlay, boolean glint, int outlineColor) {
        if (data == null) return;
        Miniature.Level level = data.level();
        submit(level.solid, MiniatureLayers.CUTOUT, data, matrices, queue, light, overlay);
        submit(level.translucent, MiniatureLayers.TRANSLUCENT, data, matrices, queue, light, overlay);
        if (!level.blockEntities.isEmpty()) {
            BlockEntityPartsRenderer.INSTANCE.render(level.blockEntities, displayContext, matrices, queue, light, overlay, glint, outlineColor);
        }
    }

    private static void submit(Mesh mesh, net.minecraft.client.renderer.rendertype.RenderType layer, Data data, PoseStack matrices, SubmitNodeCollector queue,
                               int light, int overlay) {
        if (mesh.isEmpty()) return;
        int[] tints = data.tints();
        int tintVersion = data.tintVersion();
        queue.submitCustomGeometry(matrices, layer, (entry, consumer) -> mesh.emit(entry, consumer, light, overlay, tints, tintVersion));
        if (MARK_SPRITE_ACTIVE != null) {
            for (TextureAtlasSprite sprite : mesh.animatedSprites) {
                try {
                    MARK_SPRITE_ACTIVE.invoke(sprite);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        // the item state gets the miniature's box corners directly
    }

    @Override
    public @Nullable Data extractArgument(ItemStack stack) {
        return null;
    }

    private static @Nullable MethodHandle sodiumSpriteHook() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return null;
        try {
            Class<?> util = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            Object instance = util.getField("INSTANCE").get(null);
            return MethodHandles.publicLookup().findVirtual(util, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class)).bindTo(instance);
        } catch (Throwable e) {
            return null;
        }
    }
}
