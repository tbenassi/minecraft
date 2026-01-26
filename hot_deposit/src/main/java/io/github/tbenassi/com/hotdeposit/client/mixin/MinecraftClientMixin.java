package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.event.SetScreenCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // This is injected into the minecraft client class. It is called every time a screen is set.
        ActionResult result = SetScreenCallback.EVENT.invoker().update(screen);
        if (result == ActionResult.FAIL) {
            // If the result is fail, cancel the event. This is so we can prevent the inventory screens from flashing when the hotkey is pressed
            ci.cancel();
        }
    }
}
