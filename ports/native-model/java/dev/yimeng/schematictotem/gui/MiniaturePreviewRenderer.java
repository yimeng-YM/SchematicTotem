package dev.yimeng.schematictotem.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class MiniaturePreviewRenderer extends PictureInPictureRenderer<MiniaturePreviewRenderer.State> {
    public record State(ItemStackRenderState item, Quaternionf view, int x0, int y0, int x1, int y1, float scale,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        public static State of(ItemStackRenderState item, Quaternionf view, int x0, int y0, int x1, int y1, float scale) {
            return new State(item, view, x0, y0, x1, y1, scale, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
        }
    }
    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected void renderToTexture(State state, PoseStack matrices, SubmitNodeCollector queue) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(state.item().usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        matrices.scale(1, -1, -1);
        matrices.mulPose(state.view());
        state.item().submit(matrices, queue, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }
    @Override protected float getTranslateY(int height, int scale) { return height / 2.0F; }
    @Override protected String getTextureLabel() { return "schematictotem_preview"; }
}
