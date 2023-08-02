package io.github.tt432.kitchenkarrot.item.food;

import io.github.tt432.kitchenkarrot.item.EffectEntry;
import io.github.tt432.kitchenkarrot.item.ModFood;
import io.github.tt432.kitchenkarrot.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.NotNull;

public class SnackDrinkItem extends ModFood {
    int tick;

    public SnackDrinkItem(int nutrition, float saturation, int tick, EffectEntry... effectEntries) {
        super(FoodUtil.effectFood(ModItems.defaultProperties(), nutrition, saturation,true, effectEntries).stacksTo(16));
        this.tick = tick;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack pStack) {
        return tick;
    }


    @Override
    @NotNull
    public UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.DRINK;
    }
}
