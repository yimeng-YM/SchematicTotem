package dev.yimeng.schematictotem.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;

/** Item contexts must be marked while states are extracted, before the render thread submits geometry. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class HeldItemRendererMixin {
    @WrapMethod(method = "extractRenderState")
    private void schematictotem$hands(LocalPlayer player, float tick, FirstPersonHandsAndItemsRenderState state, Operation<Void> original) {
        TotemModels.handPassDepth++;
        try { original.call(player, tick, state); }
        finally { TotemModels.handPassDepth--; }
    }
}
