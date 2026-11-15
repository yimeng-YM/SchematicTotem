package dev.yimeng.schematictotem.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Picture-in-picture preview for the config screen: draws an item render state (prepared with a chosen display
 * context, so it shows exactly what the game shows there) into its own texture, turned by the user's mouse.
 */
public final class MiniaturePreviewRenderer extends SpecialGuiElementRenderer<MiniaturePreviewRenderer.State> {
    public record State(ItemRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale,
                        @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds) implements SpecialGuiElementRenderState {
        public static State of(ItemRenderState item, Quaternionf view, int x1, int y1, int x2, int y2, float scale) {
            return new State(item, view, x1, y1, x2, y2, scale, null, SpecialGuiElementRenderState.createBounds(x1, y1, x2, y2, null));
        }
    }

    public MiniaturePreviewRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
        super(vertexConsumers);
    }

    @Override
    public Class<State> getElementClass() {
        return State.class;
    }

    @Override
    protected void render(State state, MatrixStack matrices) {
        matrices.scale(1.0F, -1.0F, -1.0F); // same space as an item drawn in a slot
        matrices.multiply(state.view());
        MinecraftClient client = MinecraftClient.getInstance();
        // lit the way the inventory lights this render state (as vanilla's oversized item renderer does)
        client.gameRenderer.getDiffuseLighting().setShaderLights(state.item().isSideLit() ? DiffuseLighting.Type.ITEMS_3D : DiffuseLighting.Type.ITEMS_FLAT);
        RenderDispatcher dispatcher = client.gameRenderer.getEntityRenderDispatcher();
        OrderedRenderCommandQueueImpl queue = dispatcher.getQueue();
        state.item().render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0);
        dispatcher.render();
    }

    @Override
    protected float getYOffset(int height, int windowScaleFactor) {
        return height / 2.0F;
    }

    @Override
    protected String getName() {
        return "schematictotem_preview";
    }
}
