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

import me.ryanhamshire.GriefPrevention.events.ProtectDeathDropsEvent;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

//handles events related to entities
public class EntityEventHandler implements Listener
{
    //convenience reference for the singleton datastore
    private final DataStore dataStore;
    private final GriefPrevention instance;

    public EntityEventHandler(DataStore dataStore, GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        instance = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityFormBlock(EntityBlockFormEvent event)
    {
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.PLAYER)
        {
            Player player = (Player) event.getEntity();
            String noBuildReason = GriefPrevention.instance.allowBuild(player, event.getBlock().getLocation(), event.getNewState().getType());
            if (noBuildReason != null)
            {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityChangeBLock(EntityChangeBlockEvent event)
    {
        if (!GriefPrevention.instance.config_endermenMoveBlocks && event.getEntityType() == EntityType.ENDERMAN)
        {
            event.setCancelled(true);
        }
        else if (!GriefPrevention.instance.config_silverfishBreakBlocks && event.getEntityType() == EntityType.SILVERFISH)
        {
            event.setCancelled(true);
        }
        else if (!GriefPrevention.instance.config_rabbitsEatCrops && event.getEntityType() == EntityType.RABBIT)
        {
            event.setCancelled(true);
        }
        else if (!GriefPrevention.instance.config_claims_ravagersBreakBlocks && event.getEntityType() == EntityType.RAVAGER)
        {
            event.setCancelled(true);
        }
        // All other handling depends on claims being enabled.
        else if (GriefPrevention.instance.config_claims_worldModes.get(event.getBlock().getWorld()) == ClaimsMode.Disabled)
        {
            return;
        }

        // Handle projectiles changing blocks: TNT ignition, tridents knocking down pointed dripstone, etc.
        if (event.getEntity() instanceof Projectile)
        {
            handleProjectileChangeBlock(event, (Projectile) event.getEntity());
        }

        else if (event.getEntityType() == EntityType.WITHER)
        {
            Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
            if(claim != null) {
                if (!claim.areExplosivesAllowed || !GriefPrevention.instance.config_blockClaimExplosions)
                {
                    event.setCancelled(true);
                }
            }
        }

        //don't allow crops to be trampled, except by a player with build permission
        else if (event.getTo() == Material.DIRT && event.getBlock().getType() == Material.FARMLAND)
        {
            if (event.getEntityType() != EntityType.PLAYER)
            {
                event.setCancelled(true);
            }
            else
            {
                Player player = (Player) event.getEntity();
                Block block = event.getBlock();
                if (GriefPrevention.instance.allowBreak(player, block, block.getLocation()) != null)
                {
                    event.setCancelled(true);
                }
            }
        }

        // Prevent melting powdered snow.
        else if (event.getBlock().getType() == Material.POWDER_SNOW && event.getTo() == Material.AIR)
        {
            handleEntityMeltPowderedSnow(event);
        }

        // Prevent breaking lily pads via collision with a boat.
        else if (event.getEntity() instanceof Vehicle && !event.getEntity().getPassengers().isEmpty())
        {
            Entity driver = event.getEntity().getPassengers().get(0);
            if (driver instanceof Player)
            {
                Block block = event.getBlock();
                if (GriefPrevention.instance.allowBreak((Player) driver, block, block.getLocation()) != null)
                {
                    event.setCancelled(true);
                }
            }
        }

        //sand cannon fix - when the falling block doesn't fall straight down, take additional anti-grief steps
        else if (event.getEntityType() == EntityType.FALLING_BLOCK)
        {
            FallingBlock entity = (FallingBlock) event.getEntity();
            Block block = event.getBlock();

            //if changing a block TO air, this is when the falling block formed.  note its original location
            if (event.getTo() == Material.AIR)
            {
                entity.setMetadata("GP_FALLINGBLOCK", new FixedMetadataValue(GriefPrevention.instance, block.getLocation()));
            }
            //otherwise, the falling block is forming a block.  compare new location to original source
            else
            {
                List<MetadataValue> values = entity.getMetadata("GP_FALLINGBLOCK");
                //if we're not sure where this entity came from (maybe another plugin didn't follow the standard?), allow the block to form
                //Or if entity fell through an end portal, allow it to form, as the event is erroneously fired twice in this scenario.
                if (values.size() < 1) return;

                Location originalLocation = (Location) (values.get(0).value());
                Location newLocation = block.getLocation();

                //if did not fall straight down
                if (originalLocation.getBlockX() != newLocation.getBlockX() || originalLocation.getBlockZ() != newLocation.getBlockZ())
                {
                    //in creative mode worlds, never form the block
                    if (GriefPrevention.instance.config_claims_worldModes.get(newLocation.getWorld()) == ClaimsMode.Creative)
                    {
                        event.setCancelled(true);
                        entity.remove();
                        return;
                    }

                    //in other worlds, if landing in land claim, only allow if source was also in the land claim
                    Claim claim = this.dataStore.getClaimAt(newLocation, false, null);
                    if (claim != null && !claim.contains(originalLocation, false, false))
                    {
                        //when not allowed, drop as item instead of forming a block
                        event.setCancelled(true);

                        // Just in case, skip already dead entities.
                        if (entity.isDead())
                        {
                            return;
                        }

                        // Remove entity so it doesn't continuously spawn drops.
                        entity.remove();

                        ItemStack itemStack = new ItemStack(entity.getBlockData().getMaterial(), 1);
                        block.getWorld().dropItemNaturally(entity.getLocation(), itemStack);
                    }
                }
            }
        }
    }

    /**
     * Check if an entity is a protected "animal" type for container trust purposes.
     * Includes animals, fish, allays, copper golems, and similar passive/utility mobs.
     *
     * @param entity the {@code Entity}
     * @return true if the {@code Entity} is a protected animal type
     */
    public static boolean isProtectedAnimal(@NotNull Entity entity)
    {
        return entity instanceof Animals
            || entity instanceof Fish
            || entity instanceof Allay
            || entity instanceof CopperGolem;
    }

    private void handleProjectileChangeBlock(EntityChangeBlockEvent event, Projectile projectile)
    {
        Block block = event.getBlock();
        Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);

        // Wilderness rules
        if (claim == null)
        {
            // No modification in the wilderness in creative mode.
            if (instance.creativeRulesApply(block.getLocation()) || instance.config_claims_worldModes.get(block.getWorld()) == ClaimsMode.SurvivalRequiringClaims)
            {
                event.setCancelled(true);
                return;
            }

            // Unclaimed area is fair game.
            return;
        }

        ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Player)
        {
            Supplier<String> denial = claim.checkPermission((Player) shooter, ClaimPermission.Build, event);

            // If the player cannot place the material being broken, disallow.
            if (denial != null)
            {
                // Unlike entities where arrows rebound and may cause multiple alerts,
                // projectiles lodged in blocks do not continuously re-trigger events.
                GriefPrevention.sendMessage((Player) shooter, TextMode.Err, denial.get());
                event.setCancelled(true);
            }

            return;
        }

        // Allow change if projectile was shot by a dispenser in the same claim.
        if (isBlockSourceInClaim(shooter, claim))
            return;

        // Allow change if the config value is set, to enable things like TNT music disc farms on claims.
        if (GriefPrevention.instance.config_mobProjectilesChangeBlocks && shooter instanceof Mob)
            return;

        // Prevent change in all other cases.
        event.setCancelled(true);
    }

    private void handleEntityMeltPowderedSnow(@NotNull EntityChangeBlockEvent event)
    {
        // Note: this does not handle flaming arrows; they are handled earlier by #handleProjectileChangeBlock
        Player player = null;
        if (event.getEntity() instanceof Player localPlayer)
        {
            player = localPlayer;
        }
        else if (event.getEntity() instanceof Mob mob)
        {
            // Handle players leading packs of zombies.
            if (mob.getTarget() instanceof Player localPlayer)
                player = localPlayer;
            // Handle players leading burning leashed entities.
            else if (mob.isLeashed() && mob.getLeashHolder() instanceof Player localPlayer)
                player = localPlayer;
        }

        if (player != null)
        {
            Block block = event.getBlock();
            if (GriefPrevention.instance.allowBreak(player, block, block.getLocation()) != null)
            {
                event.setCancelled(true);
            }
        }
        else
        {
            // Unhandled case, i.e. skeletons on fire due to sunlight lose target to search for cover.
            // Possible to handle by tagging entities during combustion, but likely not worth it.
            event.setCancelled(true);
        }
    }

    static boolean isBlockSourceInClaim(@Nullable ProjectileSource projectileSource, @Nullable Claim claim)
    {
        return projectileSource instanceof BlockProjectileSource &&
                GriefPrevention.instance.dataStore.getClaimAt(((BlockProjectileSource) projectileSource).getBlock().getLocation(), false, claim) == claim;
    }

    //Used by "sand cannon" fix to ignore fallingblocks that fell through End Portals
    //This is largely due to a CB issue with the above event
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFallingBlockEnterPortal(EntityPortalEnterEvent event)
    {
        if (event.getEntityType() != EntityType.FALLING_BLOCK)
            return;
        event.getEntity().removeMetadata("GP_FALLINGBLOCK", instance);
    }

    //don't allow zombies to break down doors
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onZombieBreakDoor(EntityBreakDoorEvent event)
    {
        if (!GriefPrevention.instance.config_zombiesBreakDoors) event.setCancelled(true);
    }

    //don't allow entities to trample crops
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityInteract(EntityInteractEvent event)
    {
        Material material = event.getBlock().getType();
        if (material == Material.FARMLAND)
        {
            if (!GriefPrevention.instance.config_creaturesTrampleCrops)
            {
                event.setCancelled(true);
            }
            else
            {
                Entity rider = event.getEntity().getPassenger();
                if (rider != null && rider.getType() == EntityType.PLAYER)
                {
                    event.setCancelled(true);
                }
            }
        }
    }

    //when an entity explodes...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent explodeEvent)
    {
        // If there aren't any affected blocks, there's nothing to do. Vanilla mob griefing rule causes this.
        if (explodeEvent.blockList().isEmpty()) return;

        // Explosion causes interactable blocks (levers, buttons, etc.) to change state.
        if (explodeEvent.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK)
        {
            handleExplodeInteract(explodeEvent.getLocation(), explodeEvent.getEntity(), explodeEvent.blockList(), explodeEvent);
        }
        // Explosion damages world.
        else
        {
            handleExplosion(explodeEvent.getLocation(), explodeEvent.getEntity(), explodeEvent.blockList());
        }
    }

    //when a block explodes...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent explodeEvent)
    {
        // If there aren't any affected blocks, there's nothing to do. Vanilla mob griefing rule causes this.
        if (explodeEvent.blockList().isEmpty()) return;

        // Explosion causes interactable blocks (levers, buttons, etc.) to change state.
        if (explodeEvent.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK)
        {
            handleExplodeInteract(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList(), explodeEvent);
        }
        // Explosion damages world.
        else
        {
            handleExplosion(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList());
        }
    }

    void handleExplodeInteract(@NotNull Location location, @Nullable Entity entity, @NotNull List<Block> blocks, @NotNull Event event)
    {
        World world = location.getWorld();

        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        Player player = null;
        PlayerData playerData = null;
        ProjectileSource source = null;
        if (entity instanceof Projectile projectile)
        {
            source = projectile.getShooter();
            if (source instanceof Player)
            {
                player = (Player) source;
                playerData = dataStore.getPlayerData(player.getUniqueId());
            }
        }

        List<Block> removed = new ArrayList<>();
        Claim cachedClaim = playerData != null ? playerData.lastClaim : null;

        for (Block block : blocks)
        {
            // Always ignore air blocks.
            if (block.getType().isAir()) continue;

            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);

            // Is it in a land claim?
            if (claim == null) continue;

            cachedClaim = claim;

            if (player == null)
            {
                // If the source is not part of the claim, prevent interaction.
                if (!isBlockSourceInClaim(source, claim))
                    removed.add(block);
                continue;
            }

            // If the player is not allowed to interact with blocks, prevent interaction.
            if (claim.checkPermission(player, ClaimPermission.Access, event) != null)
                removed.add(block);
        }

        if (playerData != null && cachedClaim != null)
            playerData.lastClaim = cachedClaim;

        blocks.removeAll(removed);
    }

