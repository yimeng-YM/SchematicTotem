package dev.yimeng.schematictotem.test.mixin;

import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test only: the client game test resets the options to the defaults it recorded when GameOptions was created. Key
 * bindings that mods (Punchy) register later have no recorded default, so the reset passes null here; leave them
 * unbound instead of crashing.
 */
@Mixin(InputUtil.class)
public abstract class LateKeyBindingMixin {
    @Inject(method = "fromTranslationKey", at = @At("HEAD"), cancellable = true)
    private static void schematictotemtest$unboundWithoutDefault(String translationKey, CallbackInfoReturnable<InputUtil.Key> cir) {
        if (translationKey == null) cir.setReturnValue(InputUtil.UNKNOWN_KEY);
    }
}
