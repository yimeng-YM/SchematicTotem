package dev.yimeng.schematictotem.test.mixin;

import dev.yimeng.schematictotem.test.FrameTimer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: CPU time of whole frames and of the first-person hand pass (which draws the held items). */
@Mixin(GameRenderer.class)
public abstract class FrameTimerMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void schematictotemtest$frameStart(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        FrameTimer.frameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void schematictotemtest$frameEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        FrameTimer.frameEnd();
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void schematictotemtest$handStart(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        FrameTimer.handStart();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void schematictotemtest$handEnd(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        FrameTimer.handEnd();
    }
}
