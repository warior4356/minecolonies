package com.minecolonies.colony;

import com.minecolonies.MineColonies;
import com.minecolonies.colony.buildings.Building;
import com.minecolonies.colony.buildings.BuildingTownHall;
import com.minecolonies.configuration.Configurations;
import com.minecolonies.entity.EntityCitizen;
import com.minecolonies.entity.EntityWorker;
import com.minecolonies.lib.Constants;
import com.minecolonies.network.messages.ColonyBuildingViewMessage;
import com.minecolonies.network.messages.ColonyViewCitizensMessage;
import com.minecolonies.network.messages.ColonyViewMessage;
import com.minecolonies.tileentities.TileEntityColonyBuilding;
import com.minecolonies.util.ChunkCoordUtils;
import com.minecolonies.util.LanguageHandler;
import com.minecolonies.util.Schematic;
import com.minecolonies.util.Utils;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import java.lang.ref.WeakReference;
import java.util.*;

public class Colony
{
    private final UUID id;
    private final int  dimensionId;

    //  Update Subscriptions
    private Set<EntityPlayerMP>    subscribers      = new HashSet<EntityPlayerMP>();
    private boolean                isDirty          = false;
    private boolean                isCitizensDirty  = false;
    private boolean                isBuildingsDirty = false;
    private List<ChunkCoordinates> removedBuildings = new ArrayList<ChunkCoordinates>();

    //  General Attributes
    private String               name  = "ERROR(Wasn't placed by player)";
    private WeakReference<World> world = new WeakReference<World>(null); // Enforce existence for code simplicity
    private ChunkCoordinates center;

    //  Administration
    private Set<UUID> owners = new HashSet<UUID>();

    //  Buildings
    private BuildingTownHall townhall;
    private Map<ChunkCoordinates, Building> buildings = new HashMap<ChunkCoordinates, Building>();

    //  Citizenry
    private int                                     maxCitizens = Constants.DEFAULTMAXCITIZENS;
    private Map<UUID, WeakReference<EntityCitizen>> citizens    = new HashMap<UUID, WeakReference<EntityCitizen>>();

    //  Workload and Jobs
    private Map<ChunkCoordinates, String> buildingUpgradeMap = new HashMap<ChunkCoordinates, String>();

    final static String TAG_ID           = "id";
    final static String TAG_NAME         = "name";
    final static String TAG_DIMENSION    = "dimension";
    final static String TAG_CENTER       = "center";
    final static String TAG_MAX_CITIZENS = "maxCitizens";
    final static String TAG_OWNERS       = "owners";
    final static String TAG_BUILDINGS_TYPO = "buidings";
    final static String TAG_BUILDINGS    = "buildings";
    final static String TAG_CITIZENS     = "citizens";
    final static String TAG_BUILDING_UPGRADES = "buildingUpgrades";

    /**
     * Constructor for a newly created Colony.
     *
     * @param w The world the colony exists in
     * @param c The center of the colony (location of Town Hall).
     */
    public Colony(World w, ChunkCoordinates c)
    {
        this(UUID.randomUUID(), w.provider.dimensionId);
        center = c;
        world = new WeakReference<World>(w);
    }

    /**
     * Base constructor for a colony.
     *
     * @param uuid The current id for the colony
     * @param dim  The world the colony exists in
     */
    protected Colony(UUID uuid, int dim)
    {
        id = uuid;
        dimensionId = dim;
    }

    /**
     * Call when a Colony must be destroyed; it ensures Citizens are cleaned up properly
     */
    protected void Cleanup()
    {
        for (WeakReference<EntityCitizen> citizen : citizens.values())
        {
            EntityCitizen actualCitizen = (citizen != null) ? citizen.get() : null;
            if (actualCitizen != null)
            {
                actualCitizen.setColony(null);
            }
        }
    }

    /**
     * Load a saved colony
     *
     * @param compound The NBT compound containing the colony's data
     * @return loaded colony
     */
    public static Colony createAndLoadColony(NBTTagCompound compound)
    {
        UUID id = UUID.fromString(compound.getString(TAG_ID));
        int dimensionId = compound.getInteger(TAG_DIMENSION);
        Colony c = new Colony(id, dimensionId);
        c.readFromNBT(compound);
        return c;
    }

