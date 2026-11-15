package dev.yimeng.schematictotem.mixin;

import dev.yimeng.schematictotem.TotemModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.toast.SystemToast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/** Dropping a .litematic / .schem / .nbt file onto the game window (in game or on any screen) imports and uses it. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onFilesDropped", at = @At("HEAD"), cancellable = true)
    private void schematictotem$importSchematic(long window, List<Path> paths, int invalidFilesCount, CallbackInfo ci) {
        List<Path> rest = TotemModels.INSTANCE.handleDroppedFiles(paths);
        if (rest.size() == paths.size()) return; // nothing for us: vanilla handles the drop
        ci.cancel();
        client.getInactivityFpsLimiter().onInput();
        if (!rest.isEmpty() && client.currentScreen != null) client.currentScreen.onFilesDropped(rest);
        if (invalidFilesCount > 0) SystemToast.addFileDropFailure(client, invalidFilesCount);
    }
}
