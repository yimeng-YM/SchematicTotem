package dev.yimeng.schematictotem.test.mixin;

import dev.yimeng.schematictotem.TotemModels;
import dev.yimeng.schematictotem.render.MiniatureMeshRenderer;
import dev.yimeng.schematictotem.test.HandFrameProbe;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: the matrix (display transform included) the miniature is drawn with in each hand during the hand pass. */
@Mixin(value = MiniatureMeshRenderer.class, remap = false)
public abstract class DrawnScaleProbeMixin {
    @Inject(method = "render(Ldev/yimeng/schematictotem/render/MiniatureMeshRenderer$Data;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V",
            at = @At("HEAD"))
    private void schematictotemtest$probe(MiniatureMeshRenderer.Data data, ItemDisplayContext context, MatrixStack matrices,
                                          OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor, CallbackInfo ci) {
        if (TotemModels.handPassDepth <= 0 || data == null) return;
        boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (right || left) HandFrameProbe.recordDrawn(right, matrices.peek().getPositionMatrix());
    }
}