    /**
     * Read colony from saved data
     *
     * @param compound
     */
    protected void readFromNBT(NBTTagCompound compound)
    {
        name = compound.getString(TAG_NAME);
        center = ChunkCoordUtils.readFromNBT(compound, TAG_CENTER);

        maxCitizens = compound.getInteger(TAG_MAX_CITIZENS);

        //  Owners
        NBTTagList ownerTagList = compound.getTagList(TAG_OWNERS, NBT.TAG_STRING);
        for (int i = 0; i < ownerTagList.tagCount(); ++i)
        {
            String owner = ownerTagList.getStringTagAt(i);
            owners.add(UUID.fromString(owner));
        }

        //  Buildings
        String buildingsTagName = compound.hasKey(TAG_BUILDINGS_TYPO) ? TAG_BUILDINGS_TYPO : TAG_BUILDINGS;
        NBTTagList buildingTagList =  compound.getTagList(buildingsTagName, NBT.TAG_COMPOUND);
        for (int i = 0; i < buildingTagList.tagCount(); ++i)
        {
            NBTTagCompound buildingCompound = buildingTagList.getCompoundTagAt(i);
            Building b = Building.createFromNBT(this, buildingCompound);
            if (b != null)
            {
                addBuilding(b);
            }
        }

        //  Citizens
        NBTTagList citizenTagList = compound.getTagList(TAG_CITIZENS, NBT.TAG_STRING);
        for (int i = 0; i < citizenTagList.tagCount(); ++i)
        {
            String owner = citizenTagList.getStringTagAt(i);
            citizens.put(UUID.fromString(owner), null);
        }

        //  Workload
        NBTTagList buildingUpgradeTagList = compound.getTagList(TAG_BUILDING_UPGRADES, NBT.TAG_COMPOUND);
        for (int i = 0; i < buildingUpgradeTagList.tagCount(); ++i)
        {
            NBTTagCompound upgrade = buildingUpgradeTagList.getCompoundTagAt(i);
            ChunkCoordinates coords = ChunkCoordUtils.readFromNBT(upgrade, TAG_ID);
            String name = upgrade.getString(TAG_NAME);
            buildingUpgradeMap.put(coords, name);
        }
    }

    /**
     * Write colony to save data
     *
     * @param compound
     */
    public void writeToNBT(NBTTagCompound compound)
    {
        //  Core attributes
        compound.setString(TAG_ID, id.toString());
        compound.setInteger(TAG_DIMENSION, dimensionId);

        //  Basic data
        compound.setString(TAG_NAME, name);
        ChunkCoordUtils.writeToNBT(compound, TAG_CENTER, center);

        compound.setInteger(TAG_MAX_CITIZENS, maxCitizens);

        //  Owners
        NBTTagList ownerTagList = new NBTTagList();
        for (UUID owner : owners)
        {
            ownerTagList.appendTag(new NBTTagString(owner.toString()));
        }
        compound.setTag(TAG_OWNERS, ownerTagList);

        //  Buildings
        NBTTagList buildingTagList = new NBTTagList();
        for (Building b : buildings.values())
        {
            NBTTagCompound buildingCompound = new NBTTagCompound();
            b.writeToNBT(buildingCompound);
            buildingTagList.appendTag(buildingCompound);
        }
        compound.setTag(TAG_BUILDINGS, buildingTagList);

        //  Citizens
        NBTTagList citizenTagList = new NBTTagList();
        for (UUID citizen : citizens.keySet())
        {
            citizenTagList.appendTag(new NBTTagString(citizen.toString()));
        }
        compound.setTag(TAG_CITIZENS, citizenTagList);

        //  Workload
        if (!buildingUpgradeMap.isEmpty())
        {
            NBTTagList buildingUpgradeTagList = new NBTTagList();
            for (Map.Entry<ChunkCoordinates, String> entry : buildingUpgradeMap.entrySet())
            {
                NBTTagCompound upgrade = new NBTTagCompound();
                ChunkCoordUtils.writeToNBT(upgrade, TAG_ID, entry.getKey());
                upgrade.setString(TAG_NAME, entry.getValue());
                buildingUpgradeTagList.appendTag(upgrade);
            }
            compound.setTag(TAG_BUILDING_UPGRADES, buildingUpgradeTagList);
        }
    }

