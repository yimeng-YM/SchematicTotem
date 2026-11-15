package dev.yimeng.schematictotem.compat;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;

public record CompatFluidRenderHandler(FluidModel model) {
    public TextureAtlasSprite[] getFluidSprites(BlockAndTintGetter view, BlockPos pos, FluidState state) {
        return new TextureAtlasSprite[]{model.stillMaterial().sprite(), model.flowingMaterial().sprite()};
    }
    public int getFluidColor(BlockAndTintGetter view, BlockPos pos, FluidState state) {
        if (model.tintSource() == null) return -1;
        return view == null ? model.tintSource().color(state.createLegacyBlock())
                : model.tintSource().colorInWorld(state.createLegacyBlock(), view, pos);
    }
}
