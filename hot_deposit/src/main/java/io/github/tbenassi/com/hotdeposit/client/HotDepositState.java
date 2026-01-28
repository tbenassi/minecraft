package io.github.tbenassi.com.hotdeposit.client;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages state for the hot deposit operation.
 * Tracks syncId, revision, and inventory contents from server packets.
 */
public class HotDepositState {
    private static final HotDepositState INSTANCE = new HotDepositState();

    private int syncId = -1;
    private int revision = 0;
    private ScreenHandlerType<?> screenType = null;
    private List<ItemStack> contents = new ArrayList<>();
    private ItemStack cursorStack = ItemStack.EMPTY;

    // Futures for async packet responses
    private CompletableFuture<Integer> screenOpenFuture = null;
    private CompletableFuture<List<ItemStack>> inventoryFuture = null;

    private HotDepositState() {}

    public static HotDepositState getInstance() {
        return INSTANCE;
    }

    /**
     * Called when we receive OpenScreenS2CPacket (for block containers)
     */
    public void onScreenOpened(int syncId, ScreenHandlerType<?> screenType) {
        this.syncId = syncId;
        this.screenType = screenType;
        this.revision = 0;
        this.contents.clear();

        if (screenOpenFuture != null && !screenOpenFuture.isDone()) {
            screenOpenFuture.complete(syncId);
        }

        HotDepositClient.LOGGER.debug("Screen opened: syncId={}, type={}", syncId, screenType);
    }

    /**
     * Called when we receive OpenMountScreenS2CPacket (for horse/donkey/mule inventories)
     */
    public void onMountScreenOpened(int syncId, int slotColumnCount, int mountId) {
        this.syncId = syncId;
        this.screenType = null; // Mount screens don't have a ScreenHandlerType
        this.revision = 0;
        this.contents.clear();

        if (screenOpenFuture != null && !screenOpenFuture.isDone()) {
            screenOpenFuture.complete(syncId);
        }

        HotDepositClient.LOGGER.debug("Mount screen opened: syncId={}, slotColumns={}, mountId={}", syncId, slotColumnCount, mountId);
    }

    /**
     * Called when we receive InventoryS2CPacket
     */
    public void onInventoryReceived(int syncId, int revision, List<ItemStack> contents, ItemStack cursorStack) {
        if (this.syncId != syncId) {
            return; // Not our container
        }

        this.revision = revision;
        this.contents = new ArrayList<>(contents);
        this.cursorStack = cursorStack;

        if (inventoryFuture != null && !inventoryFuture.isDone()) {
            inventoryFuture.complete(contents);
        }

        HotDepositClient.LOGGER.debug("Inventory received: syncId={}, revision={}, slots={}", syncId, revision, contents.size());
    }

    /**
     * Called when we receive ScreenHandlerSlotUpdateS2CPacket
     */
    public void onSlotUpdate(int syncId, int revision, int slot, ItemStack stack) {
        if (this.syncId != syncId) {
            return;
        }

        this.revision = revision;
        if (slot >= 0 && slot < contents.size()) {
            contents.set(slot, stack);
        }
    }

    /**
     * Called when screen is closed (either by us or server)
     */
    public void onScreenClosed() {
        this.syncId = -1;
        this.revision = 0;
        this.screenType = null;
        this.contents.clear();
        this.cursorStack = ItemStack.EMPTY;
    }

    /**
     * Wait for a screen to open (with timeout)
     */
    public void waitForScreenOpen(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        screenOpenFuture = new CompletableFuture<>();
        try {
            screenOpenFuture.get(timeout, unit);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wait for inventory contents (with timeout)
     */
    public List<ItemStack> waitForInventory(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        inventoryFuture = new CompletableFuture<>();
        try {
            return inventoryFuture.get(timeout, unit);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // Getters
    public int getSyncId() { return syncId; }
    public int getRevision() { return revision; }
    public ScreenHandlerType<?> getScreenType() { return screenType; }
    public List<ItemStack> getContents() { return contents; }
    public ItemStack getCursorStack() { return cursorStack; }
    public boolean isScreenOpen() { return syncId >= 0; }

}
