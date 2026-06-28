/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

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
import me.ryanhamshire.GriefPrevention.events.ClaimInspectionEvent;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Handles claim tool interactions outside of PlayerEventHandler.
 */
final class ClaimToolDispatcher implements Listener
{
    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;

    ClaimToolDispatcher(@NotNull DataStore dataStore, @NotNull GriefPrevention instance)
    {
        this.dataStore = dataStore;
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerInteract(PlayerInteractEvent event)
    {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
        {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND)
        {
            return;
        }

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        Material clickedBlockType = clickedBlock != null ? clickedBlock.getType() : Material.AIR;
        Material materialInHand = instance.getItemInHand(player, hand).getType();
        if (materialInHand != instance.config_claims_investigationTool
                && materialInHand != instance.config_claims_modificationTool)
        {
            return;
        }

        // Claim tools should not continue into the general interaction listener.
        event.setCancelled(true);

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        //if he's investigating a claim
        if (materialInHand == instance.config_claims_investigationTool)
        {
            handleInvestigationTool(event, player, playerData, clickedBlock, clickedBlockType);
            return;
        }

        //if it's a golden shovel
        handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
    }

    private void handleInvestigationTool(
            @NotNull PlayerInteractEvent event,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @Nullable Block clickedBlock,
            @NotNull Material clickedBlockType)
    {
        //if claims are disabled in this world, do nothing
        if (!instance.claimsEnabledForWorld(player.getWorld()))
        {
            return;
        }

        // If investigation tool is on cooldown, do nothing.
        if (player.getCooldown(instance.config_claims_investigationTool) > 0)
        {
            return;
        }
        // Set investigation tool on cooldown to prevent spamming.
        player.setCooldown(instance.config_claims_investigationTool, 1);

        //if holding shift (sneaking), show all claims in area
        if (player.isSneaking() && player.hasPermission("griefprevention.visualizenearbyclaims"))
        {
            //find nearby claims
            Set<Claim> claims = this.dataStore.getNearbyClaims(player.getLocation());

            // alert plugins of a claim inspection, return if cancelled
            ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, null, claims, true);
            Bukkit.getPluginManager().callEvent(inspectionEvent);
            if (inspectionEvent.isCancelled())
            {
                return;
            }

            //visualize boundaries
            BoundaryVisualization.visualizeNearbyClaims(
                    player,
                    inspectionEvent.getClaims(),
                    player.getEyeLocation().getBlockY());
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.ShowNearbyClaims, String.valueOf(claims.size()));
            return;
        }

        //FEATURE: shovel and stick can be used from a distance away
        if (event.getAction() == Action.RIGHT_CLICK_AIR)
        {
            //try to find a far away non-air block along line of sight
            clickedBlock = getTargetBlock(player, 100);
            clickedBlockType = clickedBlock.getType();
        }

        //if no block, stop here
        if (clickedBlock == null)
        {
            return;
        }

