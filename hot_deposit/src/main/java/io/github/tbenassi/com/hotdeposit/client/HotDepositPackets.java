package io.github.tbenassi.com.hotdeposit.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Utility class for sending packets related to hot deposit operations.
 * Bypasses client-side interaction managers for cleaner, more maintainable code.
 */
public class HotDepositPackets {

    /**
     * Send a packet to open a block container (chest, barrel, shulker, etc.)
     */
    public static void openBlockContainer(BlockPos pos, Vec3d hitPos, Direction side) {
        ClientPlayNetworkHandler networkHandler = getNetworkHandler();
        if (networkHandler == null) return;

        BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);
        networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
    }

    /**
     * Send a packet to open an entity container (chest boat, minecart, donkey, etc.)
     * For horse-like entities, sends with sneaking=true to open inventory instead of mounting.
     */
    public static void openEntityContainer(Entity entity) {
        ClientPlayNetworkHandler networkHandler = getNetworkHandler();
        if (networkHandler == null) return;

        boolean sneaking = entity instanceof AbstractHorseEntity;
        networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, sneaking, Hand.MAIN_HAND));

        // Reset sneak state immediately after interacting with horse-family entities.
        // The interact packet sets sneaking=true (required to open inventory instead of mounting),
        // but ServerPlayNetworkHandler syncs that sneak state back to clients, causing a visual crouch.
        // Sending a PlayerInputC2SPacket with sneak=false resets the state before the tick completes.
        if (sneaking) {
            networkHandler.sendPacket(new PlayerInputC2SPacket(
                    new PlayerInput(false, false, false, false, false, false, false)));
        }
    }

    /**
     * Send a packet to transfer an item from one slot to another using shift-click.
     * This is the QUICK_MOVE action which moves items between player inventory and container.
     *
     * @param syncId The screen handler sync ID
     * @param revision The current revision number
     * @param slot The slot to shift-click
     * @return The new revision number
     */
    public static int quickMoveSlot(int syncId, int revision, int slot) {
        ClientPlayNetworkHandler networkHandler = getNetworkHandler();
        if (networkHandler == null) return revision;

        // For QUICK_MOVE, we don't need to track modified stacks since:
        // 1. We're not rendering the screen
        // 2. Server will send us updates via ScreenHandlerSlotUpdateS2CPacket
        Int2ObjectMap<ItemStackHash> modifiedStacks = new Int2ObjectOpenHashMap<>();

        // Cursor should be empty for QUICK_MOVE operations
        ItemStackHash cursorHash = ItemStackHash.fromItemStack(ItemStack.EMPTY, networkHandler.getComponentHasher());

        ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
                syncId,
                revision,
                (short) slot,
                (byte) 0, // Left click
                SlotActionType.QUICK_MOVE,
                modifiedStacks,
                cursorHash
        );

        networkHandler.sendPacket(packet);
        return revision + 1;
    }

    /**
     * Send a packet to close the current screen handler.
     */
    public static void closeScreen(int syncId) {
        ClientPlayNetworkHandler networkHandler = getNetworkHandler();
        if (networkHandler == null) return;

        networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
    }

    private static ClientPlayNetworkHandler getNetworkHandler() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getNetworkHandler();
    }
}
