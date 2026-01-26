package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.HotDepositClient;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void interactBlockHead(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        BlockEntity blockEntity = player.getWorld().getBlockEntity(hitResult.getBlockPos());
        HotDepositClient.addBlockEntityToQueue(player, blockEntity);
    }

    @Inject(method = "interactEntity", at = @At("HEAD"))
    public void interactEntityHead(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (entity instanceof ChestMinecartEntity) {
            HotDepositClient.addEntityToQueue((ClientPlayerEntity) player, entity);
        }
    }
}
