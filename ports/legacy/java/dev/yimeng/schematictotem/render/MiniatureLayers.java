package dev.yimeng.schematictotem.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;

/** Vanilla atlas layers preserve shader and renderer-mod compatibility. */
public final class MiniatureLayers {
    public static final RenderLayer CUTOUT = RenderLayer.getEntityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    public static final RenderLayer TRANSLUCENT = RenderLayer.getItemEntityTranslucentCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    private MiniatureLayers() { }
}