    void handleExplosion(@NotNull Location location, @Nullable Entity entity, @NotNull List<Block> blocks)
    {
        //only applies to claims-enabled worlds
        World world = location.getWorld();

        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        //FEATURE: explosions don't destroy surface blocks by default
        boolean isCreeper = (entity != null && entity.getType() == EntityType.CREEPER);

        boolean applySurfaceRules = world.getEnvironment() == Environment.NORMAL && ((isCreeper && GriefPrevention.instance.config_blockSurfaceCreeperExplosions) || (!isCreeper && GriefPrevention.instance.config_blockSurfaceOtherExplosions));

        //special rule for creative worlds: explosions don't destroy anything
        if (GriefPrevention.instance.creativeRulesApply(location))
        {
            for (int i = 0; i < blocks.size(); i++)
            {
                blocks.remove(i--);
            }

            return;
        }

        //make a list of blocks which were allowed to explode
        List<Block> explodedBlocks = new ArrayList<>();
        Claim cachedClaim = null;
        for (Block block : blocks)
        {
            //always ignore air blocks
            if (block.getType().isAir()) continue;

            //is it in a land claim?
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);
            if (claim != null)
            {
                cachedClaim = claim;
            }

            //if yes, apply claim exemptions if they should apply
            if (claim != null && (claim.areExplosivesAllowed || !GriefPrevention.instance.config_blockClaimExplosions))
            {
                explodedBlocks.add(block);
                continue;
            }

            //if claim is under siege, allow soft blocks to be destroyed
            if (claim != null && claim.siegeData != null)
            {
                Material material = block.getType();
                boolean breakable = GriefPrevention.instance.config_siege_blocks.contains(material);

                if (breakable) continue;
            }

            //if no, then also consider surface rules
            if (claim == null)
            {
                if (!applySurfaceRules || block.getLocation().getBlockY() < GriefPrevention.instance.getSeaLevel(world) - 7)
                {
                    explodedBlocks.add(block);
                }
            }
        }

