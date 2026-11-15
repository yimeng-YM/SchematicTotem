package dev.yimeng.schematictotem.test.mixin;

import dev.yimeng.schematictotem.test.FrameTimer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ItemCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: time spent writing item vertices (all items). */
@Mixin(ItemCommandRenderer.class)
public abstract class EmitTimerMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void schematictotemtest$start(BatchingRenderCommandQueue queue, VertexConsumerProvider.Immediate vertexConsumers,
                                          OutlineVertexConsumerProvider outlineVertexConsumers, CallbackInfo ci) {
        FrameTimer.emitStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void schematictotemtest$end(BatchingRenderCommandQueue queue, VertexConsumerProvider.Immediate vertexConsumers,
                                        OutlineVertexConsumerProvider outlineVertexConsumers, CallbackInfo ci) {
        FrameTimer.emitEnd();
    }
}
