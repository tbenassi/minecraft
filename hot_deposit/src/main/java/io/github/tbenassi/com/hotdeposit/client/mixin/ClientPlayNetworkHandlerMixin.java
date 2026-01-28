package io.github.tbenassi.com.hotdeposit.client.mixin;

import io.github.tbenassi.com.hotdeposit.client.HotDepositClient;
import io.github.tbenassi.com.hotdeposit.client.HotDepositState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenMountScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts server-to-client packets for screen/inventory handling.
 * During hot deposit, we capture the data we need. The normal handlers still run
 * to maintain proper client state, but MinecraftClientMixin prevents screen display.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onOpenScreen", at = @At("HEAD"))
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) {
            HotDepositClient.LOGGER.debug("Captured OpenScreenS2CPacket: syncId={}", packet.getSyncId());
            HotDepositState.getInstance().onScreenOpened(packet.getSyncId(), packet.getScreenHandlerType());
        }
    }

    @Inject(method = "onOpenMountScreen", at = @At("HEAD"))
    private void onOpenMountScreen(OpenMountScreenS2CPacket packet, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) {
            HotDepositClient.LOGGER.debug("Captured OpenMountScreenS2CPacket: syncId={}, slotColumns={}, mountId={}",
                    packet.getSyncId(), packet.getSlotColumnCount(), packet.getMountId());
            HotDepositState.getInstance().onMountScreenOpened(packet.getSyncId(), packet.getSlotColumnCount(), packet.getMountId());
        }
    }

    @Inject(method = "onInventory", at = @At("HEAD"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) {
            HotDepositState state = HotDepositState.getInstance();
            // Only capture inventory packets for our screen
            if (packet.syncId() == state.getSyncId()) {
                HotDepositClient.LOGGER.debug("Captured InventoryS2CPacket: syncId={}, revision={}, slots={}",
                        packet.syncId(), packet.revision(), packet.contents().size());
                state.onInventoryReceived(
                        packet.syncId(),
                        packet.revision(),
                        packet.contents(),
                        packet.cursorStack()
                );
            }
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) {
            HotDepositState state = HotDepositState.getInstance();
            // Only capture slot updates for our screen
            if (packet.getSyncId() == state.getSyncId()) {
                state.onSlotUpdate(
                        packet.getSyncId(),
                        packet.getRevision(),
                        packet.getSlot(),
                        packet.getStack()
                );
            }
        }
    }

    @Inject(method = "onCloseScreen", at = @At("HEAD"))
    private void onCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        if (HotDepositClient.isHotDepositRunning()) {
            HotDepositClient.LOGGER.debug("Captured CloseScreenS2CPacket");
            HotDepositState.getInstance().onScreenClosed();
        }
    }
}
