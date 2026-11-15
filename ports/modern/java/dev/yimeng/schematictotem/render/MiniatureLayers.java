package dev.yimeng.schematictotem.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * The vanilla block-atlas item layers (entity_cutout and item_entity_translucent_cull, which Iris and Sodium already
 * handle) but sampling the atlas with its mip levels: a whole build squeezed into one item puts many texels on each
 * pixel, and without mipmaps it sparkles whenever the hand moves.
 */
@SuppressWarnings("deprecation") // BLOCK_ATLAS_TEXTURE: the same id vanilla's TexturedRenderLayers binds
public final class MiniatureLayers {
    public static final RenderType CUTOUT = RenderType.create("schematictotem_cutout",
            RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true))
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup());

    public static final RenderType TRANSLUCENT = RenderType.create("schematictotem_translucent",
            RenderSetup.builder(RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true))
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup());

    private MiniatureLayers() {
    }
}
