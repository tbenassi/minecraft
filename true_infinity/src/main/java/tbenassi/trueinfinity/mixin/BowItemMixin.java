package tbenassi.trueinfinity.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void allowInfinityWithoutArrows(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack itemStack = user.getStackInHand(hand);

        boolean hasAmmo = !user.getProjectileType(itemStack).isEmpty();
        boolean hasInfinity = world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT)
                .flatMap(registry -> registry.getOptional(Enchantments.INFINITY))
                .map(entry -> EnchantmentHelper.getLevel(entry, itemStack) > 0)
                .orElse(false);

        boolean canUse = hasAmmo || hasInfinity;

        if (!user.getAbilities().creativeMode && !canUse) {
            cir.setReturnValue(ActionResult.FAIL);
        } else {
            user.setCurrentHand(hand);
            cir.setReturnValue(ActionResult.CONSUME);
        }
    }
}