        //air indicates too far away
        if (clickedBlockType == Material.AIR)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);

            // Remove visualizations
            playerData.setVisibleBoundaries(null);
            return;
        }

        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
        //no claim case
        if (claim == null)
        {
            // alert plugins of a claim inspection, return if cancelled
            ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, null);
            Bukkit.getPluginManager().callEvent(inspectionEvent);
            if (inspectionEvent.isCancelled())
            {
                return;
            }

            GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockNotClaimed);
            playerData.setVisibleBoundaries(null);
            return;
        }

        //claim case
        // alert plugins of a claim inspection, return if cancelled
        ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, claim);
        Bukkit.getPluginManager().callEvent(inspectionEvent);
        if (inspectionEvent.isCancelled())
        {
            return;
        }

        playerData.lastClaim = claim;
        GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockClaimed, claim.getOwnerName());

        //visualize boundary
        BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CLAIM);

        if (player.hasPermission("griefprevention.seeclaimsize"))
        {
            GriefPrevention.sendMessage(player, TextMode.Info, "  " + claim.getWidth() + "x" + claim.getHeight() + "=" + claim.getArea());
        }

        //if permission, tell about the player's offline time
        if (!claim.isAdminClaim() && (player.hasPermission("griefprevention.deleteclaims")
                || player.hasPermission("griefprevention.seeinactivity")))
        {
            if (claim.parent != null)
            {
                claim = claim.parent;
            }

            Date lastLogin = new Date(Bukkit.getOfflinePlayer(claim.ownerID).getLastPlayed());
            Date now = new Date();
            long daysElapsed = (now.getTime() - lastLogin.getTime()) / (1000 * 60 * 60 * 24);
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.PlayerOfflineTime, String.valueOf(daysElapsed));

            //drop the data we just loaded, if the player isn't online
            if (instance.getServer().getPlayer(claim.ownerID) == null)
            {
                this.dataStore.clearCachedPlayerData(claim.ownerID);
            }
        }
    }

    private void handleModificationTool(
            @NotNull PlayerInteractEvent event,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @Nullable Block clickedBlock,
            @NotNull Material clickedBlockType)
    {
        event.setCancelled(true);  //GriefPrevention exclusively reserves this tool  (e.g. no grass path creation for golden shovel)

        //FEATURE: shovel and stick can be used from a distance away
        if (event.getAction() == Action.RIGHT_CLICK_AIR)
        {
            //try to find a far away non-air block along line of sight
            clickedBlock = getTargetBlock(player, 100);
            clickedBlockType = clickedBlock.getType();
        }

        //if no block, stop here
        if (clickedBlock == null)
        {
            return;
        }

        //can't use the shovel from too far away
        if (clickedBlockType == Material.AIR)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);
            return;
        }

        //if the player doesn't have claims permission, don't do anything
        if (!player.hasPermission("griefprevention.createclaims"))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoCreateClaimPermission);
            return;
        }

        //if he's resizing a claim and that claim hasn't been deleted since he started resizing it
        if (playerData.claimResizing != null && playerData.claimResizing.inDataStore)
        {
            if (clickedBlock.getLocation().equals(playerData.lastShovelLocation))
            {
                return;
            }

            //figure out what the coords of his new claim would be
            int newx1;
            int newx2;
            int newz1;
            int newz2;
            int newy1;
            int newy2;
            if (playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getLesserBoundaryCorner().getBlockX())
            {
                newx1 = clickedBlock.getX();
                newx2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockX();
            }
            else
            {
                newx1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockX();
                newx2 = clickedBlock.getX();
            }

            if (playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getLesserBoundaryCorner().getBlockZ())
            {
                newz1 = clickedBlock.getZ();
                newz2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ();
            }
            else
            {
                newz1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockZ();
                newz2 = clickedBlock.getZ();
            }

            newy1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockY();
            newy2 = clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance;

            this.dataStore.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);
            return;
        }

        //otherwise, since not currently resizing a claim, must be starting a resize, creating a new claim, or creating a subdivision
        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), true, playerData.lastClaim);
        //if within an existing claim, he's not creating a new one
        if (claim != null)
        {
            //if the player has permission to edit the claim or subdivision
            Supplier<String> noEditReason = claim.checkPermission(
                    player,
                    ClaimPermission.Edit,
                    event,
                    () -> instance.dataStore.getMessage(Messages.CreateClaimFailOverlapOtherPlayer, claim.getOwnerName()));
            if (noEditReason == null)
            {
                //if he clicked on a corner, start resizing it
                boolean clickedCorner = (clickedBlock.getX() == claim.getLesserBoundaryCorner().getBlockX()
                        || clickedBlock.getX() == claim.getGreaterBoundaryCorner().getBlockX())
                        && (clickedBlock.getZ() == claim.getLesserBoundaryCorner().getBlockZ()
                                || clickedBlock.getZ() == claim.getGreaterBoundaryCorner().getBlockZ());
                if (clickedCorner)
                {
                    playerData.claimResizing = claim;
                    playerData.lastShovelLocation = clickedBlock.getLocation();
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ResizeStart);
                }
                //if he didn't click on a corner and is in subdivision mode, he's creating a new subdivision
                else if (playerData.shovelMode == ShovelMode.Subdivide)
                {
                    //if it's the first click, he's trying to start a new subdivision
                    if (playerData.lastShovelLocation == null)
                    {
                        //if the clicked claim was a subdivision, tell him he can't start a new subdivision here
                        if (claim.parent != null)
                        {
                            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapSubdivision);
                        }
                        //otherwise start a new subdivision
                        else
                        {
                            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionStart);
                            playerData.lastShovelLocation = clickedBlock.getLocation();
                            playerData.claimSubdividing = claim;
                        }
                    }
                    //otherwise, he's trying to finish creating a subdivision by setting the other boundary corner
                    else
                    {
                        //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
                        if (!playerData.lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
                        {
                            playerData.lastShovelLocation = null;
                            handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
                            return;
                        }

                        //try to create a new claim (will return null if this subdivision overlaps another)
                        CreateClaimResult result = this.dataStore.createClaim(
                                player.getWorld(),
                                playerData.lastShovelLocation.getBlockX(), clickedBlock.getX(),
                                playerData.lastShovelLocation.getBlockY() - instance.config_claims_claimsExtendIntoGroundDistance,
                                clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance,
                                playerData.lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                                null,
                                playerData.claimSubdividing,
                                null,
                                player);

                        //if it didn't succeed, tell the player why
                        if (!result.succeeded || result.claim == null)
                        {
                            if (result.claim != null)
                            {
                                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateSubdivisionOverlap);
                                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
                            }
                            else
                            {
                                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                            }

                            return;
                        }

                        //otherwise, advise him on the /trust command and show him his new subdivision
                        GriefPrevention.sendMessage(player, TextMode.Success, Messages.SubdivisionSuccess);
                        BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, clickedBlock);
                        playerData.lastShovelLocation = null;
                        playerData.claimSubdividing = null;
                    }
                }
                //otherwise tell him he can't create a claim here, and show him the existing claim
                //also advise him to consider /abandonclaim or resizing the existing claim
                else
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlap);
                    BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CLAIM, clickedBlock);
                }
            }
            //otherwise tell the player he can't claim here because it's someone else's claim, and show him the claim
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noEditReason.get());
                BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
            }

            return;
        }

        //otherwise, the player isn't in an existing claim!
        //if he hasn't already start a claim with a previous shovel action
        Location lastShovelLocation = playerData.lastShovelLocation;
        if (lastShovelLocation == null)
        {
            //if claims are not enabled in this world and it's not an administrative claim, display an error message and stop
            if (!instance.claimsEnabledForWorld(player.getWorld()))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                return;
            }

            //if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
            if (instance.config_claims_maxClaimsPerPlayer > 0
                    && !player.hasPermission("griefprevention.overrideclaimcountlimit")
                    && playerData.getClaims().size() >= instance.config_claims_maxClaimsPerPlayer)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                return;
            }

            //remember it, and start him on the new claim
            playerData.lastShovelLocation = clickedBlock.getLocation();
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimStart);

            //show him where he's working
            BoundaryVisualization.visualizeArea(player, new BoundingBox(clickedBlock), VisualizationType.INITIALIZE_ZONE);
            return;
        }

        //otherwise, he's trying to finish creating a claim by setting the other boundary corner
        //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
        if (!lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
        {
            playerData.lastShovelLocation = null;
            handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
            return;
        }

        //apply pvp rule
        if (playerData.inPvpCombat())
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoClaimDuringPvP);
            return;
        }

        int newClaimWidth = Math.abs(playerData.lastShovelLocation.getBlockX() - clickedBlock.getX()) + 1;
        int newClaimHeight = Math.abs(playerData.lastShovelLocation.getBlockZ() - clickedBlock.getZ()) + 1;

        //apply minimum claim dimensions rule
        if (playerData.shovelMode != ShovelMode.Admin)
        {
            if (newClaimWidth < instance.config_claims_minWidth || newClaimHeight < instance.config_claims_minWidth)
            {
                //this IF block is a workaround for craftbukkit bug which fires two events for one interaction
                if (newClaimWidth != 1 && newClaimHeight != 1)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.NewClaimTooNarrow, String.valueOf(instance.config_claims_minWidth));
                }
                return;
            }

            int newArea = newClaimWidth * newClaimHeight;
            if (newArea < instance.config_claims_minArea)
            {
                if (newArea != 1)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeClaimInsufficientArea, String.valueOf(instance.config_claims_minArea));
                }
                return;
            }
        }

        UUID playerID = player.getUniqueId();
        //if not an administrative claim, verify the player has enough claim blocks for this new claim
        if (playerData.shovelMode != ShovelMode.Admin)
        {
            int newClaimArea = newClaimWidth * newClaimHeight;
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            if (newClaimArea > remainingBlocks)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(newClaimArea - remainingBlocks));
                instance.dataStore.tryAdvertiseAdminAlternatives(player);
                return;
            }
        }
        else
        {
            playerID = null;
        }

        //try to create a new claim
        CreateClaimResult result = this.dataStore.createClaim(
                player.getWorld(),
                lastShovelLocation.getBlockX(), clickedBlock.getX(),
                lastShovelLocation.getBlockY() - instance.config_claims_claimsExtendIntoGroundDistance,
                clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance,
                lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                playerID,
                null,
                null,
                player);

        //if it didn't succeed, tell the player why
        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }

            return;
        }

        //otherwise, advise him on the /trust command and show him his new claim
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);
        BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, clickedBlock);
        playerData.lastShovelLocation = null;

        //if it's a big claim, tell the player about subdivisions
        if (!player.hasPermission("griefprevention.adminclaims") && result.claim.getArea() >= 1000)
        {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.BecomeMayor, 200L);
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, 201L, DataStore.SUBDIVISION_VIDEO_URL);
        }

        AutoExtendClaimTask.scheduleAsync(result.claim);
    }

    private static @NotNull Block getTargetBlock(@NotNull Player player, int maxDistance) throws IllegalStateException
    {
        Location eye = player.getEyeLocation();
        Material eyeMaterial = eye.getBlock().getType();
        boolean passThroughWater = eyeMaterial == Material.WATER;
        BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
        Block result = player.getLocation().getBlock().getRelative(BlockFace.UP);
        while (iterator.hasNext())
        {
            result = iterator.next();
            Material type = result.getType();
            if (!Tag.REPLACEABLE.isTagged(type) || (!passThroughWater && type == Material.WATER))
            {
                return result;
            }
        }

        return result;
    }
}
