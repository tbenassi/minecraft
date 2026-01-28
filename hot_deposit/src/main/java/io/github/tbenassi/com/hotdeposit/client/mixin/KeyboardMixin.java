package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.event.OnKeyCallback;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Keyboard;debugCrashStartTime:J", ordinal = 0), cancellable = true)
    private void onOnKey(long window, int action, KeyInput input, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            return;
        }
        int key = input.key();
        ActionResult result = switch (action) {
            case InputUtil.GLFW_RELEASE -> OnKeyCallback.RELEASE.invoker().update(key);
            case InputUtil.GLFW_PRESS -> OnKeyCallback.PRESS.invoker().update(key);
            default -> ActionResult.PASS;
        };

        if (result == ActionResult.FAIL) {
            ci.cancel();
        }
    }
}
