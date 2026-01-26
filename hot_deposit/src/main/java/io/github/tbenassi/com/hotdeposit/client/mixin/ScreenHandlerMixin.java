package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.HotDepositClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ScreenHandler.class)
@Environment(EnvType.CLIENT)
public abstract class ScreenHandlerMixin {

    @Inject(method = "updateSlotStacks(ILjava/util/List;Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void onUpdateSlotStacks(int revision, List<ItemStack> stacks, ItemStack cursorStack, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) HotDepositClient.addScreenToQueue((ScreenHandler) (Object) this);
    }
}
