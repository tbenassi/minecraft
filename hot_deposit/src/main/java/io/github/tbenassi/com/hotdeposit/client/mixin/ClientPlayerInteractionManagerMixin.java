package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.ChestLikeEntity;
import io.github.tbenassi.com.hotdeposit.client.HotDepositClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void interactBlockHead(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return;
        BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
        if (ChestLikeEntity.isValidChestLikeBlockEntity(blockEntity)) {
            HotDepositClient.LOGGER.debug("Block Entity is: {}", blockEntity.getClass().getName());
            HotDepositClient.addBlockEntityToQueue(player, blockEntity);
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"))
    public void interactEntityHead(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (ChestLikeEntity.isValidChestLikeEntity(entity)) {
            HotDepositClient.LOGGER.debug("Entity is: {}", entity.getClass().getName());
            HotDepositClient.addEntityToQueue((ClientPlayerEntity) player, entity);
        }
    }
}
