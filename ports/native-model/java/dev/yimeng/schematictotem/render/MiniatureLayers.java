package dev.yimeng.schematictotem.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

public final class MiniatureLayers {
    public static final RenderType CUTOUT = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    public static final RenderType TRANSLUCENT = RenderTypes.itemTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private MiniatureLayers() { }
}
