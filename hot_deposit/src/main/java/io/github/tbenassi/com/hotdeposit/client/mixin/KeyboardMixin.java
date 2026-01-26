package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.event.OnKeyCallback;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Keyboard;debugCrashStartTime:J", ordinal = 0), cancellable = true)
    private void onOnKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            return;
        }
        ActionResult result = switch (action) {
            case 0 -> OnKeyCallback.RELEASE.invoker().update(key);
            case 1 -> OnKeyCallback.PRESS.invoker().update(key);
            default -> ActionResult.PASS;
        };

        if (result == ActionResult.FAIL) {
            ci.cancel();
        }
    }
}