    public UUID getID()
    {
        return id;
    }

    public int getDimensionId() { return dimensionId; }
//    public World getWorld() { return world != null ? world.get() : null; }

    public String getName() { return name; }
    public void setName(String n)
    {
        name = n;
        markDirty();
    }

    public Set<UUID> getOwners() { return Collections.unmodifiableSet(owners); }
    public boolean isOwner(UUID o) { return owners.contains(o); }
    public boolean isOwner(EntityPlayer player) { return owners.contains(player.getUniqueID()); }
    public void addOwner(UUID o)
    {
        owners.add(o);
        markDirty();
    }
    public void removeOwner(UUID o)
    {
        owners.remove(o);
        markDirty();
    }

    public ChunkCoordinates getCenter() { return center; }

    private void markDirty() { isDirty = true; }
    private void markCitizensDirty() { isCitizensDirty = true; }
    public void markBuildingsDirty() { isBuildingsDirty = true; }

    /**
     * Determine if a given chunk coordinate is considered to be within the colony's bounds
     *
     * @param coord
     * @return
     */
    public boolean isCoordInColony(World w, ChunkCoordinates coord)
    {
        return isCoordInColony(w, coord.posX, coord.posY, coord.posZ);
    }

    public boolean isCoordInColony(World w, int x, int y, int z)
    {
        //  Perform a 2D distance calculation, so pass center.posY as the Y
        return w == world.get() &&
                center.getDistanceSquared(x, center.posY, z) <= Utils.square(Configurations.workingRangeTownhall);
    }

    public float getDistanceSquared(ChunkCoordinates coord)
    {
        return getDistanceSquared(coord.posX, coord.posY, coord.posZ);
    }

    public float getDistanceSquared(int posX, int posY, int posZ)
    {
        //  Perform a 2D distance calculation, so pass center.posY as the Y
        return center.getDistanceSquared(posX, center.posY, posZ);
    }

    /**
     * When the Colony's world is loaded, associate with it
     *
     * @param w
     */
    public void onWorldLoad(World w)
    {
        if (w.provider.dimensionId == dimensionId)
        {
            world = new WeakReference<World>(w);
        }
    }

    public void onWorldUnload(World w)
    {
        //  Nothing for now
    }

    /**
     * Any per-server-tick logic should be performed here
     *
     * @param event
     */
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        for (Building b : buildings.values())
        {
            b.onServerTick(event);
        }

        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        //  Recompute subscribers every frame (for now)
        //  Subscribers = Owners + Players within (double working town hall range)
        Set<EntityPlayerMP> oldSubscribers = subscribers;
        subscribers = new HashSet<EntityPlayerMP>();

        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList)
        {
            if (o instanceof EntityPlayerMP)
            {
                EntityPlayerMP player = (EntityPlayerMP)o;
                if (owners.contains(player.getUniqueID()))
                {
                    subscribers.add(player);
                }
            }
        }

        World w = world.get();
        if (w != null)
        {
            for (Object o : w.playerEntities)
            {
                if (o instanceof EntityPlayerMP)
                {
                    EntityPlayerMP player = (EntityPlayerMP)o;

                    if (subscribers.contains(player))
                    {
                        //  Already a subscriber
                        continue;
                    }

                    double distance = player.getDistanceSq(center.posX, center.posY, center.posZ);
                    if (distance < Utils.square(Configurations.workingRangeTownhall + 16))
                    {
                        //  Players become subscribers if they come within 16 blocks of the edge of the colony
                        subscribers.add(player);
                    }
                    else if (oldSubscribers.contains(player) &&
                            distance < Utils.square(Configurations.workingRangeTownhall * 2))
                    {
                        //  Players remain subscribers while they remain within double the colony's radius
                        subscribers.add(player);
                    }
                }
            }
        }

        //  Determine if any new subscribers were added this pass
        boolean hasNewSubscribers = false;
        for (EntityPlayerMP player : subscribers)
        {
            if (!oldSubscribers.contains(player))
            {
                hasNewSubscribers = true;
                break;
            }
        }

        //  Send each type of update packet as appropriate:
        //      - To Subscribers if the data changes
        //      - To New Subscribers even if it hasn't changed

        //  ColonyView
        if (isDirty || hasNewSubscribers)
        {
            NBTTagCompound compound = new NBTTagCompound();
            ColonyView.createNetworkData(this, compound);

            for (EntityPlayerMP player : subscribers)
            {
                boolean isNewSubscriber = !oldSubscribers.contains(player);
                if (isDirty || isNewSubscriber)
                {
                    MineColonies.network.sendTo(new ColonyViewMessage(id, compound, isNewSubscriber), player);
                }
            }
        }

        //  Citizens
        if (isCitizensDirty || hasNewSubscribers)
        {
            NBTTagCompound compound = new NBTTagCompound();
            ColonyView.createCitizenNetworkData(this, compound);

            for (EntityPlayerMP player : subscribers)
            {
                if (isCitizensDirty || !oldSubscribers.contains(player))
                {
                    MineColonies.network.sendTo(new ColonyViewCitizensMessage(id, compound), player);
                }
            }
        }

        //  Buildings
        if (isBuildingsDirty || hasNewSubscribers)
        {
            for (Building b : buildings.values())
            {
                if (b.isDirty() || hasNewSubscribers)
                {
                    NBTTagCompound compound = new NBTTagCompound();
                    b.createViewNetworkData(compound);

                    for (EntityPlayerMP player : subscribers)
                    {
                        if (b.isDirty() || !oldSubscribers.contains(player))
                        {
                            MineColonies.network.sendTo(new ColonyBuildingViewMessage(id, b.getID(), compound), player);
                        }
                    }
                }
            }
        }

        removedBuildings.clear();   //  We will have set these

