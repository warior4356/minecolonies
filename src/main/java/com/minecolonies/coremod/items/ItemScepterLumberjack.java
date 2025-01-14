package com.minecolonies.coremod.items;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.items.IBlockOverlayItem;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingLumberjack;
import com.minecolonies.coremod.entity.ai.citizen.lumberjack.EntityAIWorkLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Lumberjack Scepter Item class. Used to give tasks to Lumberjacks.
 */
public class ItemScepterLumberjack extends AbstractItemMinecolonies implements IBlockOverlayItem
{
    private static final int GREEN_OVERLAY = 0xFF00FF00;
    private static final String NBT_START_POS = Constants.MOD_ID + ":" + "start_pos";
    private static final String NBT_END_POS   = Constants.MOD_ID + ":" + "end_pos";

    /**
     * LumberjackScepter constructor. Sets max stack to 1, like other tools.
     *
     * @param properties the properties.
     */
    public ItemScepterLumberjack(final Properties properties)
    {
        super("scepterlumberjack", properties.stacksTo(1));
    }

    @NotNull
    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getLevel().isClientSide)
        {
            return InteractionResult.FAIL;
        }

        final ItemStack scepter = context.getPlayer().getItemInHand(context.getHand());
        MessageUtils.format(TOOL_LUMBERJACK_SCEPTER_POSITION_B_SET).sendTo(context.getPlayer());
        if (setPosition(scepter.getOrCreateTag(), NBT_START_POS, context.getClickedPos(), context.getPlayer()))
        {
            storeRestrictedArea(context.getPlayer(), scepter.getOrCreateTag(), context.getLevel());
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean canAttackBlock(@NotNull final BlockState state, @NotNull final Level world, @NotNull final BlockPos pos, @NotNull final Player player)
    {
        if (!world.isClientSide)
        {
            final ItemStack tool = player.getMainHandItem();
            MessageUtils.format(TOOL_LUMBERJACK_SCEPTER_POSITION_A_SET).sendTo(player);
            if (setPosition(tool.getOrCreateTag(), NBT_END_POS, pos, player))
            {
                storeRestrictedArea(player, tool.getOrCreateTag(), world);
            }
        }

        return false;
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 3.4028235E38F;
    }

    private void storeRestrictedArea(final Player player, final CompoundTag compound, final Level worldIn)
    {
        final BlockPos startRestriction = BlockPosUtil.read(compound, NBT_START_POS);
        final BlockPos endRestriction = BlockPosUtil.read(compound, NBT_END_POS);

        // Check restricted area isn't too large
        final int minX = Math.min(startRestriction.getX(), endRestriction.getX());
        final int minY = Math.min(startRestriction.getY(), endRestriction.getY());
        final int minZ = Math.min(startRestriction.getZ(), endRestriction.getZ());
        final int maxX = Math.max(startRestriction.getX(), endRestriction.getX());
        final int maxY = Math.max(startRestriction.getY(), endRestriction.getY());
        final int maxZ = Math.max(startRestriction.getZ(), endRestriction.getZ());

        final int distX = maxX - minX;
        final int distY = maxY - minY;
        final int distZ = maxZ - minZ;

        final int volume = distX * distY * distZ;
        final int maxVolume = (int) Math.floor(2 * Math.pow(EntityAIWorkLumberjack.SEARCH_RANGE, 3));

        if (volume > maxVolume)
        {
            MessageUtils.format(TOOL_LUMBERJACK_SCEPTER_AREA_TOO_BIG, volume, maxVolume).sendTo(player);
            return;
        }

        MessageUtils.format(TOOL_LUMBERJACK_SCEPTER_AREA_SET, minX, maxX, minY, maxY, minZ, maxZ, volume, maxVolume).sendTo(player);
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(compound.getInt(TAG_ID), worldIn);
        final BlockPos hutPos = BlockPosUtil.read(compound, TAG_POS);
        final BuildingLumberjack hut = colony.getBuildingManager().getBuilding(hutPos, BuildingLumberjack.class);
        if (hut == null)
        {
            return;
        }

        hut.setRestrictedArea(startRestriction, endRestriction);
    }

    /**
     * Set the position into the compound with the right key.
     * Decide if flux continues or stops.
     * @param compound the set compound.
     * @param key the key to set.
     * @param pos the pos to set for the key.
     * @param player the player entity.
     * @return true if continue.
     */
    private boolean setPosition(final CompoundTag compound, final String key, final BlockPos pos, final Player player)
    {
        if (compound.contains(key))
        {
            if (BlockPosUtil.read(compound, key).equals(pos))
            {
                player.getInventory().removeItemNoUpdate(player.getInventory().selected);
                return false;
            }
        }

        BlockPosUtil.write(compound, key, pos);
        return compound.contains(NBT_END_POS) && compound.contains(NBT_START_POS);
    }

    @NotNull
    @Override
    public List<OverlayBox> getOverlayBoxes(@NotNull final Level world, @NotNull final Player player, @NotNull ItemStack stack)
    {
        final CompoundTag compound = stack.getOrCreateTag();
        final IColonyView colony = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_ID), world.dimension());

        if (colony != null && colony.getBuilding(BlockPosUtil.read(compound, TAG_POS)) instanceof final BuildingLumberjack.View hut)
        {
            final BlockPos startRestriction = compound.contains(NBT_START_POS) ? BlockPosUtil.read(compound, NBT_START_POS) : hut.getStartRestriction();
            final BlockPos endRestriction = compound.contains(NBT_END_POS) ? BlockPosUtil.read(compound, NBT_END_POS) : hut.getEndRestriction();
            final AABB bounds = new AABB(startRestriction, endRestriction.offset(1, 1, 1)).inflate(1);
            // inflate(1) is due to implementation of BlockPosUtil.isInArea

            return Collections.singletonList(new OverlayBox(bounds, GREEN_OVERLAY, 0.02f, true));
        }

        return Collections.emptyList();
    }
}
