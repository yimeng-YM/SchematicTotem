package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.toasts.SystemToast;

/** Dropping a .litematic / .schem / .nbt file onto the game window (in game or on any screen) imports and uses it. */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onDrop", at = @At("HEAD"), cancellable = true)
    private void schematictotem$importSchematic(long window, List<Path> paths, int invalidFilesCount, CallbackInfo ci) {
        List<Path> rest = TotemModels.INSTANCE.handleDroppedFiles(paths);
        if (rest.size() == paths.size()) return; // nothing for us: vanilla handles the drop
        ci.cancel();
        minecraft.getFramerateLimitTracker().onInputReceived();
        if (!rest.isEmpty() && minecraft.screen != null) minecraft.screen.onFilesDrop(rest);
        if (invalidFilesCount > 0) SystemToast.onFileDropFailure(minecraft, invalidFilesCount);
    }
}
