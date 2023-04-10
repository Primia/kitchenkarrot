package io.github.tt432.kitchenkarrot.block;

import io.github.tt432.kitchenkarrot.blockentity.ModBlockEntities;
import io.github.tt432.kitchenkarrot.blockentity.PlateBlockEntity;
import io.github.tt432.kitchenkarrot.recipes.recipe.PlateRecipe;
import io.github.tt432.kitchenkarrot.recipes.register.RecipeTypes;
import io.github.tt432.kitchenkarrot.sound.ModSoundEvents;
import io.github.tt432.kitchenkarrot.tag.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author DustW
 **/

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public class PlateBlock extends FacingEntityBlock<PlateBlockEntity> {
    static {
        var part1 = Block.box(1, 1, 1, 16 - 1, 2, 16 - 1);
        var part2 = Block.box(3, 0, 3, 16 - 3, 1, 16 - 3);
        SHAPE = Shapes.or(part1, part2);
    }

    public static final VoxelShape SHAPE;

    public PlateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<PlateBlockEntity> getBlockEntity() {
        return ModBlockEntities.PLATE.get();
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }



    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @NotNull
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        AtomicBoolean success = new AtomicBoolean(false);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity != null) {
            blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(handler -> {
                ItemStack heldItem = player.getItemInHand(hand);
                ItemStack dishItem = handler.getStackInSlot(0);
                if (player.isShiftKeyDown()) {
                    if (heldItem.isEmpty()) {
                        ItemStack stack = new ItemStack(this);
                        blockEntity.saveToItem(stack);
                        setPlate(stack, dishItem);
                        //如果盘子中装有食物，则端起来时会显示"盘装的XXX"
                        if (stack.getOrCreateTag().contains("plate_type") && !dishItem.is(Items.AIR)) {
                            String inputName = dishItem.getDisplayName().getString().replace("[", "").replace("]", "");
                            stack.setHoverName((new TranslatableComponent("info.kitchenkarrot.dished", inputName)).setStyle(Style.EMPTY.withItalic(false)));
                        }
                        player.setItemInHand(hand, stack);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    }
                } else {
                    if (dishItem.isEmpty() && !heldItem.isEmpty()) {
                            if (addToPlate(level, heldItem, handler)) {
                                success.set(true);
                            }
                    } else if (!dishItem.isEmpty()) {
                        if(interactWithDish(dishItem, heldItem, level, player, handler)){
                            success.set(true);
                        }
                    }
                }
            });
        }

        if (success.get()) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    boolean canHoldItem(Level level, IItemHandler handler, ItemStack dishItem){
        Optional<PlateRecipe> recipe = level.getRecipeManager().getAllRecipesFor(RecipeTypes.PLATE.get()).stream()
                        .filter(r ->
                                r.matches(Collections.singletonList(dishItem))
                        ).findFirst();
        return recipe.filter(plateRecipe -> handler.getStackInSlot(0).getCount() < plateRecipe.getMax()).isPresent();
    }

    boolean interactWithDish(ItemStack dishItem, ItemStack heldItem,Level level, Player player, IItemHandler handler){
        AtomicBoolean result = new AtomicBoolean(false);
        if (heldItem.isEmpty() || heldItem.is(ModItemTags.KNIFE_ITEM)){
            if (removeFromPlate(level, player, handler, dishItem, heldItem)) {
                result.set(true);
            }
        } else if (heldItem.sameItem(dishItem) && canHoldItem(level, handler, dishItem)) {
            if (addToPlate(level,heldItem,handler)){
                result.set(true);
            }
        }
        return result.get();
    }

    boolean removeFromPlate(Level level, Player player, IItemHandler handler, ItemStack input, ItemStack heldItem) {
        Optional<PlateRecipe> recipe = level.getRecipeManager()
                .getAllRecipesFor(RecipeTypes.PLATE.get())
                .stream()
                .filter(r ->
                        r.matches(Collections.singletonList(input)) &&
                                r.canCut(heldItem, input)).findFirst();

        AtomicBoolean result = new AtomicBoolean(false);

        recipe.ifPresent(r -> {
            if (giveRecipeResult(level, r, handler)) {
                level.playSound(player, player.getOnPos(), ModSoundEvents.CHOP.get(), player.getSoundSource(), 0.5F, 1F);
                result.set(true);
            }
        });

        if (recipe.isEmpty()) {
            ItemStack item = handler.extractItem(0, 1, false);
            player.getInventory().add(item);


            result.set(true);
        }

        return result.get();
    }

    boolean addToPlate(Level level, ItemStack heldItem, IItemHandler handler) {
        Optional<PlateRecipe> recipe = level.getRecipeManager().getAllRecipesFor(RecipeTypes.PLATE.get())
                .stream().filter(r -> r.matches(Collections.singletonList(heldItem))).findFirst();

        AtomicBoolean result = new AtomicBoolean(false);

        recipe.ifPresent(r -> {
            ItemStack Stack = heldItem.split(1);
            handler.insertItem(0, Stack, false);
            result.set(true);
        });

        return result.get();
    }

    boolean giveRecipeResult(Level level, PlateRecipe recipe, IItemHandler handler) {
        Optional<PlateRecipe> outputRecipe = level.getRecipeManager().getAllRecipesFor(RecipeTypes.PLATE.get())
                .stream().filter(or -> or.matches(Collections.singletonList(recipe.getResultItem()))).findFirst();

        AtomicBoolean result = new AtomicBoolean(false);

        outputRecipe.ifPresent(or -> {
            handler.extractItem(0, 64, false);
            handler.insertItem(0, or.getMaxStack(), false);
            result.set(true);
        });

        return result.get();
    }

    public static void setPlate(ItemStack self, ItemStack content) {
        self.getOrCreateTag().putInt("plate_amount", content.getCount());
        if (content.getItem().getRegistryName() != null) {
            self.getOrCreateTag().putString("plate_type", content.getItem().getRegistryName().toString());
        }
    }

    @Override
    protected void spawnDestroyParticles(Level pLevel, Player pPlayer, BlockPos pPos, BlockState pState) {
        pLevel.levelEvent(pPlayer, 2001, pPos, getId(pState));
    }
}