        //clear original damage list and replace with allowed damage list
        blocks.clear();
        blocks.addAll(explodedBlocks);
    }

    //when an item spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event)
    {
        //if in a creative world, cancel the event (don't drop items on the ground)
        if (GriefPrevention.instance.creativeRulesApply(event.getLocation()))
        {
            event.setCancelled(true);
        }

        //if item is on watch list, apply protection
        ArrayList<PendingItemProtection> watchList = GriefPrevention.instance.pendingItemWatchList;
        Item newItem = event.getEntity();
        Long now = null;
        for (int i = 0; i < watchList.size(); i++)
        {
            PendingItemProtection pendingProtection = watchList.get(i);
            //ignore and remove any expired pending protections
            if (now == null) now = System.currentTimeMillis();
            if (pendingProtection.expirationTimestamp < now)
            {
                watchList.remove(i--);
                continue;
            }
            //skip if item stack doesn't match
            if (pendingProtection.itemStack.getAmount() != newItem.getItemStack().getAmount() ||
                    pendingProtection.itemStack.getType() != newItem.getItemStack().getType())
            {
                continue;
            }

            //skip if new item location isn't near the expected spawn area
            Location spawn = event.getLocation();
            Location expected = pendingProtection.location;
            if (!spawn.getWorld().equals(expected.getWorld()) ||
                    spawn.getX() < expected.getX() - 5 ||
                    spawn.getX() > expected.getX() + 5 ||
                    spawn.getZ() < expected.getZ() - 5 ||
                    spawn.getZ() > expected.getZ() + 5 ||
                    spawn.getY() < expected.getY() - 15 ||
                    spawn.getY() > expected.getY() + 3)
            {
                continue;
            }

            //otherwise, mark item with protection information
            newItem.setMetadata("GP_ITEMOWNER", new FixedMetadataValue(GriefPrevention.instance, pendingProtection.owner));

            //and remove pending protection data
            watchList.remove(i);
            break;
        }
    }

    //when an experience bottle explodes...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExpBottle(ExpBottleEvent event)
    {
        //if in a creative world, cancel the event (don't drop exp on the ground)
        if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation()))
        {
            event.setExperience(0);
        }
    }

    //when a creature spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(CreatureSpawnEvent event)
    {
        //these rules apply only to creative worlds
        if (!GriefPrevention.instance.creativeRulesApply(event.getLocation())) return;

        //chicken eggs and breeding could potentially make a mess in the wilderness, once griefers get involved
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.BUILD_IRONGOLEM && reason != SpawnReason.BUILD_SNOWMAN && event.getEntityType() != EntityType.ARMOR_STAND)
        {
            event.setCancelled(true);
            return;
        }

        //otherwise, just apply the limit on total entities per claim (and no spawning in the wilderness!)
        Claim claim = this.dataStore.getClaimAt(event.getLocation(), false, null);
        if (claim == null || claim.allowMoreEntities(true) != null)
        {
            event.setCancelled(true);
            return;
        }
    }

    //when an entity dies...
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event)
    {
        LivingEntity entity = event.getEntity();

        //don't do the rest in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(entity.getWorld())) return;

        //special rule for creative worlds: killed entities don't drop items or experience orbs
        if (GriefPrevention.instance.creativeRulesApply(entity.getLocation()))
        {
            event.setDroppedExp(0);
            event.getDrops().clear();
        }

        //FEATURE: when a player is involved in a siege (attacker or defender role)
        //his death will end the siege

        if (entity.getType() != EntityType.PLAYER) return;  //only tracking players

        Player player = (Player) entity;
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        //if involved in a siege
        if (playerData.siegeData != null)
        {
            //end it, with the dieing player being the loser
            this.dataStore.endSiege(playerData.siegeData, null, player.getName(), event.getDrops());
        }

        //FEATURE: lock dropped items to player who dropped them

        World world = entity.getWorld();

        //decide whether or not to apply this feature to this situation (depends on the world where it happens)
        boolean isPvPWorld = GriefPrevention.instance.pvpRulesApply(world);
        if ((isPvPWorld && GriefPrevention.instance.config_lockDeathDropsInPvpWorlds) ||
                (!isPvPWorld && GriefPrevention.instance.config_lockDeathDropsInNonPvpWorlds))
        {
            Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            ProtectDeathDropsEvent protectionEvent = new ProtectDeathDropsEvent(claim);
            Bukkit.getPluginManager().callEvent(protectionEvent);
            if (!protectionEvent.isCancelled())
            {
                //remember information about these drops so that they can be marked when they spawn as items
                long expirationTime = System.currentTimeMillis() + 3000;  //now + 3 seconds
                Location deathLocation = player.getLocation();
                UUID playerID = player.getUniqueId();
                List<ItemStack> drops = event.getDrops();
                for (ItemStack stack : drops)
                {
                    GriefPrevention.instance.pendingItemWatchList.add(
                            new PendingItemProtection(deathLocation, playerID, expirationTime, stack));
                }

                //allow the player to receive a message about how to unlock any drops
                playerData.dropsAreUnlocked = false;
                playerData.receivedDropUnlockAdvertisement = false;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onItemMerge(ItemMergeEvent event)
    {
        Item item = event.getEntity();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");
        event.setCancelled(data != null && data.size() > 0);
    }

    //when an entity picks up an item
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickup(EntityChangeBlockEvent event)
    {
        //FEATURE: endermen don't steal claimed blocks

        //if its an enderman
        if (event.getEntity().getType() == EntityType.ENDERMAN)
        {
            //and the block is claimed
            if (this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null) != null)
            {
                //he doesn't get to steal it
                event.setCancelled(true);
            }
        }
    }

    //when a painting is broken
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHangingBreak(HangingBreakEvent event)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        //Ignore cases where itemframes should break due to no supporting blocks
        if (event.getCause() == RemoveCause.PHYSICS) return;

        //FEATURE: claimed paintings are protected from breakage

        //explosions don't destroy hangings
        if (event.getCause() == RemoveCause.EXPLOSION)
        {
            event.setCancelled(true);
            return;
        }

        //only allow players to break paintings, not anything else (like water and explosions)
        if (!(event instanceof HangingBreakByEntityEvent))
        {
            event.setCancelled(true);
            return;
        }

        HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent) event;

        //who is removing it?
        Entity remover = entityEvent.getRemover();

        //again, making sure the breaker is a player
        if (remover.getType() != EntityType.PLAYER)
        {
            event.setCancelled(true);
            return;
        }

        //if the player doesn't have build permission, don't allow the breakage
        Player playerRemover = (Player) entityEvent.getRemover();
        String noBuildReason = GriefPrevention.instance.allowBuild(playerRemover, event.getEntity().getLocation(), Material.AIR);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(playerRemover, TextMode.Err, noBuildReason);
        }
    }

    //when a painting is placed...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPaintingPlace(HangingPlaceEvent event)
    {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        //FEATURE: similar to above, placing a painting requires build permission in the claim

        //if the player doesn't have permission, don't allow the placement
        String noBuildReason = GriefPrevention.instance.allowBuild(event.getPlayer(), event.getEntity().getLocation(), Material.PAINTING);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noBuildReason);
            return;
        }

        //otherwise, apply entity-count limitations for creative worlds
        else if (GriefPrevention.instance.creativeRulesApply(event.getEntity().getLocation()))
        {
            PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getUniqueId());
            Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, playerData.lastClaim);
            if (claim == null) return;

            String noEntitiesReason = claim.allowMoreEntities(false);
            if (noEntitiesReason != null)
            {
                GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noEntitiesReason);
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityPickUpItem(@NotNull EntityPickupItemEvent event)
    {
        // Hostiles are allowed to equip death drops to preserve the challenge of item retrieval.
        if (event.getEntity() instanceof Monster) return;

        Player player = null;
        if (event.getEntity() instanceof Player)
        {
            player = (Player) event.getEntity();
        }

        //FEATURE: Lock dropped items to player who dropped them.
        protectLockedDrops(event, player);

        // FEATURE: Protect freshly-spawned players from PVP.
        preventPvpSpawnCamp(event, player);
    }

    private void protectLockedDrops(@NotNull EntityPickupItemEvent event, @Nullable Player player)
    {
        Item item = event.getItem();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");

        // Ignore absent or invalid data.
        if (data.isEmpty() || !(data.get(0).value() instanceof UUID ownerID)) return;

        // Get owner from stored UUID.
        OfflinePlayer owner = instance.getServer().getOfflinePlayer(ownerID);

        // Owner must be online and can pick up their own drops.
        if (!owner.isOnline() || Objects.equals(player, owner)) return;

        PlayerData playerData = this.dataStore.getPlayerData(ownerID);

        // If drops are unlocked, allow pick up.
        if (playerData.dropsAreUnlocked) return;

        // Block pick up.
        event.setCancelled(true);

        // Non-players (dolphins, allays) do not need to generate prompts.
        if (player == null)
        {
            return;
        }

        // If the owner hasn't been instructed how to unlock, send explanatory messages.
        if (!playerData.receivedDropUnlockAdvertisement)
        {
            GriefPrevention.sendMessage(owner.getPlayer(), TextMode.Instr, Messages.DropUnlockAdvertisement);
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PickupBlockedExplanation, GriefPrevention.lookupPlayerName(ownerID));
            playerData.receivedDropUnlockAdvertisement = true;
        }
    }

    private void preventPvpSpawnCamp(@NotNull EntityPickupItemEvent event, @Nullable Player player)
    {
        // This is specific to players in pvp worlds.
        if (player == null || !instance.pvpRulesApply(player.getWorld())) return;

        //if we're preventing spawn camping and the player was previously empty handed...
        if (instance.config_pvp_protectFreshSpawns && (instance.getItemInHand(player, EquipmentSlot.HAND).getType() == Material.AIR))
        {
            //if that player is currently immune to pvp
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            if (playerData.pvpImmune)
            {
                //if it's been less than 10 seconds since the last time he spawned, don't pick up the item
                long now = Calendar.getInstance().getTimeInMillis();
                long elapsedSinceLastSpawn = now - playerData.lastSpawn;
                if (elapsedSinceLastSpawn < 10000)
                {
                    event.setCancelled(true);
                    return;
                }

                //otherwise take away his immunity. he may be armed now.  at least, he's worth killing for some loot
                playerData.pvpImmune = false;
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
        }
    }

}
