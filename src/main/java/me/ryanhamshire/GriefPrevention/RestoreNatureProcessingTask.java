/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

//non-main-thread task which processes world data to repair the unnatural
//after processing is complete, creates a main thread task to make the necessary changes to the world
class RestoreNatureProcessingTask implements Runnable
{

    // Definitions of biomes with particularly dense log distribution. These biomes will not have logs reduced.
    private static final Set<NamespacedKey> DENSE_LOG_BIOMES = Set.of(
            NamespacedKey.minecraft("jungle"),
            NamespacedKey.minecraft("bamboo_jungle"),
            // Variants for versions < 1.18
            NamespacedKey.minecraft("modified_jungle"),
            NamespacedKey.minecraft("jungle_hills"),
            NamespacedKey.minecraft("bamboo_jungle_hills")
    );

    // Definitions of biomes where sand covers surfaces instead of grass.
    private static final Set<NamespacedKey> SAND_SOIL_BIOMES = Set.of(
            NamespacedKey.minecraft("snowy_beach"),
            NamespacedKey.minecraft("beach"),
            NamespacedKey.minecraft("desert"),
            // Variants for versions < 1.18
            NamespacedKey.minecraft("desert_hills"),
            NamespacedKey.minecraft("desert_lakes")
    );

    //world information captured from the main thread
    //will be updated and sent back to main thread to be applied to the world
    private final BlockSnapshot[][][] snapshots;

    //other information collected from the main thread.
    //not to be updated, only to be passed back to main thread to provide some context about the operation
    private int miny;
    private final Environment environment;
    private final Location lesserBoundaryCorner;
    private final Location greaterBoundaryCorner;
    private final Player player;            //absolutely must not be accessed.  not thread safe.
    private final Biome biome;
    private final boolean creativeMode;
    private final int seaLevel;
    private final boolean aggressiveMode;

    //two lists of materials
    private final Set<Material> notAllowedToHang;    //natural blocks which don't naturally hang in their air
    private final Set<Material> playerBlocks;        //a "complete" list of player-placed blocks.  MUST BE MAINTAINED as patches introduce more


    public RestoreNatureProcessingTask(BlockSnapshot[][][] snapshots, int miny, Environment environment, Biome biome, Location lesserBoundaryCorner, Location greaterBoundaryCorner, int seaLevel, boolean aggressiveMode, boolean creativeMode, Player player)
    {
        this.snapshots = snapshots;
        this.miny = miny;
        if (this.miny < 0) this.miny = 0;
        this.environment = environment;
        this.lesserBoundaryCorner = lesserBoundaryCorner;
        this.greaterBoundaryCorner = greaterBoundaryCorner;
        this.biome = biome;
        this.seaLevel = seaLevel;
        this.aggressiveMode = aggressiveMode;
        this.player = player;
        this.creativeMode = creativeMode;

        this.notAllowedToHang = new HashSet<>();
        this.notAllowedToHang.add(Material.DIRT);
        this.notAllowedToHang.add(Material.GRASS_BLOCK);
        this.notAllowedToHang.add(Material.SNOW);
        this.notAllowedToHang.add(Material.OAK_LOG);
        this.notAllowedToHang.add(Material.SPRUCE_LOG);
        this.notAllowedToHang.add(Material.BIRCH_LOG);
        this.notAllowedToHang.add(Material.JUNGLE_LOG);
        this.notAllowedToHang.add(Material.ACACIA_LOG);
        this.notAllowedToHang.add(Material.DARK_OAK_LOG);

        if (this.aggressiveMode)
        {
            this.notAllowedToHang.add(Material.STONE);
        }

        this.playerBlocks = new HashSet<>();
        this.playerBlocks.addAll(RestoreNatureProcessingTask.getPlayerBlocks(this.environment, this.biome));

        //in aggressive or creative world mode, also treat these blocks as user placed, to be removed
        //this is helpful in the few cases where griefers intentionally use natural blocks to grief,
        //like a single-block tower of iron ore or a giant penis constructed with melons
        if (this.aggressiveMode || this.creativeMode)
        {
            this.playerBlocks.add(Material.IRON_ORE);
            this.playerBlocks.add(Material.GOLD_ORE);
            this.playerBlocks.add(Material.DIAMOND_ORE);
            this.playerBlocks.add(Material.MELON);
            this.playerBlocks.add(Material.MELON_STEM);
            this.playerBlocks.add(Material.BEDROCK);
            this.playerBlocks.add(Material.COAL_ORE);
            this.playerBlocks.add(Material.PUMPKIN);
            this.playerBlocks.add(Material.PUMPKIN_STEM);
        }

        if (this.aggressiveMode)
        {
            this.playerBlocks.add(Material.OAK_LEAVES);
            this.playerBlocks.add(Material.SPRUCE_LEAVES);
            this.playerBlocks.add(Material.BIRCH_LEAVES);
            this.playerBlocks.add(Material.JUNGLE_LEAVES);
            this.playerBlocks.add(Material.ACACIA_LEAVES);
            this.playerBlocks.add(Material.DARK_OAK_LEAVES);
            this.playerBlocks.add(Material.OAK_LOG);
            this.playerBlocks.add(Material.SPRUCE_LOG);
            this.playerBlocks.add(Material.BIRCH_LOG);
            this.playerBlocks.add(Material.JUNGLE_LOG);
            this.playerBlocks.add(Material.ACACIA_LOG);
            this.playerBlocks.add(Material.DARK_OAK_LOG);
            this.playerBlocks.add(Material.VINE);
        }
    }

