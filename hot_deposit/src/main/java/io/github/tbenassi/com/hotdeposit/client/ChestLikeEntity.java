package io.github.tbenassi.com.hotdeposit.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

    public World getWorld() {
        return this.world;
    }

    public BlockPos getPos() {
        return this.pos;
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

    public Boolean isShulkerBox() {
        return this.blockEntity != null && this.blockEntity instanceof ShulkerBoxBlockEntity;
    }

    public Boolean isMinecartChest() {
        return this.entity != null && this.entity instanceof ChestMinecartEntity;
    }
}
