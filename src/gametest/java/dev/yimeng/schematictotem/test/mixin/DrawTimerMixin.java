package dev.yimeng.schematictotem.test.mixin;

import dev.yimeng.schematictotem.render.MiniatureLayers;
import dev.yimeng.schematictotem.test.FrameTimer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: time spent uploading + drawing the miniature layers' buffers. */
@Mixin(RenderLayer.class)
public abstract class DrawTimerMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void schematictotemtest$start(BuiltBuffer buffer, CallbackInfo ci) {
        if ((Object) this == MiniatureLayers.CUTOUT || (Object) this == MiniatureLayers.TRANSLUCENT) FrameTimer.drawStart(buffer.getBuffer().remaining());
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void schematictotemtest$end(BuiltBuffer buffer, CallbackInfo ci) {
        if ((Object) this == MiniatureLayers.CUTOUT || (Object) this == MiniatureLayers.TRANSLUCENT) FrameTimer.drawEnd();
    }
}
