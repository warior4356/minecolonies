package com.minecolonies.coremod.generation.defaults.workers;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.coremod.generation.CustomRecipeProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.BuildingConstants.MODULE_CRAFTING;
import static com.minecolonies.api.util.constant.BuildingConstants.MODULE_SMELTING;

/**
 * Datagen for Baker
 */
public class DefaultBakerCraftingProvider extends CustomRecipeProvider
{
    private static final String BAKER = ModJobs.BAKER_ID.getPath();

    public DefaultBakerCraftingProvider(@NotNull final DataGenerator generatorIn)
    {
        super(generatorIn);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "DefaultBakerCraftingProvider";
    }

    @Override
    protected void registerRecipes(@NotNull final Consumer<FinishedRecipe> consumer)
    {
        final ItemStack waterBottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "water_bottle")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.GLASS_BOTTLE))))
                .result(waterBottle)
                .minBuildingLevel(3)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "bread_dough")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 3))))
                .result(new ItemStack(ModItems.breadDough))
                .maxBuildingLevel(2)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "bread_dough3")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT)), new ItemStorage(waterBottle)))
                .result(new ItemStack(ModItems.breadDough, 2))
                .minBuildingLevel(3)
                .lootTable(DefaultRecipeLootProvider.LOOT_TABLE_BOTTLE)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_SMELTING, "bread")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.breadDough))))
                .result(new ItemStack(Items.BREAD))
                .intermediate(Blocks.FURNACE)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "sugary_bread")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 8)),
                        new ItemStorage(new ItemStack(Items.HONEY_BOTTLE))))
                .result(new ItemStack(ModItems.sugaryBread, 4))
                .minBuildingLevel(3)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "milky_bread")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 8)),
                        new ItemStorage(new ItemStack(Items.MILK_BUCKET))))
                .result(new ItemStack(ModItems.milkyBread, 4))
                .minBuildingLevel(4)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "golden_bread")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 8)),
                        new ItemStorage(new ItemStack(Items.GOLD_INGOT))))
                .result(new ItemStack(ModItems.goldenBread, 4))
                .minBuildingLevel(5)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "chorus_bread")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 8)),
                        new ItemStorage(new ItemStack(Items.CHORUS_FRUIT))))
                .result(new ItemStack(ModItems.chorusBread, 4))
                .minResearchId(ResearchConstants.THE_END)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "cake_batter")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 3)),
                        new ItemStorage(new ItemStack(Items.MILK_BUCKET, 3)),
                        new ItemStorage(new ItemStack(Items.SUGAR, 2)),
                        new ItemStorage(new ItemStack(Items.EGG))))
                .result(new ItemStack(ModItems.cakeBatter))
                .minBuildingLevel(2)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_SMELTING, "cake")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.cakeBatter))))
                .result(new ItemStack(Items.CAKE))
                .minBuildingLevel(2)
                .intermediate(Blocks.FURNACE)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "raw_pumpkin_pie")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.PUMPKIN)),
                        new ItemStorage(new ItemStack(Items.SUGAR)),
                        new ItemStorage(new ItemStack(Items.EGG))))
                .result(new ItemStack(ModItems.rawPumpkinPie))
                .minBuildingLevel(3)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_SMELTING, "pumpkin_pie")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.rawPumpkinPie))))
                .result(new ItemStack(Items.PUMPKIN_PIE))
                .minBuildingLevel(3)
                .intermediate(Blocks.FURNACE)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_CRAFTING, "cookie_dough")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.WHEAT, 2)),
                        new ItemStorage(new ItemStack(Items.COCOA_BEANS, 2))))
                .result(new ItemStack(ModItems.cookieDough, 8))
                .minBuildingLevel(4)
                .showTooltip(true)
                .build(consumer);

        CustomRecipeBuilder.create(BAKER, MODULE_SMELTING, "cookie")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.cookieDough))))
                .result(new ItemStack(Items.COOKIE))
                .minBuildingLevel(4)
                .intermediate(Blocks.FURNACE)
                .build(consumer);
    }
}
