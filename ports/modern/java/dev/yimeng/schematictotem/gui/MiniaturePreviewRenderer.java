package dev.yimeng.schematictotem.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Picture-in-picture preview for the config screen: draws an item render state (prepared with a chosen display
 * context, so it shows exactly what the game shows there) into its own texture, turned by the user's mouse.
 */
public final class MiniaturePreviewRenderer extends PictureInPictureRenderer<MiniaturePreviewRenderer.State> {
    public record State(ItemStackRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        public static State of(ItemStackRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale) {
            return new State(item, view, x1, y1, x2, y2, scale, null, PictureInPictureRenderState.getBounds(x1, y1, x2, y2, null));
        }
    }

    public MiniaturePreviewRenderer(MultiBufferSource.BufferSource vertexConsumers) {
        super(vertexConsumers);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected void render(State state, PoseStack matrices) {
        matrices.scale(1.0F, -1.0F, -1.0F); // same space as an item drawn in a slot
        matrices.mulPose(state.view());
        Minecraft client = Minecraft.getInstance();
        // lit the way the inventory lights this render state (as vanilla's oversized item renderer does)
        client.gameRenderer.getLighting().setupFor(state.item().usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        FeatureRenderDispatcher dispatcher = client.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage queue = dispatcher.getSubmitNodeStorage();
        state.item().submit(matrices, queue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        dispatcher.renderAllFeatures();
    }

    @Override
    protected float getTranslateY(int height, int windowScaleFactor) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "schematictotem_preview";
    }
}
