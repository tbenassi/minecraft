package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.HotDepositClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZJ)V", at = @At("HEAD"), cancellable = true)
    private void cancelSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean bl, long l, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning())
            ci.cancel();
    }

    @Inject(method = "playSoundFromEntity", at = @At("HEAD"), cancellable = true)
    private void cancelSoundFromEntity(@Nullable PlayerEntity except, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning())
            ci.cancel();
    }
}
