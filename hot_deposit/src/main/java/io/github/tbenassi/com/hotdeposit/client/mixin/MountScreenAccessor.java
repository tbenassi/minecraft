package io.github.tbenassi.com.hotdeposit.client.mixin;

import net.minecraft.client.gui.screen.ingame.MountScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MountScreen.class)
public interface MountScreenAccessor {

    @Accessor
    LivingEntity getMount();
}
