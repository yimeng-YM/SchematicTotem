package dev.yimeng.schematictotem.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** Off-screen GUI preview for the GUI-state versions preceding render command queues. */
public final class MiniaturePreviewRenderer extends SpecialGuiElementRenderer<MiniaturePreviewRenderer.State> {
    public record State(ItemRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale,
                        @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) implements SpecialGuiElementRenderState {
        public static State of(ItemRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale) {
            return new State(item, view, x1, y1, x2, y2, scale, null, SpecialGuiElementRenderState.createBounds(x1, y1, x2, y2, null));
        }
    }
    public MiniaturePreviewRenderer(VertexConsumerProvider.Immediate consumers) { super(consumers); }
    @Override public Class<State> getElementClass() { return State.class; }
    @Override protected void render(State state, MatrixStack matrices) {
        matrices.scale(1, -1, -1);
        matrices.multiply(state.view());
        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(
                state.item().isSideLit() ? DiffuseLighting.Type.ITEMS_3D : DiffuseLighting.Type.ITEMS_FLAT);
        state.item().render(matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
    }
    @Override protected float getYOffset(int height, int scale) { return height / 2.0F; }
    @Override protected String getName() { return "schematictotem_preview"; }
}
