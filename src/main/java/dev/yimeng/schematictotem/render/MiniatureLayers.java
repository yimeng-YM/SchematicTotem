package dev.yimeng.schematictotem.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * The vanilla block-atlas item layers (entity_cutout and item_entity_translucent_cull, which Iris and Sodium already
 * handle) but sampling the atlas with its mip levels: a whole build squeezed into one item puts many texels on each
 * pixel, and without mipmaps it sparkles whenever the hand moves.
 */
@SuppressWarnings("deprecation") // BLOCK_ATLAS_TEXTURE: the same id vanilla's TexturedRenderLayers binds
public final class MiniatureLayers {
    public static final RenderLayer CUTOUT = RenderLayer.of("schematictotem_cutout",
            RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
                    .texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, () -> RenderSystem.getSamplerCache().get(FilterMode.NEAREST, true))
                    .useLightmap()
                    .useOverlay()
                    .crumbling()
                    .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                    .build());

    public static final RenderLayer TRANSLUCENT = RenderLayer.of("schematictotem_translucent",
            RenderSetup.builder(RenderPipelines.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL)
                    .texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, () -> RenderSystem.getSamplerCache().get(FilterMode.NEAREST, true))
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .crumbling()
                    .translucent()
                    .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                    .build());

    private MiniatureLayers() {
    }
}
