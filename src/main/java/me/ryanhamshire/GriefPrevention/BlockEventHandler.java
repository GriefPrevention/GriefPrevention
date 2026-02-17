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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

//event handlers related to blocks
public class BlockEventHandler implements Listener
{
    //convenience reference to singleton datastore
    private final DataStore dataStore;

    private final Set<Material> trashBlocks;

    //constructor
    public BlockEventHandler(DataStore dataStore)
    {
        this.dataStore = dataStore;

        //create the list of blocks which will not trigger a warning when they're placed outside of land claims
        this.trashBlocks = new HashSet<>();
        this.trashBlocks.add(Material.COBBLESTONE);
        this.trashBlocks.add(Material.TORCH);
        this.trashBlocks.add(Material.DIRT);
        this.trashBlocks.add(Material.OAK_SAPLING);
        this.trashBlocks.add(Material.SPRUCE_SAPLING);
        this.trashBlocks.add(Material.BIRCH_SAPLING);
        this.trashBlocks.add(Material.JUNGLE_SAPLING);
        this.trashBlocks.add(Material.ACACIA_SAPLING);
        this.trashBlocks.add(Material.DARK_OAK_SAPLING);
        this.trashBlocks.add(Material.GRAVEL);
        this.trashBlocks.add(Material.SAND);
        this.trashBlocks.add(Material.TNT);
        this.trashBlocks.add(Material.CRAFTING_TABLE);
        this.trashBlocks.add(Material.TUFF);
        this.trashBlocks.add(Material.COBBLED_DEEPSLATE);
    }

    //when a player breaks a block...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent breakEvent)
    {
        Player player = breakEvent.getPlayer();
        Block block = breakEvent.getBlock();

        //make sure the player is allowed to break at the location
        String noBuildReason = GriefPrevention.instance.allowBreak(player, block, block.getLocation(), breakEvent);
        if (noBuildReason != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
            breakEvent.setCancelled(true);
            return;
        }
    }

    //when a player changes the text of a sign...
    @EventHandler(ignoreCancelled = true)
    public void onSignChanged(SignChangeEvent event)
    {
        Player player = event.getPlayer();
        Block sign = event.getBlock();

        if (player == null || sign == null) return;

        String noBuildReason = GriefPrevention.instance.allowBuild(player, sign.getLocation(), sign.getType());
        if (noBuildReason != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
            event.setCancelled(true);
            return;
        }

        //send sign content to online administrators
        if (!GriefPrevention.instance.config_signNotifications) return;

        StringBuilder lines = new StringBuilder(" placed a sign @ " + GriefPrevention.getfriendlyLocationString(event.getBlock().getLocation()));
        boolean notEmpty = false;
        for (int i = 0; i < event.getLines().length; i++)
        {
            String withoutSpaces = event.getLine(i).replace(" ", "");
            if (!withoutSpaces.isEmpty())
            {
                notEmpty = true;
                lines.append("\n  ").append(event.getLine(i));
            }
        }

        String signMessage = lines.toString();

        //prevent signs with blocked IP addresses
        if (!player.hasPermission("griefprevention.spam") && GriefPrevention.instance.containsBlockedIP(signMessage))
        {
            event.setCancelled(true);
            return;
        }

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        //if not empty and wasn't the same as the last sign, log it and remember it for later
        //This has been temporarily removed since `signMessage` includes location, not just the message. Waste of memory IMO
        //if(notEmpty && (playerData.lastSignMessage == null || !playerData.lastSignMessage.equals(signMessage)))
        if (notEmpty)
        {
            GriefPrevention.AddLogEntry(player.getName() + lines.toString().replace("\n  ", ";"), null);
            PlayerEventHandler.makeSocialLogEntry(player.getName(), signMessage);
            //playerData.lastSignMessage = signMessage;

            if (!player.hasPermission("griefprevention.eavesdropsigns"))
            {
                @SuppressWarnings("unchecked")
                Collection<Player> players = (Collection<Player>) GriefPrevention.instance.getServer().getOnlinePlayers();
                for (Player otherPlayer : players)
                {
                    if (otherPlayer.hasPermission("griefprevention.eavesdropsigns"))
                    {
                        otherPlayer.sendMessage(ChatColor.GRAY + player.getName() + signMessage);
                    }
                }
            }
        }
    }

