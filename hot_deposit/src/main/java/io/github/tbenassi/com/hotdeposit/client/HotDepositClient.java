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
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.Item;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toSet;

@Environment(EnvType.CLIENT)
public class HotDepositClient implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final KeyBinding HOT_DEPOSIT_KEY_BINDING = new KeyBinding("Hot Deposit Keybind", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "Hot Deposit Settings");

    private static final BlockingQueue<ScreenHandler> DEPOSIT_SCREEN_QUEUE = new ArrayBlockingQueue<>(1);
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

        // Listen for container block entity events

    }

    private void addCheckboxToContainerScreen(MinecraftClient minecraftClient, Screen screen, int screenWidth, int screenHeight) {
        // Only add the checkbox to container screens
        if (!Utils.isContainerScreen(screen)) {
            return;
        }

        try {
            ChestLikeEntity container = CONTAINER_QUEUE.poll(60, TimeUnit.MILLISECONDS);
            if (container == null) {
                return;
            }

            BlockPos blockPos = container.getPos().toImmutable();
            boolean checked = ClientState.isContainerChecked(blockPos.asLong());
            int checkboxWidth = 15;
            int checkboxHeight = 15;

            if (container.isDoubleChest()) {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 230)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> ClientState.toggleContainerChecked(blockPos.asLong(), isChecked))
                        .posUpdater(parent -> getAbsolutePos(parent, -20, -108))
                        .build();
            } else if (container.isShulkerBox()) {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 230)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> ClientState.toggleContainerChecked(blockPos.asLong(), isChecked))
                        .posUpdater(parent -> getAbsolutePos(parent, -20, -80))
                        .build();
            }  else if (container.isMinecartChest())  {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 215)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> ClientState.toggleContainerChecked(blockPos.asLong(), isChecked))
                        .posUpdater(parent -> getAbsolutePos(parent, -20, -81))
                        .build();

            } else {
                CustomCheckboxWidget.builder((HandledScreen<?>) screen, Text.of("Hot Deposit"))
                        .pos(screenWidth - 30, screenHeight - 230)
                        .size(checkboxWidth, checkboxHeight)
                        .checked(checked)
                        .callback((checkbox, isChecked) -> ClientState.toggleContainerChecked(blockPos.asLong(), isChecked))
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

    public static void addScreenToQueue(ScreenHandler screenHandler) {
        DEPOSIT_SCREEN_QUEUE.add(screenHandler);
    }

    public static void addBlockEntityToQueue(ClientPlayerEntity player, BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity ||
                blockEntity instanceof ShulkerBoxBlockEntity ||
                blockEntity instanceof EnderChestBlockEntity
        ) {
            if (ChestBlock.isChestBlocked(player.getWorld(), blockEntity.getPos()))
                return;
            // check if double chest is blocked
            if (HotDepositClient.isDoubleChest(blockEntity)) {
                BlockPos otherHalfPos = HotDepositClient.getTheOtherHalfPosOfLargeChest(player.getWorld(), blockEntity.getPos());
                if (otherHalfPos != null && ChestBlock.isChestBlocked(player.getWorld(), otherHalfPos)) {
                    return;
                }
            }
            if (!HotDepositClient.isHotDepositRunning()) {
                ChestLikeEntity container = new ChestLikeEntity(blockEntity);
                CONTAINER_QUEUE.clear(); // clear the queue to avoid multiple containers
                CONTAINER_QUEUE.add(container);
            }
        } else if (blockEntity instanceof BarrelBlockEntity) {
            if (!HotDepositClient.isHotDepositRunning()){
                ChestLikeEntity container = new ChestLikeEntity(blockEntity);
                CONTAINER_QUEUE.clear(); // clear the queue to avoid multiple containers
                CONTAINER_QUEUE.add(container);
            }
        }
    }

    public static void addEntityToQueue(ClientPlayerEntity player, Entity entity) {
        if (entity instanceof ChestMinecartEntity) {
            if (!HotDepositClient.isHotDepositRunning()) {
                ChestLikeEntity container = new ChestLikeEntity(entity);
                CONTAINER_QUEUE.clear(); // clear the queue to avoid multiple containers
                CONTAINER_QUEUE.add(container);
            }
        }
    }

    public Runnable depositToContainersTask() {
        return () -> {
            try {
                // Initialize all the things we need
                MinecraftClient client = MinecraftClient.getInstance();
                ClientWorld world = Objects.requireNonNull(client.world);
                ClientPlayerEntity player = Objects.requireNonNull(client.player);
                ClientPlayerInteractionManager interactionManager = Objects.requireNonNull(client.interactionManager);

                // Get the player's position
                Vec3d playerPos = player.getCameraPosVec(0);
                var chestsInRange = getReachableContainers(world, playerPos, player);
                if (chestsInRange.isEmpty()) {
                    LOGGER.warn("No chests in range");
                    return;
                }

                Set<BlockPos> searchedContainers = new HashSet<>();
                boolean searchedEnderChest = false;

                // Open the chests and deposit items
                for (BlockPos pos : chestsInRange) {
                    boolean hotDeposit = ClientState.isContainerChecked(pos.asLong());
                    if (!hotDeposit) {
                        continue;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    BlockState state = world.getBlockState(pos);
                    Vec3d closestPos = Utils.getClosestPoint(pos, state.getOutlineShape(world, pos), playerPos);

                    // If the container is an ender chest, check if we've already opened and searched it
                    if (state.getBlock() instanceof EnderChestBlock) {
                        if (searchedEnderChest) {
                            continue;
                        }
                        searchedEnderChest = true;
                    }

                    // If the container is a large chest, get the pos of the other half and add it to the searched containers
                    if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                        BlockPos otherHalfPos = getTheOtherHalfPosOfLargeChest(world, pos);
                        if (otherHalfPos != null) {
                            searchedContainers.add(otherHalfPos);
                            if (chestsInRange.stream().anyMatch(otherHalfPos::equals)) {
                                hotDeposit = ClientState.isContainerChecked(otherHalfPos.asLong());
                                if (!hotDeposit) {
                                    continue;
                                }
                            }
                        }
                    }

                    // Don't search if the container is already searched
                    if (searchedContainers.contains(pos)) {
                        continue;
                    }

                    searchedContainers.add(pos);
                    interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(closestPos, Utils.getFacingDirection(closestPos.subtract(playerPos)), pos, false));
                    ScreenHandler screenHandler = DEPOSIT_SCREEN_QUEUE.poll(4, TimeUnit.SECONDS);

                    if (screenHandler == null) {
                        throw new TimeoutException();
                    }
                    depositItemsToContainer(screenHandler, client);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
//                        sendChatMessage("stack-to-nearby-chests.message.operationInterrupted");
                LOGGER.error("The operation was interrupted", e);
            } catch (TimeoutException e) {
//                        sendChatMessage("stack-to-nearby-chests.message.interruptedByTimeout");
                LOGGER.error("The operation was interrupted by timeout", e);
            } catch (Exception e) {
//                        sendChatMessage("stack-to-nearby-chests.message.exceptionOccurred");
                LOGGER.error("An exception occurred during the operation", e);
            } finally {
                DEPOSIT_SCREEN_QUEUE.clear();
                CONTAINER_QUEUE.clear();
                MinecraftClient.getInstance().executeSync(() -> {
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.closeHandledScreen();
                    }
                    DEPOSIT_THREAD = null;
                });
            }
        };
    }

    public List<BlockPos> getReachableContainers(ClientWorld world, Vec3d playerPos, ClientPlayerEntity clientPlayerEntity) {
        // Get the player's surrounding container blocks
        float reachDistance = (float) clientPlayerEntity.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        float squaredReachDistance = reachDistance * reachDistance;
        Box area = Utils.getBox(playerPos, reachDistance);
        return StreamSupport.stream(Utils.getBlocksInBox(area).spliterator(), false).map(BlockPos::toImmutable).filter(pos -> {
            // Check if the block is a container
            BlockState blockState = world.getBlockState(pos);
            Block block = blockState.getBlock();
            return (block instanceof ChestBlock ||
                    block instanceof EnderChestBlock ||
                    block instanceof BarrelBlock ||
                    block instanceof ShulkerBoxBlock) && !ChestBlock.isChestBlocked(world, pos);
        }).filter(pos -> {
            // Check if the container is reachable
            Vec3d closestPos = Utils.getClosestPoint(pos, world.getBlockState(pos).getOutlineShape(world, pos), playerPos);
            return closestPos.squaredDistanceTo(playerPos) <= squaredReachDistance;
        }).toList();
    }

    public static boolean isDoubleChest(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) {
            BlockPos pos = blockEntity.getPos();
            World world = blockEntity.getWorld();
            BlockState state = null;
            if (world != null) {
                state = world.getBlockState(pos);
            }
            if (state != null) {
                return state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE;
            }
        }
        return false;
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

    public static void depositItemsToContainer(ScreenHandler containerScreenHandler, MinecraftClient client) {
        var slots = SlotsInScreenHandler.of(containerScreenHandler);

        Set<Item> itemsInContainer = slots.containerSlots().stream().map(slot -> slot.getStack().getItem()).filter(item -> !(item instanceof AirBlockItem)).collect(toSet());

        slots.playerSlots().stream().filter(slot -> itemsInContainer.contains(slot.getStack().getItem())).filter(slot -> slot.canTakeItems(client.player)).filter(Slot::hasStack).forEach(slot -> {
            sendChatMessage(String.format("Moving %d %s(s) to container", slot.getStack().getCount(), slot.getStack().getItem().getName().getString()));
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager == null) return;
            interactionManager.clickSlot(containerScreenHandler.syncId, slot.id, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.QUICK_MOVE, client.player);
        });
    }

    private record SlotsInScreenHandler(List<Slot> playerSlots, List<Slot> containerSlots) {

        static SlotsInScreenHandler of(ScreenHandler screenHandler) {
            Map<Boolean, List<Slot>> inventories = screenHandler.slots.stream().collect(partitioningBy(slot -> slot.inventory instanceof PlayerInventory));

            return new SlotsInScreenHandler(inventories.get(true), inventories.get(false));
        }
    }

    private static void sendChatMessage(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable(message));
    }
}
