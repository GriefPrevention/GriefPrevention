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
import com.griefprevention.protection.ProtectionHelper;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

//event handlers related to blocks
public class BlockEventHandler implements Listener
{

    protected static final Set<Material> TRASH_BLOCKS;

    static
    {
        //create the list of blocks which will not trigger a warning when they're placed outside of land claims
        TRASH_BLOCKS = new HashSet<>();
        TRASH_BLOCKS.add(Material.COBBLESTONE);
        TRASH_BLOCKS.add(Material.TORCH);
        TRASH_BLOCKS.add(Material.DIRT);
        TRASH_BLOCKS.addAll(Tag.SAPLINGS.getValues());
        TRASH_BLOCKS.add(Material.GRAVEL);
        TRASH_BLOCKS.add(Material.SAND);
        TRASH_BLOCKS.add(Material.SANDSTONE);
        TRASH_BLOCKS.add(Material.TNT);
        TRASH_BLOCKS.add(Material.CRAFTING_TABLE);
        TRASH_BLOCKS.add(Material.TUFF);
        TRASH_BLOCKS.add(Material.COBBLED_DEEPSLATE);
    }

    //convenience reference to singleton datastore
    private final DataStore dataStore;

    //constructor
    public BlockEventHandler(DataStore dataStore)
    {
        this.dataStore = dataStore;

    }

    //when a player breaks a block...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent breakEvent)
    {
        Player player = breakEvent.getPlayer();
        Block block = breakEvent.getBlock();

        //make sure the player is allowed to break at the location
        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, breakEvent);
        if (noBuildReason != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
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

        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, sign.getLocation(), ClaimPermission.Build, event);
        if (noBuildReason != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
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
            Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, placeEvent);
            if (noBuildReason != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
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
        if (Tag.FIRE.isTagged(block.getType()) && !doesAllowFireProximityInWorld(block.getWorld()))
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
        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, placeEvent);
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
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
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

            //warn about TNT not destroying claimed blocks (10 minute cooldown)

