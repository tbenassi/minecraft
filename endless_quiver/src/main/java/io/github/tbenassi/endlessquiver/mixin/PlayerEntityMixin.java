package io.github.tbenassi.endlessquiver.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    private static final RegistryKey<Enchantment> ENDLESS_QUIVER =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("endless_quiver", "endless_quiver"));

    @Inject(method = "getProjectileType", at = @At("RETURN"), cancellable = true)
    private void endlessQuiver_supplyArrow(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) {
            return;
        }

        PlayerEntity self = (PlayerEntity) (Object) this;
        boolean hasEnchant = self.getEntityWorld().getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT)
                .flatMap(registry -> registry.getOptional(ENDLESS_QUIVER))
                .map(entry -> EnchantmentHelper.getLevel(entry, weapon) > 0)
                .orElse(false);

        if (hasEnchant) {
            cir.setReturnValue(new ItemStack(Items.ARROW));
        }
    }
}