//        for (EntityPlayerMP oldPlayers : oldSubscribers)
//        {
//            if (!subscribers.contains(oldPlayers))
//            {
//                //  This player should no longer subscribe
//            }
//        }

        isDirty = false;
        isCitizensDirty = false;
        isBuildingsDirty = false;
        for (Building b : buildings.values())
        {
            b.clearDirty();
        }
    }

    /**
     * Any per-world-tick logic should be performed here
     *
     * @param event
     */
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        //  Cleanup disappeared citizens
        //  It would be really nice if we didn't have to do this... but Citizens can disappear without dying!
        if (event.phase == TickEvent.Phase.START &&
                world.get() != null)
        {
            World worldObj = world.get();
            boolean playerIsCloseEnoughForEntityCleanup = false;
            boolean allColonyChunksLoaded = false;

            for (Object o : worldObj.playerEntities)
            {
                if (o instanceof EntityPlayerMP)
                {
                    EntityPlayerMP player = (EntityPlayerMP)o;

                    if (player.getPlayerCoordinates().getDistanceSquaredToChunkCoordinates(getCenter()) <= (16 * 16))
                    {
                        playerIsCloseEnoughForEntityCleanup = true;
                        break;
                    }
                }
            }

            if (playerIsCloseEnoughForEntityCleanup)
            {
                //  If we have a player close enough to town hall, check if all the colony chunks are loaded

                //  Assume all chunks are loaded until we find one that isn't
                allColonyChunksLoaded = true;

                int distanceFromCenter = Configurations.workingRangeTownhall + 48 /* 3 chunks */ + 15 /* round up a chunk */;
                for (int x = getCenter().posX - distanceFromCenter, endX = getCenter().posX + distanceFromCenter; x <= endX; x += 16)
                {
                    for (int z = getCenter().posZ - distanceFromCenter, endZ = getCenter().posZ + distanceFromCenter; z <= endZ; z += 16)
                    {
                        if (!worldObj.blockExists(x, 128, z))
                        {
                            allColonyChunksLoaded = false;
                            break;
                        }
                    }

                    if (!allColonyChunksLoaded)
                    {
                        break;
                    }
                }
            }

            if (allColonyChunksLoaded)
            {
                //  All chunks within a good range of the colony should be loaded, so all citizens should be loaded
                //  If we don't have any references to them, destroy the citizen

                for (Iterator<Map.Entry<UUID, WeakReference<EntityCitizen>>> it = citizens.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry<UUID, WeakReference<EntityCitizen>> entry = it.next();
                    if (entry.getValue() == null || entry.getValue().get() == null)
                    {
                        MineColonies.logger.warn(String.format("Citizen '%s' has gone AWOL, removing them!", entry.getKey().toString()));

                        for (Building b : buildings.values())
                        {
                            b.removeCitizen(entry.getKey());
                        }

                        it.remove();
                        isCitizensDirty = true;
                    }
                }
            }
        }

        //  Spawn Citizens
        if (event.phase == TickEvent.Phase.END)
        {
            World worldObj = world.get();
            if (townhall != null &&
                    citizens.size() < maxCitizens &&
                    worldObj != null)
            {
                int respawnInterval = Configurations.citizenRespawnInterval * 20;
                respawnInterval -= (60 * townhall.getBuildingLevel());

                if (worldObj.getWorldInfo().getWorldTime() % respawnInterval == 0)
                {
                    spawnCitizen();
                }
            }
        }

        //  Tick Buildings
        List<Building> cleanupBuildings = null;

        for (Building b : buildings.values())
        {
            World w = world.get();
            ChunkCoordinates loc = b.getLocation();
            if (w != null &&
                    w.blockExists(loc.posX, loc.posY, loc.posZ) &&
                    !Building.buildingMatchesBlock(b, w.getBlock(loc.posX, loc.posY, loc.posZ)))
            {
                //  Sanity cleanup
                if (cleanupBuildings == null)
                {
                    cleanupBuildings = new ArrayList<Building>();
                }

                cleanupBuildings.add(b);
                continue;
            }

            b.onWorldTick(event);
        }

        if (cleanupBuildings != null)
        {
            for (Building b : cleanupBuildings)
            {
                b.destroy();
            }
        }
    }

    private void spawnCitizen()
    {
        World worldObj = world.get();
        int xCoord = center.posX, yCoord = center.posY, zCoord = center.posZ;

        if (!worldObj.blockExists(center.posX, center.posY, center.posZ))
        {
            //  Chunk with TownHall Block is not loaded
            return;
        }

        ChunkCoordinates spawnPoint = Utils.scanForBlockNearPoint(worldObj, Blocks.air, xCoord, yCoord, zCoord, 1, 0, 1);
        if(spawnPoint == null)
        {
            spawnPoint = Utils.scanForBlockNearPoint(worldObj, Blocks.snow_layer, xCoord, yCoord, zCoord, 1, 0, 1);
        }

        if(spawnPoint != null)
        {
            EntityCitizen citizen = new EntityCitizen(worldObj);
            citizen.setColony(this);
            citizen.setPosition(spawnPoint.posX, spawnPoint.posY, spawnPoint.posZ);
            worldObj.spawnEntityInWorld(citizen);

            citizens.put(citizen.getUniqueID(), new WeakReference<EntityCitizen>(citizen));
            markCitizensDirty();

            if(getMaxCitizens() == getCitizens().size())
            {
                LanguageHandler.sendPlayersLocalizedMessage(Utils.getPlayersFromUUID(worldObj, getOwners()), "tile.blockHutTownhall.messageMaxSize");
            }
        }

    }

    /*
     *
     * BUILDINGS
     *
     */

    public Map<ChunkCoordinates, Building> getBuildings()
    {
        return Collections.unmodifiableMap(buildings);
    }

    public BuildingTownHall getTownhall()
    {
        return townhall;
    }

    public List<ChunkCoordinates> getRemovedBuildings() { return Collections.unmodifiableList(removedBuildings); }

    /**
     * Get building in Colony by ID
     *
     * @param buildingId
     * @return
     */
    public Building getBuilding(ChunkCoordinates buildingId)
    {
        return buildings.get(buildingId);
    }

    /**
     * Add a Building to the Colony
     *
     * @param building
     */
    private void addBuilding(Building building)
    {
        buildings.put(building.getLocation(), building);
        building.markDirty();

        if (building instanceof BuildingTownHall)
        {
            //  Limit 1 town hall
            if (townhall == null)
            {
                townhall = (BuildingTownHall) building;
            }
        }
    }

    public Building addNewBuilding(TileEntityColonyBuilding tileEntity)
    {
        tileEntity.setColony(this);

        Building building = Building.create(this, tileEntity);
        if (building != null)
        {
            addBuilding(building);
            tileEntity.setBuilding(building);
        }
        return building;
    }

    /**
     * Remove a Building from the Colony (when it is destroyed)
     *
     * @param building
     */
    public void removeBuilding(Building building)
    {
        if (buildings.remove(building.getLocation()) != null)
        {
            removedBuildings.add(building.getLocation());
            markDirty();
        }
    }

    /*
     *
     * CITIZENS
     *
     */

    public int getMaxCitizens() { return maxCitizens; }
    //public void setMaxCitizens();

    public Map<UUID, WeakReference<EntityCitizen>> getCitizens() { return Collections.unmodifiableMap(citizens); }

    public List<EntityCitizen> getActiveCitizens()
    {
        List<EntityCitizen> activeCitizens = new ArrayList<EntityCitizen>();

        for (WeakReference<EntityCitizen> citizen : citizens.values())
        {
            EntityCitizen actualCitizen = (citizen != null) ? citizen.get() : null;
            if (actualCitizen != null)
            {
                activeCitizens.add(actualCitizen);
            }
        }

        return activeCitizens;
    }

    public boolean isCitizen(UUID c) { return citizens.containsKey(c); }

    public void removeCitizen(EntityCitizen citizen)
    {
        removeCitizen(citizen.getUniqueID());
    }

    public void removeCitizen(UUID citizenId)
    {
        citizens.remove(citizenId);
        markCitizensDirty();

        for (Building building : buildings.values())
        {
            building.removeCitizen(citizenId);
        }
    }

    public boolean registerCitizen(EntityCitizen citizen)
    {
        if (!citizens.containsKey(citizen.getUniqueID()))
        {
            return false;
        }

        citizens.put(citizen.getUniqueID(), new WeakReference<EntityCitizen>(citizen));
        markCitizensDirty();
        return true;
    }

