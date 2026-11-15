package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every item render (GUI, hands, other entities, item frames, dropped items, the totem pop animation, bundles)
 * resolves its model through ItemModelManager.update, so replacing the model here covers all of them - and works
 * whatever item_model a server or resource pack gives the totem.
 */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void schematictotem$useMiniature(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                             @Nullable World world, @Nullable HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        if (TotemModels.INSTANCE.tryRender(renderState, stack, displayContext, heldItemContext)) ci.cancel();
    }

    /** Item frames and dropped items pass no holder; remember the entity so the level of detail can use its distance. */
    @Inject(method = "updateForNonLivingEntity", at = @At("HEAD"))
    private void schematictotem$trackEntity(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity,
                                            CallbackInfo ci) {
        TotemModels.trackedEntity = entity;
    }

    @Inject(method = "updateForNonLivingEntity", at = @At("RETURN"))
    private void schematictotem$untrackEntity(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity,
                                              CallbackInfo ci) {
        TotemModels.trackedEntity = null;
    }
}
