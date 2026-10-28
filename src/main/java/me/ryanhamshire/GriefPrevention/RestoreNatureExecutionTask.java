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

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

//this main thread task takes the output from the RestoreNatureProcessingTask\
//and updates the world accordingly
class RestoreNatureExecutionTask implements Runnable
{
    //results from processing thread
    //will be applied to the world
    private final BlockSnapshot[][][] snapshots;

    //boundaries for changes
    private final int miny;
    private final Location lesserCorner;
    private final Location greaterCorner;

    //player who should be notified about the result (will see a visualization when the restoration is complete)
    private final Player player;

    public RestoreNatureExecutionTask(BlockSnapshot[][][] snapshots, int miny, Location lesserCorner, Location greaterCorner, Player player)
    {
        this.snapshots = snapshots;
        this.miny = miny;
        this.lesserCorner = lesserCorner;
        this.greaterCorner = greaterCorner;
        this.player = player;
    }


    @Override
    public void run()
    {
        //apply changes to the world, but ONLY to unclaimed blocks
        //note that the edge of the results is not applied (the 1-block-wide band around the outside of the chunk)
        //those data were sent to the processing thread for referernce purposes, but aren't part of the area selected for restoration
        Claim cachedClaim = null;
        for (int x = 1; x < this.snapshots.length - 1; x++)
        {
            for (int z = 1; z < this.snapshots[0][0].length - 1; z++)
            {
                for (int y = this.miny; y < this.snapshots[0].length; y++)
                {
                    BlockSnapshot blockUpdate = this.snapshots[x][y][z];
                    Block currentBlock = blockUpdate.location.getBlock();
                    if (blockUpdate.typeId != currentBlock.getType() || !blockUpdate.data.equals(currentBlock.getBlockData()))
                    {
                        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(blockUpdate.location, false, cachedClaim);
                        if (claim != null)
                        {
                            cachedClaim = claim;
                            break;
                        }

                        try
                        {
                            currentBlock.setType(blockUpdate.typeId, false);
                            // currentBlock.setBlockData(blockUpdate.data, false);
                        }
                        catch (IllegalArgumentException e)
                        {
                            //just don't update this block and continue trying to update other blocks
                        }
                    }
                }
            }
        }

        //clean up any entities in the chunk, ensure no players are suffocated
        Chunk chunk = this.lesserCorner.getChunk();
        Entity[] entities = chunk.getEntities();
        for (Entity entity : entities)
        {
            if (!(entity instanceof Player || entity instanceof Animals))
            {
                //hanging entities (paintings, item frames) and cushions are protected when they're in land claims
                if (!(entity instanceof Hanging || entity instanceof Cushion) || GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null) == null)
                {
                    //everything else is removed
                    entity.remove();
                }
            }

            //for players, always ensure there's air where the player is standing
            else
            {
                Block feetBlock = entity.getLocation().getBlock();
                feetBlock.setType(Material.AIR);
                feetBlock.getRelative(BlockFace.UP).setType(Material.AIR);
            }
        }

        //show visualization to player who started the restoration
        if (player != null)
        {
            BoundaryVisualization.visualizeArea(
                    player,
                    new BoundingBox(lesserCorner, greaterCorner),
                    VisualizationType.NATURE_RESTORATION_ZONE);
        }
    }
}