//    public void replaceCitizen(EntityCitizen oldCitizen, EntityCitizen newCitizen)
//    {
//        if (citizens.containsKey(oldCitizen.getUniqueID()))
//        {
//            citizens.remove(oldCitizen.getUniqueID());
//            citizens.put(newCitizen.getUniqueID(), new WeakReference<EntityCitizen>(newCitizen));
//            markDirty();
//        }
//        else
//        {
//            MineColonies.logger.error(String.format("Colony.replaceCitizen() - Citizen %s is not a member of the Colony.", oldCitizen.getUniqueID().toString()));
//        }
//    }

    /**
     * Get citizen in Colony by ID
     * @param citizenId
     * @return
     */
    public EntityCitizen getCitizen(UUID citizenId)
    {
        WeakReference<EntityCitizen> citizen = citizens.get(citizenId);
        return citizen != null ? citizen.get() : null;
    }

    /**
     * Get an idle citizen
     *
     * @return Citizen with no current job
     */
    public EntityCitizen getIdleCitizen()
    {
        for (WeakReference<EntityCitizen> citizenRef : citizens.values())
        {
            EntityCitizen citizen = (citizenRef != null) ? citizenRef.get() : null;
            //if (citizen != null && citizen.getColonyJob() == null)
            if (citizen != null && citizen.getWorkBuilding() == null)
            {
                return citizen;
            }
        }

        return null;
    }

    public void addBuildingForUpgrade(Building building, int level)
    {
        String upgradeName = building.getSchematicName() + level;
        buildingUpgradeMap.put(building.getID(), upgradeName);
    }

    public void removeBuildingForUpgrade(ChunkCoordinates pos)
    {
        buildingUpgradeMap.remove(pos);
    }

    public Map<ChunkCoordinates, String> getBuildingUpgrades()
    {
        return buildingUpgradeMap;
    }

    public List<ChunkCoordinates> getDeliverymanRequired()
    {
        List<ChunkCoordinates> deliverymanRequired = new ArrayList<ChunkCoordinates>();

        for (WeakReference<EntityCitizen> citizenRef : citizens.values())
        {
            EntityCitizen citizen = (citizenRef != null) ? citizenRef.get() : null;
            if (citizen != null && citizen instanceof EntityWorker)
            {
                EntityWorker worker = (EntityWorker)citizen;

                if (worker.getWorkBuilding() != null && !worker.hasItemsNeeded())
                {
                    deliverymanRequired.add(worker.getWorkBuilding().getLocation());
                }
            }
        }

        return deliverymanRequired;
    }
}