    //when a player places multiple blocks...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlocksPlace(BlockMultiPlaceEvent placeEvent)
    {
        Player player = placeEvent.getPlayer();

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;

        //make sure the player is allowed to build at the location
        for (BlockState block : placeEvent.getReplacedBlockStates())
        {
            String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation(), block.getType());
            if (noBuildReason != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                placeEvent.setCancelled(true);
                return;
            }
        }
    }

    private boolean doesAllowFireProximityInWorld(World world)
    {
        if (GriefPrevention.instance.pvpRulesApply(world))
        {
            return GriefPrevention.instance.config_pvp_allowFireNearPlayers;
        }
        else
        {
            return GriefPrevention.instance.config_pvp_allowFireNearPlayers_NonPvp;
        }
    }

    //when a player places a block...
    @SuppressWarnings("null")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent placeEvent)
    {
        Player player = placeEvent.getPlayer();
        Block block = placeEvent.getBlock();

        //FEATURE: limit fire placement, to prevent PvP-by-fire

        //if placed block is fire and pvp is off, apply rules for proximity to other players
        if (block.getType() == Material.FIRE && !doesAllowFireProximityInWorld(block.getWorld()))
        {
            List<Player> players = block.getWorld().getPlayers();
            for (Player otherPlayer : players)
            {
                // Ignore players in creative or spectator mode to avoid users from checking if someone is spectating near them
                if (otherPlayer.getGameMode() == GameMode.CREATIVE || otherPlayer.getGameMode() == GameMode.SPECTATOR)
                {
                    continue;
                }

                Location location = otherPlayer.getLocation();
                if (!otherPlayer.equals(player) && location.distanceSquared(block.getLocation()) < 9 && player.canSee(otherPlayer))
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerTooCloseForFire2);
                    placeEvent.setCancelled(true);
                    return;
                }
            }
        }

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;

        //make sure the player is allowed to build at the location
        String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation(), block.getType());
        if (noBuildReason != null)
        {
            // Allow players with container trust to place books in lecterns
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
            if (block.getType() == Material.LECTERN && placeEvent.getBlockReplacedState().getType() == Material.LECTERN)
            {
                if (claim != null)
                {
                    playerData.lastClaim = claim;
                    Supplier<String> noContainerReason = claim.checkPermission(player, ClaimPermission.Container, placeEvent);
                    if (noContainerReason == null)
                        return;

                    placeEvent.setCancelled(true);
                    GriefPrevention.sendMessage(player, TextMode.Err, noContainerReason.get());
                    return;
                }
            }
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
            placeEvent.setCancelled(true);
            return;
        }

        //if the block is being placed within or under an existing claim
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);

        //If block is a chest, don't allow a DoubleChest to form across a claim boundary
        denyConnectingDoubleChestsAcrossClaimBoundary(claim, block, player);
        
        if (claim != null)
        {
            playerData.lastClaim = claim;

            //warn about TNT not destroying claimed blocks
            if (block.getType() == Material.TNT && !claim.areExplosivesAllowed && playerData.siegeData == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoTNTDamageClaims);
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimExplosivesAdvertisement);
            }

            //if the player has permission for the claim and he's placing UNDER the claim
            if (block.getY() <= claim.lesserBoundaryCorner.getBlockY() && claim.checkPermission(player, ClaimPermission.Build, placeEvent) == null)
            {
                //extend the claim downward
                this.dataStore.extendClaim(claim, block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
            }

            //allow for a build warning in the future
            playerData.warnedAboutBuildingOutsideClaims = false;
        }

        //FEATURE: automatically create a claim when a player who has no claims places a chest

        //otherwise if there's no claim, the player is placing a chest, and new player automatic claims are enabled
        else if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1 && player.hasPermission("griefprevention.createclaims") && block.getType() == Material.CHEST)
        {
            //if the chest is too deep underground, don't create the claim and explain why
            if (GriefPrevention.instance.config_claims_preventTheft && block.getY() < GriefPrevention.instance.getMaxDepth(block.getWorld()))
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.TooDeepToClaim);
                return;
            }

            int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;

            //if the player doesn't have any claims yet, automatically create a claim centered at the chest
            if (playerData.getClaims().size() == 0 && player.getGameMode() == GameMode.SURVIVAL)
            {
                //radius == 0 means protect ONLY the chest
                if (GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius == 0)
                {
                    this.dataStore.createClaim(block.getWorld(), block.getX(), block.getX(), block.getY(), block.getY(), block.getZ(), block.getZ(), player.getUniqueId(), null, null, player);
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.ChestClaimConfirmation);
                }

                //otherwise, create a claim in the area around the chest
                else
                {
                    //if failure due to insufficient claim blocks available
                    if (playerData.getRemainingClaimBlocks() < Math.pow(1 + 2 * GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadiusMin, 2))
                    {
                        GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoEnoughBlocksForChestClaim);
                        return;
                    }

                    //as long as the automatic claim overlaps another existing claim, shrink it
                    //note that since the player had permission to place the chest, at the very least, the automatic claim will include the chest
                    CreateClaimResult result = null;
                    while (radius >= GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadiusMin)
                    {
                        int area = (radius * 2 + 1) * (radius * 2 + 1);
                        if (playerData.getRemainingClaimBlocks() >= area)
                        {
                            result = this.dataStore.createClaim(
                                    block.getWorld(),
                                    block.getX() - radius, block.getX() + radius,
                                    block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, block.getY(),
                                    block.getZ() - radius, block.getZ() + radius,
                                    player.getUniqueId(),
                                    null, null,
                                    player);

                            if (result.succeeded) break;
                        }

                        radius--;
                    }

                    if (result != null && result.claim != null)
                    {
                        if (result.succeeded)
                        {
                            //notify and explain to player
                            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AutomaticClaimNotification);

                            //show the player the protected area
                            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, block);
                        }
                        else
                        {
                            //notify and explain to player
                            GriefPrevention.sendMessage(player, TextMode.Err, Messages.AutomaticClaimOtherClaimTooClose);

                            //show the player the protected area
                            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, block);
                        }
                    }
                }

                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
            }

            //check to see if this chest is in a claim, and warn when it isn't
            if (GriefPrevention.instance.config_claims_preventTheft && this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim) == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnprotectedChestWarning);
            }
        }

        //FEATURE: limit wilderness tree planting to grass, or dirt with more blocks beneath it
        else if (Tag.SAPLINGS.isTagged(block.getType()) && GriefPrevention.instance.config_blockSkyTrees && GriefPrevention.instance.claimsEnabledForWorld(player.getWorld()))
        {
            Block earthBlock = placeEvent.getBlockAgainst();


            // in Minecraft 1.20.3 GRASS was renamed to SHORT_GRASS
            // This will support both before/after the change
            Material grassMaterial;
            try{
                grassMaterial = Material.valueOf("GRASS");
            } catch(IllegalArgumentException e){
                grassMaterial = Material.valueOf("SHORT_GRASS");
            }

            if (earthBlock.getType() != grassMaterial)
            {
                if (earthBlock.getRelative(BlockFace.DOWN).getType() == Material.AIR ||
                        earthBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType() == Material.AIR)
                {
                    placeEvent.setCancelled(true);
                }
            }
        }

        //FEATURE: warn players when they're placing non-trash blocks outside of their claimed areas
        else if (!this.trashBlocks.contains(block.getType()) && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld()))
        {
            if (!playerData.warnedAboutBuildingOutsideClaims && !player.hasPermission("griefprevention.adminclaims")
                    && player.hasPermission("griefprevention.createclaims") && ((playerData.lastClaim == null
                    && playerData.getClaims().size() == 0) || (playerData.lastClaim != null
                    && playerData.lastClaim.isNear(player.getLocation(), 15))))
            {
                Long now = null;
                if (playerData.buildWarningTimestamp == null || (now = System.currentTimeMillis()) - playerData.buildWarningTimestamp > 600000)  //10 minute cooldown
                {
                    GriefPrevention.sendMessage(player, TextMode.Warn, Messages.BuildingOutsideClaims);
                    playerData.warnedAboutBuildingOutsideClaims = true;

                    if (now == null) now = System.currentTimeMillis();
                    playerData.buildWarningTimestamp = now;

                    if (playerData.getClaims().size() < 2)
                    {
                        GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
                    }

                    if (playerData.lastClaim != null)
                    {
                        BoundaryVisualization.visualizeClaim(player, playerData.lastClaim, VisualizationType.CLAIM, block);
                    }
                }
            }
        }

        //warn players when they place TNT above sea level, since it doesn't destroy blocks there
        if (GriefPrevention.instance.config_blockSurfaceOtherExplosions && block.getType() == Material.TNT &&
                block.getWorld().getEnvironment() != Environment.NETHER &&
                block.getY() > GriefPrevention.instance.getSeaLevel(block.getWorld()) - 5 &&
                claim == null &&
                playerData.siegeData == null)
        {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoTNTDamageAboveSeaLevel);
        }

        //warn players about disabled pistons outside of land claims
        if (GriefPrevention.instance.config_pistonMovement == PistonMode.CLAIMS_ONLY &&
                (block.getType() == Material.PISTON || block.getType() == Material.STICKY_PISTON) &&
                claim == null)
        {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoPistonsOutsideClaims);
        }

        //limit active blocks in creative mode worlds
        if (!player.hasPermission("griefprevention.adminclaims") && GriefPrevention.instance.creativeRulesApply(block.getLocation()) && isActiveBlock(block))
        {
            String noPlaceReason = claim.allowMoreActiveBlocks();
            if (noPlaceReason != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noPlaceReason);
                placeEvent.setCancelled(true);
                return;
            }
        }
    }

    static boolean isActiveBlock(Block block)
    {
        return isActiveBlock(block.getType());
    }

    static boolean isActiveBlock(BlockState state)
    {
        return isActiveBlock(state.getType());
    }

    static boolean isActiveBlock(Material type)
    {
        if (type == Material.HOPPER || type == Material.BEACON || type == Material.SPAWNER) return true;
        return false;
    }

    private static final BlockFace[] HORIZONTAL_DIRECTIONS = new BlockFace[] {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };
    private void denyConnectingDoubleChestsAcrossClaimBoundary(Claim claim, Block block, Player player)
    {
        UUID claimOwner = null;
        if (claim != null)
            claimOwner = claim.getOwnerID();

        // Check for double chests placed just outside the claim boundary
        if (block.getBlockData() instanceof Chest)
        {
            for (BlockFace face : HORIZONTAL_DIRECTIONS)
            {
                Block relative = block.getRelative(face);
                if (!(relative.getBlockData() instanceof Chest)) continue;

                Claim relativeClaim = this.dataStore.getClaimAt(relative.getLocation(), true, claim);
                UUID relativeClaimOwner = relativeClaim == null ? null : relativeClaim.getOwnerID();

                // Chests outside claims should connect (both null)
                // and chests inside the same claim should connect (equal)
                if (Objects.equals(claimOwner, relativeClaimOwner)) break;

                // Change both chests to singular chests
                Chest chest = (Chest) block.getBlockData();
                chest.setType(Chest.Type.SINGLE);
                block.setBlockData(chest);

                Chest relativeChest = (Chest) relative.getBlockData();
                relativeChest.setType(Chest.Type.SINGLE);
                relative.setBlockData(relativeChest);

                // Resend relative chest block to prevent visual bug
                player.sendBlockChange(relative.getLocation(), relativeChest);
                break;
            }
        }
    }

    // Prevent pistons pushing blocks into or out of claims.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonExtend(BlockPistonExtendEvent event)
    {
        onPistonEvent(event, event.getBlocks(), false);
    }

    // Prevent pistons pulling blocks into or out of claims.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonRetract(BlockPistonRetractEvent event)
    {
        onPistonEvent(event, event.getBlocks(), true);
    }

    // Handle piston push and pulls.
    private void onPistonEvent(BlockPistonEvent event, List<Block> blocks, boolean isRetract)
    {
        PistonMode pistonMode = GriefPrevention.instance.config_pistonMovement;
        // Return if piston movements are ignored.
        if (pistonMode == PistonMode.IGNORED) return;

        // Don't check in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        BlockFace direction = event.getDirection();
        Block pistonBlock = event.getBlock();
        Claim pistonClaim = this.dataStore.getClaimAt(pistonBlock.getLocation(), false,
                pistonMode != PistonMode.CLAIMS_ONLY, null);

        // A claim is required, but the piston is not inside a claim.
        if (pistonClaim == null && pistonMode == PistonMode.CLAIMS_ONLY)
        {
            event.setCancelled(true);
            return;
        }

        // If no blocks are moving, quickly check if another claim's boundaries are violated.
        if (blocks.isEmpty())
        {
            // No block and retraction is always safe.
            if (isRetract) return;

            Block invadedBlock = pistonBlock.getRelative(direction);
            Claim invadedClaim = this.dataStore.getClaimAt(invadedBlock.getLocation(), false,
                    pistonMode != PistonMode.CLAIMS_ONLY, pistonClaim);
            if (invadedClaim != null && (pistonClaim == null || !Objects.equals(pistonClaim.getOwnerID(), invadedClaim.getOwnerID())))
            {
                event.setCancelled(true);
            }

            return;
        }

        // Create bounding box for moved blocks.
        BoundingBox movedBlocks = BoundingBox.ofBlocks(blocks);
        // Expand to include invaded zone.
        movedBlocks.resize(direction, 1);

        if (pistonClaim != null)
        {
            // If blocks are all inside the same claim as the piston, allow.
            if (new BoundingBox(pistonClaim).contains(movedBlocks)) return;

            /*
             * In claims-only mode, all moved blocks must be inside of the owning claim.
             * From BigScary:
             *  - Could push into another land claim, don't want to spend CPU checking for that
             *  - Push ice out, place torch, get water outside the claim
             */
            if (pistonMode == PistonMode.CLAIMS_ONLY)
            {
                event.setCancelled(true);
                return;
            }
        }

        // Check if blocks are in line vertically.
        if (movedBlocks.getLength() == 1 && movedBlocks.getWidth() == 1)
        {
            // Pulling up is always safe. The claim may not contain the area pulled from, but claims cannot stack.
            if (isRetract && direction == BlockFace.UP) return;

            // Pushing down is always safe. The claim may not contain the area pushed into, but claims cannot stack.
            if (!isRetract && direction == BlockFace.DOWN) return;
        }

        // Assemble list of potentially intersecting claims from chunks interacted with.
        ArrayList<Claim> intersectable = new ArrayList<>();
        int chunkXMax = movedBlocks.getMaxX() >> 4;
        int chunkZMax = movedBlocks.getMaxZ() >> 4;

        for (int chunkX = movedBlocks.getMinX() >> 4; chunkX <= chunkXMax; ++chunkX)
        {
            for (int chunkZ = movedBlocks.getMinZ() >> 4; chunkZ <= chunkZMax; ++chunkZ)
            {
                ArrayList<Claim> chunkClaims = dataStore.chunksToClaimsMap.get(DataStore.getChunkHash(chunkX, chunkZ));
                if (chunkClaims == null) continue;

                for (Claim claim : chunkClaims)
                {
                    // Ensure claim is not piston claim and is in same world.
                    if (pistonClaim != claim && pistonBlock.getWorld().equals(claim.getLesserBoundaryCorner().getWorld()))
                        intersectable.add(claim);
                }
            }
        }

        BiPredicate<Claim, BoundingBox> intersectionHandler;
        final Claim finalPistonClaim = pistonClaim;

        // Fast mode: Bounding box intersection always causes a conflict, even if blocks do not conflict.
        if (pistonMode == PistonMode.EVERYWHERE_SIMPLE)
        {
            intersectionHandler = (claim, claimBoundingBox) ->
            {
                // If owners are different, cancel.
                if (finalPistonClaim == null || !Objects.equals(finalPistonClaim.getOwnerID(), claim.getOwnerID()))
                {
                    event.setCancelled(true);
                    return true;
                }

                // Otherwise, proceed to next claim.
                return false;
            };
        }
        // Precise mode: Bounding box intersection may not yield a conflict. Individual blocks must be considered.
        else
        {
            // Set up list of affected blocks.
            HashSet<Block> checkBlocks = new HashSet<>(blocks);

            // Add all blocks that will be occupied after the shift.
            for (Block block : blocks)
                if (block.getPistonMoveReaction() != PistonMoveReaction.BREAK)
                    checkBlocks.add(block.getRelative(direction));

            intersectionHandler = (claim, claimBoundingBox) ->
            {
                // Ensure that the claim contains an affected block.
                if (checkBlocks.stream().noneMatch(claimBoundingBox::contains)) return false;

                // If pushing this block will change ownership, cancel the event and take away the piston (for performance reasons).
                if (finalPistonClaim == null || !Objects.equals(finalPistonClaim.getOwnerID(), claim.getOwnerID()))
                {
                    event.setCancelled(true);
                    if (GriefPrevention.instance.config_pistonExplosionSound)
                    {
                        pistonBlock.getWorld().createExplosion(pistonBlock.getLocation(), 0);
                    }
                    pistonBlock.getWorld().dropItem(pistonBlock.getLocation(), new ItemStack(event.isSticky() ? Material.STICKY_PISTON : Material.PISTON));
                    pistonBlock.setType(Material.AIR);
                    return true;
                }

                // Otherwise, proceed to next claim.
                return false;
            };
        }

        for (Claim claim : intersectable)
        {
            BoundingBox claimBoundingBox = new BoundingBox(claim);

            // Ensure claim intersects with block bounding box.
            if (!claimBoundingBox.intersects(movedBlocks)) continue;

            // Do additional mode-based handling.
            if (intersectionHandler.test(claim, claimBoundingBox)) return;
        }
    }

    //blocks are ignited ONLY by flint and steel (not by being near lava, open flames, etc), unless configured otherwise
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockIgnite(BlockIgniteEvent igniteEvent)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(igniteEvent.getBlock().getWorld())) return;

        if (igniteEvent.getCause() == IgniteCause.LIGHTNING && GriefPrevention.instance.dataStore.getClaimAt(igniteEvent.getIgnitingEntity().getLocation(), false, null) != null)
        {
            igniteEvent.setCancelled(true); //BlockIgniteEvent is called before LightningStrikeEvent. See #532. However, see #1125 for further discussion on detecting trident-caused lightning.
        }

        // If a fire is started by a fireball from a dispenser, allow it if the dispenser is in the same claim.
        if (igniteEvent.getCause() == IgniteCause.FIREBALL && igniteEvent.getIgnitingEntity() instanceof Fireball)
        {
            ProjectileSource shooter = ((Fireball) igniteEvent.getIgnitingEntity()).getShooter();
            if (shooter instanceof BlockProjectileSource)
            {
                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(igniteEvent.getBlock().getLocation(), false, null);
                if (claim != null && GriefPrevention.instance.dataStore.getClaimAt(((BlockProjectileSource) shooter).getBlock().getLocation(), false, claim) == claim)
                {
                    return;
                }
            }
        }

        // Arrow ignition.
        if (igniteEvent.getCause() == IgniteCause.ARROW && igniteEvent.getIgnitingEntity() != null)
        {
            // Arrows shot by players may return the shooter, not the arrow.
            if (igniteEvent.getIgnitingEntity() instanceof Player player)
            {
                BlockBreakEvent breakEvent = new BlockBreakEvent(igniteEvent.getBlock(), player);
                onBlockBreak(breakEvent);
                if (breakEvent.isCancelled())
                {
                    igniteEvent.setCancelled(true);
                }
                return;
            }
            // Flammable lightable blocks do not fire EntityChangeBlockEvent when igniting.
            BlockData blockData = igniteEvent.getBlock().getBlockData();
            if (blockData instanceof Lightable lightable)
            {
                // Set lit for resulting data in event. Currently unused, but may be in the future.
                lightable.setLit(true);

                // Call event.
                EntityChangeBlockEvent changeBlockEvent = new EntityChangeBlockEvent(igniteEvent.getIgnitingEntity(), igniteEvent.getBlock(), blockData);
                GriefPrevention.instance.entityEventHandler.onEntityChangeBLock(changeBlockEvent);

                // Respect event result.
                if (changeBlockEvent.isCancelled())
                {
                    igniteEvent.setCancelled(true);
                }
            }
            return;
        }

        if (!GriefPrevention.instance.config_fireSpreads && igniteEvent.getCause() != IgniteCause.FLINT_AND_STEEL && igniteEvent.getCause() != IgniteCause.LIGHTNING && igniteEvent.getCause() != IgniteCause.FIREBALL)
        {
            igniteEvent.setCancelled(true);
        }
    }

    // Prevent spreading blocks (including sculk and fire) from crossing claim ownership.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockSpread(BlockSpreadEvent spreadEvent)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(spreadEvent.getBlock().getWorld())) return;

        Material newType = spreadEvent.getNewState().getType();
        boolean isFire = newType == Material.FIRE;

        // Obey global fire rules.
        if (isFire && !GriefPrevention.instance.config_fireSpreads)
        {
            spreadEvent.setCancelled(true);

            Block underBlock = spreadEvent.getSource().getRelative(BlockFace.DOWN);
            if (underBlock.getType() != Material.NETHERRACK)
            {
                spreadEvent.getSource().setType(Material.AIR);
            }

            return;
        }

        Claim spreadTo = this.dataStore.getClaimAt(spreadEvent.getBlock().getLocation(), false, null);

        // Spreading in unclaimed area is allowed.
        if (spreadTo == null)
        {
            return;
        }

        Claim spreadFrom = this.dataStore.getClaimAt(spreadEvent.getSource().getLocation(), false, spreadTo);

        // Disallow spreading from other users' claims or wilderness into claims.
        if (spreadFrom == null || !Objects.equals(spreadTo.getOwnerID(), spreadFrom.getOwnerID()))
        {
            if (isFire)
            {
                Block source = spreadEvent.getSource();
                if (source.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK)
                {
                    source.setType(Material.AIR);
                }
            }
            spreadEvent.setCancelled(true);
            return;
        }

        // If owners match, also obey claim fire spread rules.
        if (isFire && !GriefPrevention.instance.config_claims_firespreads)
        {
            Block source = spreadEvent.getSource();
            if (source.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK)
            {
                source.setType(Material.AIR);
            }
            spreadEvent.setCancelled(true);
        }
    }

    // Prevent moss bonemeal growth from affecting blocks across claim boundaries.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockFertilize(BlockFertilizeEvent event)
    {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        Player player = event.getPlayer();

        // Determine source claim for non-player sources (e.g., dispensers)
        Claim sourceClaim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);

        for (BlockState state : event.getBlocks())
        {
            Claim targetClaim = this.dataStore.getClaimAt(state.getLocation(), false, sourceClaim);

            if (player != null)
            {
                if (targetClaim != null)
                {
                    Supplier<String> noBuildReason = targetClaim.checkPermission(player, ClaimPermission.Build, event);
                    if (noBuildReason != null)
                    {
                        GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            else
            {
                // No player: allow only if owners match when entering a claim.
                if (targetClaim != null)
                {
                    if (sourceClaim == null || !Objects.equals(targetClaim.getOwnerID(), sourceClaim.getOwnerID()))
                    {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    //blocks are not destroyed by fire, unless configured to do so
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBurn(BlockBurnEvent burnEvent)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(burnEvent.getBlock().getWorld())) return;

        if (!GriefPrevention.instance.config_fireDestroys)
        {
            burnEvent.setCancelled(true);
            Block block = burnEvent.getBlock();
            Block[] adjacentBlocks = new Block[]
                    {
                            block.getRelative(BlockFace.UP),
                            block.getRelative(BlockFace.DOWN),
                            block.getRelative(BlockFace.NORTH),
                            block.getRelative(BlockFace.SOUTH),
                            block.getRelative(BlockFace.EAST),
                            block.getRelative(BlockFace.WEST)
                    };

            //pro-actively put out any fires adjacent the burning block, to reduce future processing here
            for (Block adjacentBlock : adjacentBlocks)
            {
                if (adjacentBlock.getType() == Material.FIRE && adjacentBlock.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK)
                {
                    adjacentBlock.setType(Material.AIR);
                }
            }

            Block aboveBlock = block.getRelative(BlockFace.UP);
            if (aboveBlock.getType() == Material.FIRE)
            {
                aboveBlock.setType(Material.AIR);
            }
            return;
        }

        //never burn claimed blocks, regardless of settings
        if (this.dataStore.getClaimAt(burnEvent.getBlock().getLocation(), false, null) != null)
        {
            if (GriefPrevention.instance.config_claims_firedamages) return;
            burnEvent.setCancelled(true);
        }
    }


    //ensures fluids don't flow into land claims from outside
    private Claim lastSpreadFromClaim = null;
    private Claim lastSpreadToClaim = null;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockFromTo(BlockFromToEvent spreadEvent)
    {
        //always allow fluids to flow straight down
        if (spreadEvent.getFace() == BlockFace.DOWN) return;

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(spreadEvent.getBlock().getWorld())) return;

        //where from and where to?
        Location fromLocation = spreadEvent.getBlock().getLocation();
        Location toLocation = spreadEvent.getToBlock().getLocation();
        boolean isInCreativeRulesWorld = GriefPrevention.instance.creativeRulesApply(toLocation);
        Claim fromClaim = this.dataStore.getClaimAt(fromLocation, false, lastSpreadFromClaim);
        Claim toClaim = this.dataStore.getClaimAt(toLocation, false, lastSpreadToClaim);

        //due to the nature of what causes this event (fluid flow/spread),
        //we'll probably run similar checks for the same pair of claims again,
        //so we cache them to use in claim lookup later
        this.lastSpreadFromClaim = fromClaim;
        this.lastSpreadToClaim = toClaim;

        if (!isFluidFlowAllowed(fromClaim, toClaim, isInCreativeRulesWorld))
        {
            spreadEvent.setCancelled(true);
        }
    }

    /**
     * Determines whether fluid flow is allowed between two claims.
     *
     * @param from The claim at the source location of the fluid flow, or null if it's wilderness.
     * @param to The claim at the destination location of the fluid flow, or null if it's wilderness.
     * @param creativeRulesApply Whether creative rules apply to the world where claims are located.
     * @return `true` if fluid flow is allowed, `false` otherwise.
     */
    private boolean isFluidFlowAllowed(Claim from, Claim to, boolean creativeRulesApply)
    {
        // Special case: if in a world with creative rules,
        // don't allow fluids to flow into wilderness.
        if (creativeRulesApply && to == null) return false;

        // The fluid flow should be allowed or denied based on the specific combination
        // of source and destination claim types. The following matrix outlines these
        // combinations and indicates whether fluid flow should be permitted:
        //
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | From \ To    | Wild | Claim A1 | Sub A1_1 | Sub A1_2 | Sub A1_3 (R) | Claim A2 | Claim B |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Wild         | Yes  | -        | -        | -        | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim A1     | Yes  | Yes      | Yes      | Yes      | -            | Yes      | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_1     | Yes  | -        | Yes      | -        | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_2     | Yes  | -        | -        | Yes      | -            | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Sub A1_3 (R) | Yes  | -        | -        | -        | Yes          | -        | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim A2     | Yes  | Yes      | -        | -        | -            | Yes      | -       |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //   | Claim B      | Yes  | -        | -        | -        | -            | -        | Yes     |
        //   +--------------+------+----------+----------+----------+--------------+----------+---------+
        //
        //   Legend:
        //     Wild = wilderness
        //     Claim A* = claim owned by player A
        //     Sub A*_* = subdivision of Claim A*
        //     (R) = Restricted subdivision
        //     Claim B = claim owned by player B
        //     Yes = fluid flow allowed
        //     - = fluid flow not allowed

        boolean fromWilderness = from == null;
        boolean toWilderness = to == null;
        boolean sameClaim = from != null && to != null && Objects.equals(from.getID(), to.getID());
        boolean sameOwner = from != null && to != null && Objects.equals(from.getOwnerID(), to.getOwnerID());
        boolean isToSubdivision = to != null && to.parent != null;
        boolean isToRestrictedSubdivision = isToSubdivision && to.getSubclaimRestrictions();
        boolean isFromSubdivision = from != null && from.parent != null;

        if (toWilderness) return true;
        if (fromWilderness) return false;
        if (sameClaim) return true;
        if (isFromSubdivision) return false;
        if (isToSubdivision) return !isToRestrictedSubdivision;
        return sameOwner;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onForm(BlockFormEvent event)
    {
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (GriefPrevention.instance.creativeRulesApply(location))
        {
            Material type = block.getType();
            if (type == Material.COBBLESTONE || type == Material.OBSIDIAN || type == Material.LAVA || type == Material.WATER)
            {
                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
                if (claim == null)
                {
                    event.setCancelled(true);
                }
            }
        }
    }

    //Stop projectiles from destroying blocks that don't fire a proper event
    @EventHandler(ignoreCancelled = true)
    private void chorusFlower(ProjectileHitEvent event)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        Block block = event.getHitBlock();

        // Ensure projectile affects block.
        if (block == null || block.getType() != Material.CHORUS_FLOWER)
            return;

        Claim claim = dataStore.getClaimAt(block.getLocation(), false, null);
        if (claim == null)
            return;

        Player shooter = null;
        Projectile projectile = event.getEntity();

        if (projectile.getShooter() instanceof Player)
            shooter = (Player) projectile.getShooter();

        if (shooter == null)
        {
            event.setCancelled(true);
            return;
        }

        Supplier<String> allowContainer = claim.checkPermission(shooter, ClaimPermission.Container, event);

        if (allowContainer != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(shooter, TextMode.Err, allowContainer.get());
            return;
        }
    }

    //ensures dispensers can't be used to dispense a block(like water or lava) or item across a claim boundary
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent dispenseEvent)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(dispenseEvent.getBlock().getWorld())) return;

        //from where?
        Block fromBlock = dispenseEvent.getBlock();
        BlockData fromData = fromBlock.getBlockData();
        if (!(fromData instanceof Dispenser)) return;
        Dispenser dispenser = (Dispenser) fromData;

        //to where?
        Block toBlock = fromBlock.getRelative(dispenser.getFacing());
        Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, null);
        Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, fromClaim);

        //into wilderness is NOT OK in creative mode worlds
        Material materialDispensed = dispenseEvent.getItem().getType();
        if ((materialDispensed == Material.WATER_BUCKET || materialDispensed == Material.LAVA_BUCKET) && GriefPrevention.instance.creativeRulesApply(dispenseEvent.getBlock().getLocation()) && toClaim == null)
        {
            dispenseEvent.setCancelled(true);
            return;
        }

        //wilderness to wilderness is OK
        if (fromClaim == null && toClaim == null) return;

        //within claim is OK
        if (fromClaim == toClaim) return;

        //everything else is NOT OK
        dispenseEvent.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent growEvent)
    {
        //only take these potentially expensive steps if configured to do so
        if (!GriefPrevention.instance.config_limitTreeGrowth) return;

        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(growEvent.getWorld())) return;

        Location rootLocation = growEvent.getLocation();
        Claim rootClaim = this.dataStore.getClaimAt(rootLocation, false, null);
        String rootOwnerName = null;

        //who owns the spreading block, if anyone?
        if (rootClaim != null)
        {
            //tree growth in subdivisions is dependent on who owns the top level claim
            if (rootClaim.parent != null) rootClaim = rootClaim.parent;

            //if an administrative claim, just let the tree grow where it wants
            if (rootClaim.isAdminClaim()) return;

            //otherwise, note the owner of the claim
            rootOwnerName = rootClaim.getOwnerName();
        }

        //for each block growing
        for (int i = 0; i < growEvent.getBlocks().size(); i++)
        {
            BlockState block = growEvent.getBlocks().get(i);
            Claim blockClaim = this.dataStore.getClaimAt(block.getLocation(), false, rootClaim);

            //if it's growing into a claim
            if (blockClaim != null)
            {
                //if there's no owner for the new tree, or the owner for the new tree is different from the owner of the claim
                if (rootOwnerName == null || !rootOwnerName.equals(blockClaim.getOwnerName()))
                {
                    growEvent.getBlocks().remove(i--);
                }
            }
        }
    }

    /**
     * Check if the inventory is a hopper. Extracted for testability since
     * InventoryType requires server initialization in Spigot 1.21.10+.
     */
    protected boolean isHopperInventory(Inventory inventory)
    {
        return inventory.getType() == InventoryType.HOPPER;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event)
    {
        // Prevent hoppers from taking items dropped by players upon death.
        if (!isHopperInventory(event.getInventory()))
        {
            return;
        }

        List<MetadataValue> meta = event.getItem().getMetadata("GP_ITEMOWNER");
        // We only care about an item if it has been flagged as belonging to a player.
        if (meta.isEmpty())
        {
            return;
        }

        UUID itemOwnerId = (UUID) meta.get(0).value();
        // Determine if the owner has unlocked their dropped items.
        // This first requires that the player is logged in.
        if (Bukkit.getServer().getPlayer(itemOwnerId) != null)
        {
            PlayerData itemOwner = dataStore.getPlayerData(itemOwnerId);
            // If locked, don't allow pickup
            if (!itemOwner.dropsAreUnlocked)
            {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemFrameBrokenByBoat(final HangingBreakEvent event)
    {
        // Checks if the event is caused by physics - 90% of cases caused by a boat (other 10% would be block,
        // however since it's in a claim, unless you use a TNT block we don't need to worry about it).
        if (event.getCause() != HangingBreakEvent.RemoveCause.PHYSICS)
        {
            return;
        }

        // Cancels the event if in a claim, as we can not efficiently retrieve the person/entity who broke the Item Frame/Hangable Item.
        if (this.dataStore.getClaimAt(event.getEntity().getLocation(), false, null) != null)
        {
            event.setCancelled(true);
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onNetherPortalCreate(final PortalCreateEvent event)
    {
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR)
        {
            return;
        }

        // Ignore this event if preventNonPlayerCreatedPortals config option is disabled, and we don't know the entity.
        if (!(event.getEntity() instanceof Player) && !GriefPrevention.instance.config_claims_preventNonPlayerCreatedPortals)
        {
            return;
        }

        for (BlockState blockState : event.getBlocks())
        {
            Claim claim = this.dataStore.getClaimAt(blockState.getLocation(), false, null);
            if (claim != null)
            {
                if (event.getEntity() instanceof Player player)
                {
                    Supplier<String> noPortalReason = claim.checkPermission(player, ClaimPermission.Build, event);

                    if (noPortalReason != null)
                    {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(player, TextMode.Err, noPortalReason.get());
                        return;
                    }
                }
                else
                {
                    // Cancels the event if in a claim, as we can not efficiently retrieve the person/entity who created the portal.
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