            Long now = null;
            if (block.getType() == Material.TNT && !claim.areExplosivesAllowed && (playerData.explosivesWarningTimestamp == null || (now = System.currentTimeMillis()) - playerData.explosivesWarningTimestamp > 600000))
            {
                if (now == null) now = System.currentTimeMillis();
                playerData.explosivesWarningTimestamp = now;

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
            if (GriefPrevention.instance.config_claims_preventTheft && block.getY() < GriefPrevention.instance.getMinY(block.getWorld()))
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.TooDeepToClaim);
                return;
            }

            int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;

            //if the player doesn't have any claims yet, automatically create a claim centered at the chest
            if (playerData.getClaims().isEmpty() && player.getGameMode() == GameMode.SURVIVAL)
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
            if (earthBlock.getType() != Material.SHORT_GRASS)
            {
                if (earthBlock.getRelative(BlockFace.DOWN).getType() == Material.AIR ||
                        earthBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType() == Material.AIR)
                {
                    placeEvent.setCancelled(true);
                }
            }
        }

        //FEATURE: warn players when they're placing non-trash blocks outside of their claimed areas
        else if (!this.TRASH_BLOCKS.contains(block.getType()) && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld()))
        {
            if (!playerData.warnedAboutBuildingOutsideClaims && !player.hasPermission("griefprevention.adminclaims")
                    && player.hasPermission("griefprevention.createclaims") && ((playerData.lastClaim == null
                    && playerData.getClaims().isEmpty()) || (playerData.lastClaim != null
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
                claim == null)
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
        if (block.getBlockData() instanceof Chest chest)
        {
            for (BlockFace face : HORIZONTAL_DIRECTIONS)
            {
                Block relative = block.getRelative(face);
                if (!(relative.getBlockData() instanceof Chest relativeChest)) continue;

                Claim relativeClaim = this.dataStore.getClaimAt(relative.getLocation(), true, claim);
                UUID relativeClaimOwner = relativeClaim == null ? null : relativeClaim.getOwnerID();

                // Chests outside claims should connect (both null)
                // and chests inside the same claim should connect (equal)
                if (Objects.equals(claimOwner, relativeClaimOwner)) break;

                // Change both chests to singular chests
                chest.setType(Chest.Type.SINGLE);
                block.setBlockData(chest);

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

        BiPredicate<Claim, BoundingBox> intersectionHandler;
        if (pistonMode == PistonMode.EVERYWHERE_SIMPLE)
        {
            // Fast mode: Bounding box intersection always causes a conflict, even if blocks do not conflict.
            intersectionHandler = denyOtherOwnerIntersection(pistonClaim);
        }
        else
        {
            // Precise mode: Bounding box intersection may not yield a conflict. Individual blocks must be considered.
            intersectionHandler = precisePistonIntersection(pistonBlock, pistonClaim, blocks, event);
        }

        if (boxConflictsWithClaims(pistonBlock.getWorld(), movedBlocks, pistonClaim, intersectionHandler))
        {
            event.setCancelled(true);
        }
    }

    /**
     * Check if claims conflict with a given BoundingBox.
     *
     * @param world the world
     * @param boundingBox the area that may intersect a claim
     * @param initiatingClaim the claim from which the action was initiated
     * @param precisePredicate a more accurate measure determining if a conflict actually occurs
     * @return true if a claim is determined to be intersecting with the bounding box
     */
    private boolean boxConflictsWithClaims(
            @NotNull World world,
            @NotNull BoundingBox boundingBox,
            @Nullable Claim initiatingClaim,
            @NotNull BiPredicate<@NotNull Claim, @NotNull BoundingBox> precisePredicate)
    {
        // Check potentially intersecting claims from chunks interacted with.
        Set<Claim> chunkClaims = dataStore.getChunkClaims(world, boundingBox);
        if (initiatingClaim != null)
        {
            chunkClaims.remove(initiatingClaim);
        }

        for (Claim claim : chunkClaims)
        {
            BoundingBox claimBoundingBox = new BoundingBox(claim);

            // Ensure claim intersects with block bounding box.
            if (!claimBoundingBox.intersects(boundingBox)) continue;

            // Do additional mode-based handling.
            if (precisePredicate.test(claim, claimBoundingBox)) return true;
        }

        return false;
    }

    /**
     * Any conflict with another user's claim is a conflict.
     *
     * @param initiatingClaim the claim from which the move was initiated
     * @return a {@link BiPredicate} accepting a {@link Claim} and {@link BoundingBox}
     */
    private @NotNull BiPredicate<@NotNull Claim, @NotNull BoundingBox> denyOtherOwnerIntersection(
            @Nullable Claim initiatingClaim)
    {
        return (claim, claimBoundingBox) ->
        {
            // If owners are different, cancel.
            return initiatingClaim == null || !Objects.equals(initiatingClaim.getOwnerID(), claim.getOwnerID());
        };
    }

    /**
     * Precise mode: Individual blocks are considered when determining if a conflict occurs.
     *
     * @param pistonBlock the piston block
     * @param pistonClaim the claim that the piston is in
     * @param blocks the affected blocks
     * @param event the event
     * @return a {@link BiPredicate} accepting a {@link Claim} and {@link BoundingBox}
     */
    private @NotNull BiPredicate<@NotNull Claim, @NotNull BoundingBox> precisePistonIntersection(
            @NotNull Block pistonBlock,
            @Nullable Claim pistonClaim,
            @NotNull Collection<@NotNull Block> blocks,
            @NotNull BlockPistonEvent event)
    {
        // Set up list of affected blocks.
        HashSet<Block> checkBlocks = new HashSet<>(blocks);

        // Add all blocks that will be occupied after the shift.
        for (Block block : blocks)
        {
            if (block.getPistonMoveReaction() != PistonMoveReaction.BREAK)
            {
                checkBlocks.add(block.getRelative(event.getDirection()));
            }
        }

        return (claim, claimBoundingBox) ->
        {
            // Ensure that the claim contains an affected block.
            if (containsNone(claimBoundingBox, checkBlocks)) return false;

            // If pushing this block will change ownership, "explode" the piston for performance reasons.
            if (pistonClaim == null || !Objects.equals(pistonClaim.getOwnerID(), claim.getOwnerID()))
            {
                if (GriefPrevention.instance.config_pistonExplosionSound)
                {
                    pistonBlock.getWorld().createExplosion(pistonBlock.getLocation(), 0);
                }
                pistonBlock.getWorld().dropItem(
                        pistonBlock.getLocation(),
                        new ItemStack(event.isSticky() ? Material.STICKY_PISTON : Material.PISTON));
                pistonBlock.setType(Material.AIR);
                return true;
            }

            // Otherwise, proceed to next claim.
            return false;
        };
    }

    private boolean containsNone(@NotNull BoundingBox boundingBox, @NotNull Collection<@NotNull Block> blocks)
    {
        for (Block block : blocks)
        {
            if (boundingBox.contains(block))
            {
                return false;
            }
        }

        return true;
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

    private Claim lastBlockFertilizeClaim = null;
    @EventHandler(priority = EventPriority.LOWEST)
    private void onBlockFertilize(@NotNull BlockFertilizeEvent event)
    {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        // Trees are handled by the StructureGrowEvent handler.
        if (Tag.SAPLINGS.isTagged(event.getBlock().getType())) return;

        onMultiBlockGrow(
                event.getPlayer(),
                event.getBlock(),
                event.getBlocks(),
                event,
                sourceClaim ->
                {
                    if (sourceClaim != null)
                    {
                        lastBlockFertilizeClaim = sourceClaim;
                    }
                });
    }

    private <T extends Event & Cancellable> void onMultiBlockGrow(
            @Nullable Player player,
            @NotNull Block source,
            @NotNull Collection<BlockState> states,
            @NotNull T event,
            @NotNull Consumer<Claim> cancelSourceConsumer)
    {
        Claim sourceClaim = null;
        BoundingBox box = BoundingBox.ofStates(states);
        BiPredicate<@NotNull Claim, @NotNull BoundingBox> conflictCheck;
        if (player != null)
        {
            // If a player is present, check their permission in affected claims.
            conflictCheck = (claim, boundingBox) ->
            {
                Supplier<String> supplier = claim.checkPermission(player, ClaimPermission.Build, event);
                if (supplier != null)
                {
                    // Warn when denied access to a claim.
                    GriefPrevention.sendMessage(player, TextMode.Err, supplier.get());
                    return true;
                }
                return false;
            };
        }
        else
        {
            // If no player is present (dispenser, natural growth, etc.), use owner comparison.
            sourceClaim = this.dataStore.getClaimAt(source.getLocation(), false, false, lastBlockFertilizeClaim);
            conflictCheck = denyOtherOwnerIntersection(sourceClaim);
        }

        if (boxConflictsWithClaims(source.getWorld(), box, sourceClaim, conflictCheck))
        {
            event.setCancelled(true);
            cancelSourceConsumer.accept(sourceClaim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTreeGrow(@NotNull StructureGrowEvent event)
    {
        // Only take these potentially expensive steps if configured to do so.
        if (!GriefPrevention.instance.config_limitTreeGrowth) return;

        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getWorld())) return;

        Block source = event.getLocation().getBlock();
        onMultiBlockGrow(
                event.getPlayer(),
                source,
                event.getBlocks(),
                event,
                // Break the initiator to prevent repeat checks. As these are saplings, chorus flowers, etc. no special
                // tool should be required to return the sapling in the event that this is unintentional grief.
                sourceClaim -> source.breakNaturally());
    }

    private Claim lastBlockSpreadClaim = null;
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockSpread(@NotNull BlockSpreadEvent spreadEvent)
    {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(spreadEvent.getBlock().getWorld())) return;

        Material newType = spreadEvent.getNewState().getType();
        // Ignore grass growth. Grass is inoffensive and causes the majority of normal spread events.
        if (newType == Material.GRASS_BLOCK) return;

        // Check for fire against new state so that we catch more edge cases with unexpected world modifications.
        // Note that soul fire does not spread.
        boolean isFire = newType == Material.FIRE;

        // Obey global fire rules.
        if (isFire && !GriefPrevention.instance.config_fireSpreads)
        {
            extinguishFiniteFire(spreadEvent.getSource());
            spreadEvent.setCancelled(true);
            return;
        }

        Claim spreadTo = this.dataStore.getClaimAt(spreadEvent.getBlock().getLocation(), false, true, lastBlockSpreadClaim);

        // Spreading in unclaimed area is allowed.
        if (spreadTo == null) {
            return;
        }

        // Cache claim to reduce the strain of repeated attempts.
        lastBlockSpreadClaim = spreadTo;

        Claim spreadFrom = this.dataStore.getClaimAt(spreadEvent.getSource().getLocation(), false, true, spreadTo);

        // Disallow spreading from other users' claims.
        if (spreadFrom == null || !Objects.equals(spreadTo.getOwnerID(), spreadFrom.getOwnerID()))
        {
            if (isFire) extinguishFiniteFire(spreadEvent.getSource());
            spreadEvent.setCancelled(true);
            return;
        }

        // If owners match, also obey claim fire spread rules.
        if (isFire && !GriefPrevention.instance.config_claims_firespreads)
        {
            extinguishFiniteFire(spreadEvent.getSource());
            spreadEvent.setCancelled(true);
        }
    }

    private void extinguishFiniteFire(@NotNull Block fire)
    {
        if (fire.getType() != Material.FIRE) return;

        Block underBlock = fire.getRelative(BlockFace.DOWN);
        Tag<Material> infiniburn = switch (fire.getWorld().getEnvironment())
                {
                    case NETHER -> Tag.INFINIBURN_NETHER;
                    case THE_END -> Tag.INFINIBURN_END;
                    default -> Tag.INFINIBURN_OVERWORLD;
                };

        if (!infiniburn.isTagged(underBlock.getType()))
        {
            fire.setType(Material.AIR);
        }
    }

    //blocks are not destroyed by fire, unless configured to do so
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBurn(@NotNull BlockBurnEvent burnEvent)
    {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(burnEvent.getBlock().getWorld())) return;

        // Obey global fire rules.
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

            // Proactively put out any fires adjacent to the burning block now to reduce future processing.
            for (Block adjacentBlock : adjacentBlocks)
            {
                extinguishFiniteFire(adjacentBlock);
            }
            return;
        }

        Claim burnClaim = this.dataStore.getClaimAt(burnEvent.getBlock().getLocation(), false, null);
        if (burnClaim != null)
        {
            // Only burn claimed blocks if configured to do so.
            if (!GriefPrevention.instance.config_claims_firedamages)
            {
                burnEvent.setCancelled(true);
                return;
            }

            // In the event of spontaneous combustion, allow burning.
            if (burnEvent.getIgnitingBlock() == null) return;

            // If source is external, i.e. wall on the claim border lit on fire from outside, do not allow.
            Claim burningClaim = this.dataStore.getClaimAt(burnEvent.getIgnitingBlock().getLocation(), false, burnClaim);
            if (burningClaim == null || !Objects.equals(burnClaim.getOwnerID(), burningClaim.getOwnerID()))
            {
                burnEvent.setCancelled(true);
            }
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
        if (block == null || (block.getType() != Material.CHORUS_FLOWER  && block.getType() != Material.DECORATED_POT))
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
        if (!(fromData instanceof Dispenser dispenser)) return;

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
    public void onInventoryPickupItem(InventoryPickupItemEvent event)
    {
        // Prevent hoppers from taking items dropped by players upon death.
        if (event.getInventory().getType() != InventoryType.HOPPER)
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
    public void onNetherPortalCreate(final @NotNull PortalCreateEvent event)
    {
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR)
        {
            return;
        }

        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getWorld())) return;

        // Ignore this event if preventNonPlayerCreatedPortals config option is disabled, and we don't know the entity.
        if (!(event.getEntity() instanceof Player) && !GriefPrevention.instance.config_claims_preventNonPlayerCreatedPortals)
        {
            return;
        }

        BiPredicate<Claim, BoundingBox> predicate;
        Entity entity = event.getEntity();
        if (entity == null)
        {
            // No entity always means denial.
            predicate = (claim, claimBoundingBox) -> true;
        }
        else if (entity instanceof Player player)
        {
            predicate = (claim, claimBoundingBox) ->
            {
                Supplier<String> noPortalReason = claim.checkPermission(player, ClaimPermission.Build, event);

                if (noPortalReason != null)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, noPortalReason.get());
                    player.setPortalCooldown(40);
                    return true;
                }

                return false;
            };
        }
        else
        {
            predicate = (claim, claimBoundingBox) ->
            {
                // Non-player entities are denied and set on portal cooldown to prevent repeated attempts.
                entity.setPortalCooldown(100);
                return true;
            };
        }

        BoundingBox box = BoundingBox.ofStates(event.getBlocks());
        if (boxConflictsWithClaims(event.getWorld(), box, null, predicate))
        {
            event.setCancelled(true);
        }
    }
}