    @Override
    public void run()
    {
        //order is important!

        //remove sandstone which appears to be unnatural
        this.removeSandstone();

        //remove any blocks which are definitely player placed
        this.removePlayerBlocks();

        //reduce large outcroppings of stone, sandstone
        this.reduceStone();

        //reduce logs, except in jungle biomes
        this.reduceLogs();

        //remove natural blocks which are unnaturally hanging in the air
        this.removeHanging();

        //remove natural blocks which are unnaturally stacked high
        this.removeWallsAndTowers();

        //fill unnatural thin trenches and single-block potholes
        this.fillHolesAndTrenches();

        //fill water depressions and fix unnatural surface ripples
        //this.fixWater();

        //remove water/lava above sea level
        this.removeDumpedFluids();

        //cover surface stone and gravel with sand or grass, as the biome requires
        this.coverSurfaceStone();

        //remove any player-placed leaves
        ///this.removePlayerLeaves();

        //schedule main thread task to apply the result to the world
        RestoreNatureExecutionTask task = new RestoreNatureExecutionTask(this.snapshots, this.miny, this.lesserBoundaryCorner, this.greaterBoundaryCorner, this.player);
        GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task);
    }


    private void removePlayerLeaves()
    {
        if (this.seaLevel < 1) return;

        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = this.seaLevel - 1; y < snapshots[0].length; y++)
                {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (Tag.LEAVES.isTagged(block.typeId) && ((Leaves) block.data).isPersistent())
                    {
                        block.typeId = Material.AIR;
                    }
                }
            }
        }
    }

    //converts sandstone adjacent to sand to sand, and any other sandstone to air

    private void removeSandstone()
    {
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = snapshots[0].length - 2; y > miny; y--)
                {
                    if (snapshots[x][y][z].typeId != Material.SANDSTONE) continue;

                    BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                    BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                    BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                    BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                    BlockSnapshot underBlock = this.snapshots[x][y - 1][z];
                    BlockSnapshot aboveBlock = this.snapshots[x][y + 1][z];

                    //skip blocks which may cause a cave-in
                    if (aboveBlock.typeId == Material.SAND && underBlock.typeId == Material.AIR) continue;

                    //count adjacent non-air/non-leaf blocks
                    if (leftBlock.typeId == Material.SAND ||
                            rightBlock.typeId == Material.SAND ||
                            upBlock.typeId == Material.SAND ||
                            downBlock.typeId == Material.SAND ||
                            aboveBlock.typeId == Material.SAND ||
                            underBlock.typeId == Material.SAND)
                    {
                        snapshots[x][y][z].typeId = Material.SAND;
                    }
                    else
                    {
                        snapshots[x][y][z].typeId = Material.AIR;
                    }
                }
            }
        }
    }


    private void reduceStone()
    {
        if (this.seaLevel < 1) return;

        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                int thisy = this.highestY(x, z, true);

                while (thisy > this.seaLevel - 1 && (this.snapshots[x][thisy][z].typeId == Material.STONE || this.snapshots[x][thisy][z].typeId == Material.SANDSTONE))
                {
                    BlockSnapshot leftBlock = this.snapshots[x + 1][thisy][z];
                    BlockSnapshot rightBlock = this.snapshots[x - 1][thisy][z];
                    BlockSnapshot upBlock = this.snapshots[x][thisy][z + 1];
                    BlockSnapshot downBlock = this.snapshots[x][thisy][z - 1];

                    //count adjacent non-air/non-leaf blocks
                    byte adjacentBlockCount = 0;
                    if (leftBlock.typeId != Material.AIR && !Tag.LEAVES.isTagged(leftBlock.typeId) && leftBlock.typeId != Material.VINE)
                    {
                        adjacentBlockCount++;
                    }
                    if (rightBlock.typeId != Material.AIR && !Tag.LEAVES.isTagged(rightBlock.typeId) && rightBlock.typeId != Material.VINE)
                    {
                        adjacentBlockCount++;
                    }
                    if (downBlock.typeId != Material.AIR && !Tag.LEAVES.isTagged(downBlock.typeId) && downBlock.typeId != Material.VINE)
                    {
                        adjacentBlockCount++;
                    }
                    if (upBlock.typeId != Material.AIR && !Tag.LEAVES.isTagged(upBlock.typeId) && upBlock.typeId != Material.VINE)
                    {
                        adjacentBlockCount++;
                    }

                    if (adjacentBlockCount < 3)
                    {
                        this.snapshots[x][thisy][z].typeId = Material.AIR;
                    }

                    thisy--;
                }
            }
        }
    }


    private void reduceLogs()
    {
        if (this.seaLevel < 1) return;

        boolean jungleBiome = DENSE_LOG_BIOMES.contains(this.biome.getKey());

        //scan all blocks above sea level
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = this.seaLevel - 1; y < snapshots[0].length; y++)
                {
                    BlockSnapshot block = snapshots[x][y][z];

                    //skip non-logs
                    if (!Tag.LOGS.isTagged(block.typeId)) continue;

                    //if in jungle biome, skip jungle logs
                    if (jungleBiome && block.typeId == Material.JUNGLE_LOG) continue;

                    //examine adjacent blocks for logs
                    BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                    BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                    BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                    BlockSnapshot downBlock = this.snapshots[x][y][z - 1];

                    //if any, remove the log
                    if (Tag.LOGS.isTagged(leftBlock.typeId) || Tag.LOGS.isTagged(rightBlock.typeId) || Tag.LOGS.isTagged(upBlock.typeId) || Tag.LOGS.isTagged(downBlock.typeId))
                    {
                        this.snapshots[x][y][z].typeId = Material.AIR;
                    }
                }
            }
        }
    }


    private void removePlayerBlocks()
    {
        int miny = this.miny;
        if (miny < 1) miny = 1;

        //remove all player blocks
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = miny; y < snapshots[0].length - 1; y++)
                {
                    BlockSnapshot block = snapshots[x][y][z];

                    if (this.playerBlocks.contains(block.typeId))
                    {
                        block.typeId = Material.AIR;
                    }
                }
            }
        }
    }


    private void removeHanging()
    {
        int miny = this.miny;
        if (miny < 1) miny = 1;

        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = miny; y < snapshots[0].length - 1; y++)
                {
                    BlockSnapshot block = snapshots[x][y][z];
                    BlockSnapshot underBlock = snapshots[x][y - 1][z];

                    if (underBlock.typeId == Material.AIR || underBlock.typeId == Material.WATER || Tag.LEAVES.isTagged(underBlock.typeId))
                    {
                        if (this.notAllowedToHang.contains(block.typeId))
                        {
                            block.typeId = Material.AIR;
                        }
                    }
                }
            }
        }
    }


    private void removeWallsAndTowers()
    {
        Material[] excludedBlocksArray = new Material[]
                {
                        Material.CACTUS,
                        null, // will become grass (see below)
                        Material.RED_MUSHROOM,
                        Material.BROWN_MUSHROOM,
                        Material.DEAD_BUSH,
                        Material.DANDELION,
                        Material.POPPY,
                        Material.ALLIUM,
                        Material.BLUE_ORCHID,
                        Material.AZURE_BLUET,
                        Material.RED_TULIP,
                        Material.ORANGE_TULIP,
                        Material.WHITE_TULIP,
                        Material.PINK_TULIP,
                        Material.OXEYE_DAISY,
                        Material.SUGAR_CANE,
                        Material.VINE,
                        Material.PUMPKIN,
                        Material.LILY_PAD
                };

        // in Minecraft 1.20.3 GRASS was renamed to SHORT_GRASS
        // This will support both before/after the change
        try{
            excludedBlocksArray[1] = Material.valueOf("GRASS");
        } catch(IllegalArgumentException e){
            excludedBlocksArray[1] = Material.valueOf("SHORT_GRASS");
        }

        ArrayList<Material> excludedBlocks = new ArrayList<>(Arrays.asList(excludedBlocksArray));

        excludedBlocks.addAll(Tag.SAPLINGS.getValues());
        excludedBlocks.addAll(Tag.LEAVES.getValues());

        boolean changed;
        do
        {
            changed = false;
            for (int x = 1; x < snapshots.length - 1; x++)
            {
                for (int z = 1; z < snapshots[0][0].length - 1; z++)
                {
                    int thisy = this.highestY(x, z, false);
                    if (excludedBlocks.contains(this.snapshots[x][thisy][z].typeId)) continue;

                    int righty = this.highestY(x + 1, z, false);
                    int lefty = this.highestY(x - 1, z, false);
                    while (lefty < thisy && righty < thisy)
                    {
                        this.snapshots[x][thisy--][z].typeId = Material.AIR;
                        changed = true;
                    }

                    int upy = this.highestY(x, z + 1, false);
                    int downy = this.highestY(x, z - 1, false);
                    while (upy < thisy && downy < thisy)
                    {
                        this.snapshots[x][thisy--][z].typeId = Material.AIR;
                        changed = true;
                    }
                }
            }
        } while (changed);
    }


    private void coverSurfaceStone()
    {
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                int y = this.highestY(x, z, true);
                BlockSnapshot block = snapshots[x][y][z];

                if (block.typeId == Material.STONE || block.typeId == Material.GRAVEL || block.typeId == Material.FARMLAND || block.typeId == Material.DIRT || block.typeId == Material.SANDSTONE)
                {
                    if (SAND_SOIL_BIOMES.contains(this.biome.getKey()))
                    {
                        this.snapshots[x][y][z].typeId = Material.SAND;
                    }
                    else
                    {
                        this.snapshots[x][y][z].typeId = Material.GRASS_BLOCK;
                    }
                }
            }
        }
    }


    private void fillHolesAndTrenches()
    {
        ArrayList<Material> fillableBlocks = new ArrayList<>();
        fillableBlocks.add(Material.AIR);
        fillableBlocks.add(Material.WATER);
        fillableBlocks.add(Material.LAVA);



        ArrayList<Material> notSuitableForFillBlocks = new ArrayList<>();
        notSuitableForFillBlocks.add(Material.CACTUS);
        notSuitableForFillBlocks.add(Material.WATER);
        notSuitableForFillBlocks.add(Material.LAVA);
        notSuitableForFillBlocks.addAll(Tag.LOGS.getValues());

        // in Minecraft 1.20.3 GRASS was renamed to SHORT_GRASS
        // This will support both before/after the change
        try{
            fillableBlocks.add(Material.valueOf("GRASS"));
            notSuitableForFillBlocks.add(Material.valueOf("GRASS"));
        } catch(IllegalArgumentException e){
            fillableBlocks.add(Material.valueOf("SHORT_GRASS"));
            notSuitableForFillBlocks.add(Material.valueOf("SHORT_GRASS"));
        }

        boolean changed;
        do
        {
            changed = false;
            for (int x = 1; x < snapshots.length - 1; x++)
            {
                for (int z = 1; z < snapshots[0][0].length - 1; z++)
                {
                    for (int y = 0; y < snapshots[0].length - 1; y++)
                    {
                        BlockSnapshot block = this.snapshots[x][y][z];
                        if (!fillableBlocks.contains(block.typeId)) continue;

                        BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                        BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];

                        if (!fillableBlocks.contains(leftBlock.typeId) && !fillableBlocks.contains(rightBlock.typeId))
                        {
                            if (!notSuitableForFillBlocks.contains(rightBlock.typeId))
                            {
                                block.typeId = rightBlock.typeId;
                                changed = true;
                            }
                        }

                        BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                        BlockSnapshot downBlock = this.snapshots[x][y][z - 1];

                        if (!fillableBlocks.contains(upBlock.typeId) && !fillableBlocks.contains(downBlock.typeId))
                        {
                            if (!notSuitableForFillBlocks.contains(downBlock.typeId))
                            {
                                block.typeId = downBlock.typeId;
                                changed = true;
                            }
                        }
                    }
                }
            }
        } while (changed);
    }


    private void fixWater()
    {
        int miny = this.miny;
        if (miny < 1) miny = 1;

        boolean changed;

        //remove hanging water or lava
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = miny; y < snapshots[0].length - 1; y++)
                {
                    BlockSnapshot block = this.snapshots[x][y][z];
                    BlockSnapshot underBlock = this.snapshots[x][y--][z];
                    if (block.typeId == Material.WATER || block.typeId == Material.LAVA)
                    {
                        // check if block below is air or is a non-source fluid block (level 1-7 = flowing, 8 = falling)
                        if (underBlock.typeId == Material.AIR || (underBlock.typeId == Material.WATER && (((Levelled) underBlock.data).getLevel() != 0)))
                        {
                            block.typeId = Material.AIR;
                        }
                    }
                }
            }
        }

        //fill water depressions
        do
        {
            changed = false;
            for (int y = Math.max(this.seaLevel - 10, 0); y <= this.seaLevel; y++)
            {
                for (int x = 1; x < snapshots.length - 1; x++)
                {
                    for (int z = 1; z < snapshots[0][0].length - 1; z++)
                    {
                        BlockSnapshot block = snapshots[x][y][z];

                        //only consider air blocks and flowing water blocks for upgrade to water source blocks
                        if (block.typeId == Material.AIR || (block.typeId == Material.WATER && ((Levelled) block.data).getLevel() != 0))
                        {
                            BlockSnapshot leftBlock = this.snapshots[x + 1][y][z];
                            BlockSnapshot rightBlock = this.snapshots[x - 1][y][z];
                            BlockSnapshot upBlock = this.snapshots[x][y][z + 1];
                            BlockSnapshot downBlock = this.snapshots[x][y][z - 1];
                            BlockSnapshot underBlock = this.snapshots[x][y - 1][z];

                            //block underneath MUST be source water
                            if (!(underBlock.typeId == Material.WATER && ((Levelled) underBlock.data).getLevel() == 0))
                                continue;

                            //count adjacent source water blocks
                            byte adjacentSourceWaterCount = 0;
                            if (leftBlock.typeId == Material.WATER && ((Levelled) leftBlock.data).getLevel() == 0)
                            {
                                adjacentSourceWaterCount++;
                            }
                            if (rightBlock.typeId == Material.WATER && ((Levelled) rightBlock.data).getLevel() == 0)
                            {
                                adjacentSourceWaterCount++;
                            }
                            if (upBlock.typeId == Material.WATER && ((Levelled) upBlock.data).getLevel() == 0)
                            {
                                adjacentSourceWaterCount++;
                            }
                            if (downBlock.typeId == Material.WATER && ((Levelled) downBlock.data).getLevel() == 0)
                            {
                                adjacentSourceWaterCount++;
                            }

                            //at least two adjacent blocks must be source water
                            if (adjacentSourceWaterCount >= 2)
                            {
                                block.typeId = Material.WATER;
                                ((Levelled) downBlock.data).setLevel(0);
                                changed = true;
                            }
                        }
                    }
                }
            }
        } while (changed);
    }


    private void removeDumpedFluids()
    {
        if (this.seaLevel < 1) return;

        //remove any surface water or lava above sea level, presumed to be placed by players
        //sometimes, this is naturally generated.  but replacing it is very easy with a bucket, so overall this is a good plan
        if (this.environment == Environment.NETHER) return;
        for (int x = 1; x < snapshots.length - 1; x++)
        {
            for (int z = 1; z < snapshots[0][0].length - 1; z++)
            {
                for (int y = this.seaLevel; y < snapshots[0].length - 1; y++)
                {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (block.typeId == Material.WATER || block.typeId == Material.LAVA)
                    {
                        block.typeId = Material.AIR;
                    }
                }
            }
        }
    }


    private int highestY(int x, int z, boolean ignoreLeaves)
    {
        int y;
        for (y = snapshots[0].length - 1; y > 0; y--)
        {
            BlockSnapshot block = this.snapshots[x][y][z];
            if (block.typeId != Material.AIR &&
                    !(ignoreLeaves && block.typeId == Material.SNOW) &&
                    !(ignoreLeaves && Tag.LEAVES.isTagged(block.typeId)) &&
                    !(block.typeId == Material.WATER) &&
                    !(block.typeId == Material.LAVA))
            {
                return y;
            }
        }

        return y;
    }


    static Set<Material> getPlayerBlocks(Environment environment, Biome biome)
    {
        //NOTE on this list.  why not make a list of natural blocks?
        //answer: better to leave a few player blocks than to remove too many natural blocks.  remember we're "restoring nature"
        //a few extra player blocks can be manually removed, but it will be impossible to guess exactly which natural materials to use in manual repair of an overzealous block removal
        Set<Material> playerBlocks = new HashSet<>();
        playerBlocks.addAll(Tag.ANVIL.getValues());
        playerBlocks.addAll(Tag.BANNERS.getValues());
        playerBlocks.addAll(Tag.BEACON_BASE_BLOCKS.getValues());
        playerBlocks.addAll(Tag.BEDS.getValues());
        playerBlocks.addAll(Tag.BUTTONS.getValues());
        playerBlocks.addAll(Tag.CAMPFIRES.getValues());
        playerBlocks.addAll(Tag.CANDLE_CAKES.getValues());
        playerBlocks.addAll(Tag.CANDLES.getValues());
        playerBlocks.addAll(Tag.CARPETS.getValues());
        playerBlocks.addAll(Tag.CAULDRONS.getValues());
        playerBlocks.addAll(Tag.DOORS.getValues());
        playerBlocks.addAll(Tag.FENCE_GATES.getValues());
        playerBlocks.addAll(Tag.FENCES.getValues());
        playerBlocks.addAll(Tag.FIRE.getValues());
        playerBlocks.addAll(Tag.FLOWER_POTS.getValues());
        playerBlocks.addAll(Tag.IMPERMEABLE.getValues()); // Glass block variants
        playerBlocks.addAll(Tag.LOGS.getValues());
        playerBlocks.addAll(Tag.PLANKS.getValues());
        playerBlocks.addAll(Tag.PRESSURE_PLATES.getValues());
        playerBlocks.addAll(Tag.RAILS.getValues());
        playerBlocks.addAll(Tag.SHULKER_BOXES.getValues());
        playerBlocks.addAll(Tag.SIGNS.getValues());
        playerBlocks.addAll(Tag.SLABS.getValues());
        playerBlocks.addAll(Tag.STAIRS.getValues());
        playerBlocks.addAll(Tag.STONE_BRICKS.getValues());
        playerBlocks.addAll(Tag.TRAPDOORS.getValues());
        playerBlocks.addAll(Tag.WALLS.getValues());
        playerBlocks.addAll(Tag.WOOL.getValues());
        playerBlocks.add(Material.BOOKSHELF);
        playerBlocks.add(Material.BREWING_STAND);
        playerBlocks.add(Material.BRICK);
        playerBlocks.add(Material.COBBLESTONE);
        playerBlocks.add(Material.LAPIS_BLOCK);
        playerBlocks.add(Material.DISPENSER);
        playerBlocks.add(Material.NOTE_BLOCK);
        playerBlocks.add(Material.STICKY_PISTON);
        playerBlocks.add(Material.PISTON);
        playerBlocks.add(Material.PISTON_HEAD);
        playerBlocks.add(Material.MOVING_PISTON);
        playerBlocks.add(Material.WHEAT);
        playerBlocks.add(Material.TNT);
        playerBlocks.add(Material.MOSSY_COBBLESTONE);
        playerBlocks.add(Material.TORCH);
        playerBlocks.add(Material.CHEST);
        playerBlocks.add(Material.REDSTONE_WIRE);
        playerBlocks.add(Material.CRAFTING_TABLE);
        playerBlocks.add(Material.FURNACE);
        playerBlocks.add(Material.LADDER);
        playerBlocks.add(Material.SCAFFOLDING);
        playerBlocks.add(Material.LEVER);
        playerBlocks.add(Material.REDSTONE_TORCH);
        playerBlocks.add(Material.SNOW_BLOCK);
        playerBlocks.add(Material.JUKEBOX);
        playerBlocks.add(Material.NETHER_PORTAL);
        playerBlocks.add(Material.JACK_O_LANTERN);
        playerBlocks.add(Material.CAKE);
        playerBlocks.add(Material.REPEATER);
        playerBlocks.add(Material.MUSHROOM_STEM);
        playerBlocks.add(Material.RED_MUSHROOM_BLOCK);
        playerBlocks.add(Material.BROWN_MUSHROOM_BLOCK);
        playerBlocks.add(Material.IRON_BARS);
        playerBlocks.add(Material.GLASS_PANE);
        playerBlocks.add(Material.MELON_STEM);
        playerBlocks.add(Material.ENCHANTING_TABLE);
        playerBlocks.add(Material.COBWEB);
        playerBlocks.add(Material.GRAVEL);
        playerBlocks.add(Material.SANDSTONE);
        playerBlocks.add(Material.ENDER_CHEST);
        playerBlocks.add(Material.COMMAND_BLOCK);
        playerBlocks.add(Material.REPEATING_COMMAND_BLOCK);
        playerBlocks.add(Material.CHAIN_COMMAND_BLOCK);
        playerBlocks.add(Material.BEACON);
        playerBlocks.add(Material.CARROT);
        playerBlocks.add(Material.POTATO);
        playerBlocks.add(Material.SKELETON_SKULL);
        playerBlocks.add(Material.WITHER_SKELETON_SKULL);
        playerBlocks.add(Material.CREEPER_HEAD);
        playerBlocks.add(Material.ZOMBIE_HEAD);
        playerBlocks.add(Material.PLAYER_HEAD);
        playerBlocks.add(Material.DRAGON_HEAD);
        playerBlocks.add(Material.SPONGE);
        playerBlocks.add(Material.WHITE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.ORANGE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.MAGENTA_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.YELLOW_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIME_STAINED_GLASS_PANE);
        playerBlocks.add(Material.PINK_STAINED_GLASS_PANE);
        playerBlocks.add(Material.GRAY_STAINED_GLASS_PANE);
        playerBlocks.add(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        playerBlocks.add(Material.CYAN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.PURPLE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BLUE_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BROWN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.GREEN_STAINED_GLASS_PANE);
        playerBlocks.add(Material.RED_STAINED_GLASS_PANE);
        playerBlocks.add(Material.BLACK_STAINED_GLASS_PANE);
        playerBlocks.add(Material.TRAPPED_CHEST);
        playerBlocks.add(Material.COMPARATOR);
        playerBlocks.add(Material.DAYLIGHT_DETECTOR);
        playerBlocks.add(Material.REDSTONE_BLOCK);
        playerBlocks.add(Material.HOPPER);
        playerBlocks.add(Material.QUARTZ_BLOCK);
        playerBlocks.add(Material.DROPPER);
        playerBlocks.add(Material.SLIME_BLOCK);
        playerBlocks.add(Material.PRISMARINE);
        playerBlocks.add(Material.HAY_BLOCK);
        playerBlocks.add(Material.SEA_LANTERN);
        playerBlocks.add(Material.COAL_BLOCK);
        playerBlocks.add(Material.REDSTONE_LAMP);
        playerBlocks.add(Material.RED_NETHER_BRICKS);
        playerBlocks.add(Material.POLISHED_ANDESITE);
        playerBlocks.add(Material.POLISHED_DIORITE);
        playerBlocks.add(Material.POLISHED_GRANITE);
        playerBlocks.add(Material.POLISHED_BASALT);
        playerBlocks.add(Material.POLISHED_DEEPSLATE);
        playerBlocks.add(Material.DEEPSLATE_BRICKS);
        playerBlocks.add(Material.CRACKED_DEEPSLATE_BRICKS);
        playerBlocks.add(Material.DEEPSLATE_TILES);
        playerBlocks.add(Material.CRACKED_DEEPSLATE_TILES);
        playerBlocks.add(Material.CHISELED_DEEPSLATE);
        playerBlocks.add(Material.RAW_COPPER_BLOCK);
        playerBlocks.add(Material.RAW_IRON_BLOCK);
        playerBlocks.add(Material.RAW_GOLD_BLOCK);
        playerBlocks.add(Material.LIGHTNING_ROD);

        //these are unnatural in the nether and end
        if (environment != Environment.NORMAL && environment != Environment.CUSTOM)
        {
            playerBlocks.addAll(Tag.BASE_STONE_OVERWORLD.getValues());
            playerBlocks.addAll(Tag.DIRT.getValues());
            playerBlocks.addAll(Tag.SAND.getValues());
        }

        //these are unnatural in the standard world, but not in the nether
        if (environment != Environment.NETHER)
        {
            playerBlocks.addAll(Tag.NYLIUM.getValues());
            playerBlocks.addAll(Tag.WART_BLOCKS.getValues());
            playerBlocks.addAll(Tag.BASE_STONE_NETHER.getValues());
            playerBlocks.add(Material.POLISHED_BLACKSTONE);
            playerBlocks.add(Material.CHISELED_POLISHED_BLACKSTONE);
            playerBlocks.add(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);
            playerBlocks.add(Material.GILDED_BLACKSTONE);
            playerBlocks.add(Material.BONE_BLOCK);
            playerBlocks.add(Material.SOUL_SAND);
            playerBlocks.add(Material.SOUL_SOIL);
            playerBlocks.add(Material.GLOWSTONE);
            playerBlocks.add(Material.NETHER_BRICK);
            playerBlocks.add(Material.MAGMA_BLOCK);
            playerBlocks.add(Material.ANCIENT_DEBRIS);
            playerBlocks.add(Material.IRON_CHAIN);
            playerBlocks.add(Material.SHROOMLIGHT);
            playerBlocks.add(Material.NETHER_GOLD_ORE);
            playerBlocks.add(Material.NETHER_SPROUTS);
            playerBlocks.add(Material.CRIMSON_FUNGUS);
            playerBlocks.add(Material.CRIMSON_ROOTS);
            playerBlocks.add(Material.NETHER_WART_BLOCK);
            playerBlocks.add(Material.WEEPING_VINES);
            playerBlocks.add(Material.WEEPING_VINES_PLANT);
            playerBlocks.add(Material.WARPED_FUNGUS);
            playerBlocks.add(Material.WARPED_ROOTS);
            playerBlocks.add(Material.WARPED_WART_BLOCK);
            playerBlocks.add(Material.TWISTING_VINES);
            playerBlocks.add(Material.TWISTING_VINES_PLANT);
        }
        //blocks from tags that are natural in the nether
        else
        {
            playerBlocks.remove(Material.CRIMSON_STEM);
            playerBlocks.remove(Material.CRIMSON_HYPHAE);
            playerBlocks.remove(Material.NETHER_BRICK_FENCE);
            playerBlocks.remove(Material.NETHER_BRICK_STAIRS);
            playerBlocks.remove(Material.SOUL_FIRE);
            playerBlocks.remove(Material.WARPED_STEM);
            playerBlocks.remove(Material.WARPED_HYPHAE);
        }

        //these are unnatural in the standard and nether worlds, but not in the end
        if (environment != Environment.THE_END)
        {
            playerBlocks.add(Material.CHORUS_PLANT);
            playerBlocks.add(Material.CHORUS_FLOWER);
            playerBlocks.add(Material.END_ROD);
            playerBlocks.add(Material.END_STONE);
            playerBlocks.add(Material.END_STONE_BRICKS);
            playerBlocks.add(Material.OBSIDIAN);
            playerBlocks.add(Material.PURPUR_BLOCK);
            playerBlocks.add(Material.PURPUR_PILLAR);
        }
        //blocks from tags that are natural in the end
        else
        {
            playerBlocks.remove(Material.PURPUR_SLAB);
            playerBlocks.remove(Material.PURPUR_STAIRS);
        }

        //these are unnatural in sandy biomes, but not elsewhere
        if (SAND_SOIL_BIOMES.contains(biome.getKey()) || environment != Environment.NORMAL)
        {
            playerBlocks.addAll(Tag.LEAVES.getValues());
        }
        //blocks from tags that are natural in non-sandy normal biomes
        else
        {
            playerBlocks.remove(Material.OAK_LOG);
            playerBlocks.remove(Material.SPRUCE_LOG);
            playerBlocks.remove(Material.BIRCH_LOG);
            playerBlocks.remove(Material.JUNGLE_LOG);
            playerBlocks.remove(Material.ACACIA_LOG);
            playerBlocks.remove(Material.DARK_OAK_LOG);
        }

        return playerBlocks;
    }
}
