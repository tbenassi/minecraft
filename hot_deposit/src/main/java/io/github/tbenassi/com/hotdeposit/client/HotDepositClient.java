package io.github.tbenassi.com.hotdeposit.client;

import com.mojang.logging.LogUtils;
import io.github.cottonmc.cotton.gui.widget.data.Vec2i;
import io.github.tbenassi.com.hotdeposit.client.event.OnKeyCallback;
import io.github.tbenassi.com.hotdeposit.client.event.SetScreenCallback;
import io.github.tbenassi.com.hotdeposit.client.gui.CustomCheckboxWidget;
import io.github.tbenassi.com.hotdeposit.client.mixin.HandledScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Environment(EnvType.CLIENT)
public class HotDepositClient implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final KeyBinding HOT_DEPOSIT_KEY_BINDING = new KeyBinding(
            "key.hot-deposit.deposit",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            new KeyBinding.Category(Identifier.of("hot-deposit", "keybinds"))
    );

    private static HotDepositThread DEPOSIT_THREAD = null;
    private static final BlockingQueue<ChestLikeEntity> CONTAINER_QUEUE = new ArrayBlockingQueue<>(2);

    @Override
    public void onInitializeClient() {
        ClientState.init();
        // Register keybind and setup callbacks
        KeyBindingHelper.registerKeyBinding(HOT_DEPOSIT_KEY_BINDING);

        // Listen for the hot deposit keybinding press
        OnKeyCallback.PRESS.register(key -> {
            if (key == HOT_DEPOSIT_KEY_BINDING.getDefaultKey().getCode()) {
                if (HotDepositThread.running(DEPOSIT_THREAD)) {
                    HotDepositThread.interruptCurrentOperation(DEPOSIT_THREAD);
                } else {
                    DEPOSIT_THREAD = new HotDepositThread(new Thread(depositToContainersTask()));
                    DEPOSIT_THREAD.start();
                }
            }
            return ActionResult.PASS;
        });

        // Listen for the esc key when hot deposit is running, if pressed, cancel the hot deposit
        OnKeyCallback.PRESS.register(key -> {
            if (HotDepositThread.running(DEPOSIT_THREAD)) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    HotDepositThread.interruptCurrentOperation(DEPOSIT_THREAD);
                }

                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Listen for screen changes, if we are running the hot deposit thread and the
        // screen changes to the death screen, interrupt the thread
        // if we are running and the screen is not the death screen, fail the event
        // a failed event means we do not show the inventory screen
        SetScreenCallback.EVENT.register(screen -> {
            if (HotDepositThread.running(DEPOSIT_THREAD)) {
                if (screen instanceof DeathScreen) {
                    HotDepositThread.interruptCurrentOperation(DEPOSIT_THREAD);
                    return ActionResult.PASS;
                }

                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // Listen for container screens
        ScreenEvents.AFTER_INIT.register(this::addCheckboxToContainerScreen);
    }

    private void addCheckboxToContainerScreen(MinecraftClient minecraftClient, Screen screen, int screenWidth, int screenHeight) {
        // Only add the checkbox to container screens
        if (!ChestLikeEntity.isValidContainerScreen(screen)) {
            LOGGER.debug("Not a container screen");
            return;
        }

        try {
            ChestLikeEntity container = CONTAINER_QUEUE.poll(60, TimeUnit.MILLISECONDS);
            if (container == null) {
                LOGGER.debug("No container in queue");
                return;
            }

            boolean checked = container.isHotDepositEnabled();
            int checkboxWidth = 15;
            int checkboxHeight = 15;

            if (container.isDoubleChest()) {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 230)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> container.setHotDepositEnabled(isChecked))
                        .posUpdater(parent -> getAbsolutePos(parent, -20, -108))
                        .build();
            } else {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 230)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> container.setHotDepositEnabled(isChecked))
                        .posUpdater(parent -> getAbsolutePos(parent, -20, -81))
                        .build();
            }
        } catch (InterruptedException e) {
            LOGGER.error("The operation was interrupted", e);
        }
        CONTAINER_QUEUE.clear();
    }

    private static Vec2i getAbsolutePos(HandledScreenAccessor parent, int x, int y) {
        return new Vec2i(parent.getX() + parent.getBackgroundWidth() + x, parent.getY() + parent.getBackgroundHeight() / 2 + y);
    }

    public static boolean isHotDepositRunning() {
        return HotDepositThread.running(DEPOSIT_THREAD);
    }

    public static void addBlockEntityToQueue(ClientPlayerEntity player, BlockEntity blockEntity) {
        if (!HotDepositClient.isHotDepositRunning()) {
            ChestLikeEntity container = new ChestLikeEntity(blockEntity);
            CONTAINER_QUEUE.clear(); // clear the queue to avoid multiple containers
            CONTAINER_QUEUE.add(container);
        }
    }

    public static void addEntityToQueue(ClientPlayerEntity player, Entity entity) {
        if (!HotDepositClient.isHotDepositRunning()) {
            ChestLikeEntity container = new ChestLikeEntity(entity);
            CONTAINER_QUEUE.clear(); // clear the queue to avoid multiple containers
            CONTAINER_QUEUE.add(container);
        }
    }

    public Runnable depositToContainersTask() {
        return () -> {
            HotDepositState state = HotDepositState.getInstance();
            try {
                // Initialize all the things we need
                MinecraftClient client = MinecraftClient.getInstance();
                ClientWorld world = Objects.requireNonNull(client.world);
                ClientPlayerEntity player = Objects.requireNonNull(client.player);

                // Get the player's position
                Vec3d playerPos = player.getCameraPosVec(0);
                var containersInRange = getReachableContainers(world, playerPos, player);
                if (containersInRange.isEmpty()) {
                    LOGGER.warn("No containers in range");
                    return;
                }

                Set<BlockPos> searchedBlockPositions = new HashSet<>();
                Set<Integer> searchedEntityIds = new HashSet<>();
                boolean searchedEnderChest = false;

                // Open containers and deposit items
                for (ChestLikeEntity container : containersInRange) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    // Check if this container is enabled for hot deposit
                    if (!container.isHotDepositEnabled()) {
                        continue;
                    }

                    BlockPos pos = container.getPos();

                    if (container.isBlockEntity()) {
                        // Handle block entity containers (chests, barrels, shulkers, etc.)
                        BlockState blockState = world.getBlockState(pos);
                        Vec3d closestPos = Utils.getClosestPoint(pos, blockState.getOutlineShape(world, pos), playerPos);

                        // Skip if already searched this position
                        if (searchedBlockPositions.contains(pos)) {
                            continue;
                        }

                        // Ender chest deduplication (all ender chests share the same inventory)
                        if (blockState.getBlock() instanceof EnderChestBlock) {
                            if (searchedEnderChest) {
                                continue;
                            }
                            searchedEnderChest = true;
                        }

                        // Double chest handling - mark other half as searched
                        if (blockState.getBlock() instanceof ChestBlock && blockState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                            BlockPos otherHalfPos = getTheOtherHalfPosOfLargeChest(world, pos);
                            if (otherHalfPos != null) {
                                searchedBlockPositions.add(otherHalfPos);
                            }
                        }

                        searchedBlockPositions.add(pos);

                        // Send packet to open the block container
                        Direction side = Utils.getFacingDirection(closestPos.subtract(playerPos));
                        HotDepositPackets.openBlockContainer(pos, closestPos, side);

                    } else if (container.isEntity()) {
                        // Handle entity containers (chest boats, minecarts, donkeys, etc.)
                        Entity entity = container.getEntity();
                        if (entity == null || searchedEntityIds.contains(entity.getId())) {
                            continue;
                        }

                        searchedEntityIds.add(entity.getId());

                        // Send packet to open the entity container
                        HotDepositPackets.openEntityContainer(entity);
                    }

                    // Wait for screen to open and inventory to be received
                    state.waitForScreenOpen(4, TimeUnit.SECONDS);
                    List<ItemStack> contents = state.waitForInventory(4, TimeUnit.SECONDS);

                    // Deposit items using packet-based approach
                    depositItemsViaPackets(state, contents);

                    // Close the container
                    HotDepositPackets.closeScreen(state.getSyncId());
                    state.onScreenClosed();

                    // Small delay between containers
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("The operation was interrupted", e);
            } catch (TimeoutException e) {
                LOGGER.error("The operation was interrupted by timeout", e);
            } catch (Exception e) {
                LOGGER.error("An exception occurred during the operation", e);
            } finally {
                // Clean up state
                if (state.isScreenOpen()) {
                    HotDepositPackets.closeScreen(state.getSyncId());
                }
                state.onScreenClosed();
                CONTAINER_QUEUE.clear();
                DEPOSIT_THREAD = null;
            }
        };
    }

    /**
     * Deposit items from player inventory to container using packets.
     * The contents list contains both container slots and player inventory slots.
     * Player inventory is always the last 36 slots (27 main + 9 hotbar).
     */
    private void depositItemsViaPackets(HotDepositState state, List<ItemStack> contents) {
        int totalSlots = contents.size();
        int playerInventoryStart = totalSlots - 36; // Player inventory is last 36 slots

        if (playerInventoryStart <= 0) {
            LOGGER.warn("Invalid inventory size: {}", totalSlots);
            return;
        }

        // Get items that exist in the container
        Set<Item> itemsInContainer = new HashSet<>();
        for (int i = 0; i < playerInventoryStart; i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                itemsInContainer.add(stack.getItem());
            }
        }

        // Find player inventory slots with matching items and transfer them
        int revision = state.getRevision();
        for (int i = playerInventoryStart; i < totalSlots; i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty() && itemsInContainer.contains(stack.getItem())) {
                sendChatMessage(String.format("Moving %d %s(s) to container",
                        stack.getCount(), stack.getItem().getName().getString()));

                revision = HotDepositPackets.quickMoveSlot(state.getSyncId(), revision, i);

                // Small delay between transfers to avoid overwhelming the server
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public List<ChestLikeEntity> getReachableContainers(ClientWorld world, Vec3d playerPos, ClientPlayerEntity player) {
        float reachDistance = (float) player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        double squaredReach = reachDistance * reachDistance;
        Box area = Utils.getBox(playerPos, reachDistance);

        List<ChestLikeEntity> result = new ArrayList<>();

        // 1. Get inventory ENTITIES (chest boats, minecarts, donkeys w/ chests)
        world.getOtherEntities(player, area, ChestLikeEntity::isValidChestLikeEntity).stream()
                .filter(e -> e.squaredDistanceTo(playerPos) <= squaredReach)
                .map(ChestLikeEntity::new)
                .forEach(result::add);

        // 2. Get inventory BLOCK ENTITIES by iterating chunks (not every block)
        getBlockEntitiesInArea(world, area).stream()
                .filter(ChestLikeEntity::isValidChestLikeBlockEntity)
                .filter(be -> !ChestBlock.isChestBlocked(world, be.getPos()))
                .filter(be -> isBlockReachable(world, be.getPos(), playerPos, squaredReach))
                .map(ChestLikeEntity::new)
                .forEach(result::add);

        return result;
    }

    private List<BlockEntity> getBlockEntitiesInArea(ClientWorld world, Box area) {
        List<BlockEntity> result = new ArrayList<>();
        int minChunkX = ChunkSectionPos.getSectionCoord(area.minX);
        int maxChunkX = ChunkSectionPos.getSectionCoord(area.maxX);
        int minChunkZ = ChunkSectionPos.getSectionCoord(area.minZ);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(area.maxZ);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                WorldChunk chunk = world.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (area.contains(Vec3d.ofCenter(be.getPos()))) {
                        result.add(be);
                    }
                }
            }
        }
        return result;
    }

    private boolean isBlockReachable(ClientWorld world, BlockPos pos, Vec3d playerPos, double squaredReach) {
        Vec3d closest = Utils.getClosestPoint(pos, world.getBlockState(pos).getOutlineShape(world, pos), playerPos);
        return closest.squaredDistanceTo(playerPos) <= squaredReach;
    }

    public static BlockPos getTheOtherHalfPosOfLargeChest(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockPos offsetPos = pos.offset(ChestBlock.getFacing(state)); // getFacing(BlockState) returns the direction in which the other half of the chest is located
        BlockState theOtherHalf = world.getBlockState(offsetPos);
        if (theOtherHalf.getBlock() == state.getBlock() && state.get(ChestBlock.FACING) == theOtherHalf.get(ChestBlock.FACING) && ChestBlock.getFacing(state) == ChestBlock.getFacing(theOtherHalf).getOpposite()) {
            return offsetPos;
        }
        return null;
    }

    private static void sendChatMessage(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable(message));
    }
}
