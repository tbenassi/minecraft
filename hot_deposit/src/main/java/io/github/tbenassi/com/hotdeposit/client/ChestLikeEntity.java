package io.github.tbenassi.com.hotdeposit.client;

import io.github.tbenassi.com.hotdeposit.client.mixin.MountScreenAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MountScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Wrapper for container sources - either a BlockEntity or an Entity with inventory.
 */
public class ChestLikeEntity {
    private final World world;
    private final BlockPos pos;
    private final BlockEntity blockEntity;
    private final Entity entity;

    public ChestLikeEntity(BlockEntity blockEntity) {
        this.world = blockEntity.getWorld();
        this.pos = blockEntity.getPos();
        this.blockEntity = blockEntity;
        this.entity = null;
    }

    public ChestLikeEntity(Entity entity) {
        this.world = entity.getEntityWorld();
        this.pos = entity.getBlockPos();
        this.blockEntity = null;
        this.entity = entity;
    }

    /**
     * Check if this container is enabled for hot deposit.
     * Uses BlockPos for block entities, UUID for mobile entities.
     */
    public boolean isHotDepositEnabled() {
        if (isBlockEntity()) {
            return ClientState.isBlockContainerChecked(pos.asLong());
        } else if (isEntity()) {
            return ClientState.isEntityContainerChecked(entity.getUuid());
        }
        return false;
    }

    /**
     * Toggle hot deposit state for this container.
     */
    public void setHotDepositEnabled(boolean enabled) {
        if (isBlockEntity()) {
            ClientState.toggleBlockContainerChecked(pos.asLong(), enabled);
        } else if (isEntity()) {
            ClientState.toggleEntityContainerChecked(entity.getUuid(), enabled);
        }
    }

    /**
     * Get the entity UUID if this is an entity container.
     */
    public UUID getEntityUuid() {
        return entity != null ? entity.getUuid() : null;
    }

    public static boolean isValidChestLikeBlockEntity(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Inventory inv)) return false;
        return inv.size() >= 27;
    }

    public static boolean isValidChestLikeEntity(Entity entity) {
        if (entity instanceof VehicleInventory vi) return vi.size() >= 27;
        if (entity instanceof AbstractHorseEntity e){
            HotDepositClient.LOGGER.debug("Horse inventory size: {}", e.getInventorySize());
            return e.getInventorySize() >= 15;
        }
        return false;
    }

    public static boolean isValidContainerScreen(Screen screen) {
        if (!(screen instanceof HandledScreen<?>)) return false;
        return switch (screen) {
            case ShulkerBoxScreen ignored -> true;
            case GenericContainerScreen ignored -> true;
            case MountScreen<?> ms -> {
                HotDepositClient.LOGGER.debug("Checking MountScreen for horse inventory");
                yield ((MountScreenAccessor) ms).getMount() instanceof AbstractHorseEntity entity
                        && entity.getInventorySize() >= 15;
            }

            default -> false;
        };
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public boolean isBlockEntity() {
        return this.blockEntity != null;
    }

    public boolean isEntity() {
        return this.entity != null;
    }

    public BlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public Boolean isDoubleChest() {
        if (this.blockEntity != null && this.blockEntity instanceof ChestBlockEntity) {
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
}
