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

import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

//represents a player claim
//creating an instance doesn't make an effective claim
//only claims which have been added to the datastore have any effect
public class Claim
{
    //two locations, which together define the boundaries of the claim
    //note that the upper Y value is always ignored, because claims ALWAYS extend up to the sky
    Location lesserBoundaryCorner;
    Location greaterBoundaryCorner;

    //modification date.  this comes from the file timestamp during load, and is updated with runtime changes
    public Date modifiedDate;

    //id number.  unique to this claim, never changes.
    Long id = null;

    //ownerID.  for admin claims, this is NULL
    //use getOwnerName() to get a friendly name (will be "an administrator" for admin claims)
    public UUID ownerID;

    //list of players who (beyond the claim owner) have permission to grant permissions in this claim
    public ArrayList<String> managers = new ArrayList<>();

    //permissions for this claim, see ClaimPermission class
    private HashMap<String, ClaimPermission> playerIDToClaimPermissionMap = new HashMap<>();

    //whether or not this claim is in the data store
    //if a claim instance isn't in the data store, it isn't "active" - players can't interract with it
    //why keep this?  so that claims which have been removed from the data store can be correctly
    //ignored even though they may have references floating around
    public boolean inDataStore = false;

    public boolean areExplosivesAllowed = false;

    //parent claim
    //only used for claim subdivisions.  top level claims have null here
    public Claim parent = null;

    // intended for subclaims - they inherit no permissions
    private boolean inheritNothing = false;

    //children (subdivisions)
    //note subdivisions themselves never have children
    public ArrayList<Claim> children = new ArrayList<>();

    //following a siege, buttons/levers are unlocked temporarily.  this represents that state
    public boolean doorsOpen = false;

    //whether or not this is an administrative claim
    //administrative claims are created and maintained by players with the griefprevention.adminclaims permission.
    public boolean isAdminClaim()
    {
        return this.getOwnerID() == null;
    }

    //accessor for ID
    public Long getID()
    {
        return this.id;
    }

    //basic constructor, just notes the creation time
    //see above declarations for other defaults
    Claim()
    {
        this.modifiedDate = Calendar.getInstance().getTime();
    }

    //main constructor.  note that only creating a claim instance does nothing - a claim must be added to the data store to be effective
    Claim(Location lesserBoundaryCorner, Location greaterBoundaryCorner, UUID ownerID, List<String> builderIDs, List<String> containerIDs, List<String> accessorIDs, List<String> managerIDs, boolean inheritNothing, Long id)
    {
        //modification date
        this.modifiedDate = Calendar.getInstance().getTime();

        //id
        this.id = id;

        //store corners
        this.lesserBoundaryCorner = lesserBoundaryCorner.clone();
        this.greaterBoundaryCorner = greaterBoundaryCorner.clone();

        // Sanitize corners
        int x1 = this.lesserBoundaryCorner.getBlockX();
        int x2 = this.greaterBoundaryCorner.getBlockX();
        if (x1 > x2)
        {
            this.greaterBoundaryCorner.setX(x1);
            this.lesserBoundaryCorner.setX(x2);
        }
        int z1 = this.lesserBoundaryCorner.getBlockZ();
        int z2 = this.greaterBoundaryCorner.getBlockZ();
        if (z1 > z2)
        {
            this.greaterBoundaryCorner.setZ(z1);
            this.lesserBoundaryCorner.setZ(z2);
        }
        this.lesserBoundaryCorner.setY(Math.min(this.lesserBoundaryCorner.getBlockY(), this.greaterBoundaryCorner.getBlockY()));

        //owner
        this.ownerID = ownerID;

        //other permissions
        for (String builderID : builderIDs)
        {
            this.setPermission(builderID, ClaimPermission.Build);
        }

        for (String containerID : containerIDs)
        {
            this.setPermission(containerID, ClaimPermission.Container);
        }

        for (String accessorID : accessorIDs)
        {
            this.setPermission(accessorID, ClaimPermission.Access);
        }

        for (String managerID : managerIDs)
        {
            if (managerID != null && !managerID.isEmpty())
            {
                this.managers.add(managerID);
            }
        }

        this.inheritNothing = inheritNothing;
    }

    Claim(Location lesserBoundaryCorner, Location greaterBoundaryCorner, UUID ownerID, List<String> builderIDs, List<String> containerIDs, List<String> accessorIDs, List<String> managerIDs, Long id)
    {
        this(lesserBoundaryCorner, greaterBoundaryCorner, ownerID, builderIDs, containerIDs, accessorIDs, managerIDs, false, id);
    }

    //produces a copy of a claim.
    public Claim(Claim claim) {
        this.modifiedDate = claim.modifiedDate;
        this.lesserBoundaryCorner = claim.greaterBoundaryCorner.clone();
        this.greaterBoundaryCorner = claim.greaterBoundaryCorner.clone();
        this.id = claim.id;
        this.ownerID = claim.ownerID;
        this.managers = new ArrayList<>(claim.managers);
        this.playerIDToClaimPermissionMap = new HashMap<>(claim.playerIDToClaimPermissionMap);
        this.inDataStore = false; //since it's a copy of a claim, not in datastore!
        this.areExplosivesAllowed = claim.areExplosivesAllowed;
        this.parent = claim.parent;
        this.inheritNothing = claim.inheritNothing;
        this.children = new ArrayList<>(claim.children);
        this.doorsOpen = claim.doorsOpen;
    }

    //measurements.  all measurements are in blocks
    public int getArea()
    {
        try
        {
            int dX = Math.addExact(Math.subtractExact(greaterBoundaryCorner.getBlockX(), lesserBoundaryCorner.getBlockX()), 1);
            int dZ = Math.addExact(Math.subtractExact(greaterBoundaryCorner.getBlockZ(), lesserBoundaryCorner.getBlockZ()), 1);
            return Math.multiplyExact(dX, dZ);
        }
        catch (ArithmeticException e)
        {
            // If a claim's area exceeds the max value an int can hold, return max value.
            return Integer.MAX_VALUE;
        }
    }

    public int getWidth()
    {
        return this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
    }

    public int getHeight()
    {
        return this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;
    }

    public boolean getSubclaimRestrictions()
    {
        return inheritNothing;
    }

    public void setSubclaimRestrictions(boolean inheritNothing)
    {
        this.inheritNothing = inheritNothing;
    }

    //distance check for claims, distance in this case is a band around the outside of the claim rather then euclidean distance
    public boolean isNear(Location location, int howNear)
    {
        Claim claim = new Claim
                (new Location(this.lesserBoundaryCorner.getWorld(), this.lesserBoundaryCorner.getBlockX() - howNear, this.lesserBoundaryCorner.getBlockY(), this.lesserBoundaryCorner.getBlockZ() - howNear),
                        new Location(this.greaterBoundaryCorner.getWorld(), this.greaterBoundaryCorner.getBlockX() + howNear, this.greaterBoundaryCorner.getBlockY(), this.greaterBoundaryCorner.getBlockZ() + howNear),
                        null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);

        return claim.contains(location, false, true);
    }

    /**
     * @deprecated Check {@link ClaimPermission#Edit} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    public @Nullable String allowEdit(@NotNull Player player)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Edit, null);
        return supplier != null ? supplier.get() : null;
    }

    private static final Set<Material> PLACEABLE_FARMING_BLOCKS = Set.of(
            Material.PUMPKIN_STEM,
            Material.WHEAT,
            Material.MELON_STEM,
            Material.CARROTS,
            Material.POTATOES,
            Material.NETHER_WART,
            Material.BEETROOTS,
            Material.COCOA,
            Material.GLOW_BERRIES,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT);

    private static boolean placeableForFarming(Material material)
    {
        return PLACEABLE_FARMING_BLOCKS.contains(material);
    }

    /**
     * @deprecated Check {@link ClaimPermission#Build} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    //build permission check
    public @Nullable String allowBuild(@NotNull Player player, @NotNull Material material)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Build, new CompatBuildBreakEvent(material, false));
        return supplier != null ? supplier.get() : null;
    }

    public static class CompatBuildBreakEvent extends Event
    {
        private final Material material;
        private final boolean isBreak;

        private CompatBuildBreakEvent(Material material, boolean isBreak)
        {
            this.material = material;
            this.isBreak = isBreak;
        }

        public Material getMaterial()
        {
            return material;
        }

        public boolean isBreak()
        {
            return isBreak;
        }

        @Override
        public @NotNull HandlerList getHandlers()
        {
            return new HandlerList();
        }

    }

    public boolean hasExplicitPermission(@NotNull UUID uuid, @NotNull ClaimPermission level)
    {
        if (uuid.equals(this.getOwnerID())) return true;

        if (level == ClaimPermission.Manage) return this.managers.contains(uuid.toString());

        return level.isGrantedBy(this.playerIDToClaimPermissionMap.get(uuid.toString()));
    }

    public boolean hasExplicitPermission(@NotNull Player player, @NotNull ClaimPermission level)
    {
        // Check explicit ClaimPermission for UUID
        if (this.hasExplicitPermission(player.getUniqueId(), level)) return true;

        // Special case managers - a separate list is used.
        if (level == ClaimPermission.Manage)
        {
            for (String node : this.managers)
            {
                // Ensure valid permission format for permissions - [permission.node]
                if (node.length() < 3 || node.charAt(0) != '[' || node.charAt(node.length() - 1) != ']') continue;
                // Check if player has node
                if (player.hasPermission(node.substring(1, node.length() - 1))) return true;
            }
            return false;
        }

        // Check permission-based ClaimPermission
        for (Map.Entry<String, ClaimPermission> stringToPermission : this.playerIDToClaimPermissionMap.entrySet())
        {
            String node = stringToPermission.getKey();
            // Ensure valid permission format for permissions - [permission.node]
            if (node.length() < 3 || node.charAt(0) != '[' || node.charAt(node.length() - 1) != ']') continue;

            // Check if level is high enough and player has node
            if (level.isGrantedBy(stringToPermission.getValue())
                    && player.hasPermission(node.substring(1, node.length() - 1)))
                return true;
        }

        return false;
    }

    /**
     * Check whether a Player has a certain level of trust.
     *
     * @param player the Player being checked for permissions
     * @param permission the ClaimPermission level required
     * @param event the Event triggering the permission check
     * @return the denial message or null if permission is granted
     */
    public @Nullable Supplier<String> checkPermission(
            @NotNull Player player,
            @NotNull ClaimPermission permission,
            @Nullable Event event)
    {
        return checkPermission(player, permission, event, null);
    }

    /**
     * Check whether a Player has a certain level of trust. For internal use; allows changing default message.
     *
     * @param player the Player being checked for permissions
     * @param permission the ClaimPermission level required
     * @param event the Event triggering the permission check
     * @param denialOverride a message overriding the default denial for clarity
     * @return the denial message or null if permission is granted
     */
    @Nullable Supplier<String> checkPermission(
            @NotNull Player player,
            @NotNull ClaimPermission permission,
            @Nullable Event event,
            @Nullable Supplier<String> denialOverride)
    {
        return callPermissionCheck(new ClaimPermissionCheckEvent(player, this, permission, event), denialOverride);
    }

    /**
     * Check whether a UUID has a certain level of trust.
     *
     * @param uuid the UUID being checked for permissions
     * @param permission the ClaimPermission level required
     * @param event the Event triggering the permission check
     * @return the denial reason or null if permission is granted
     */
    public @Nullable Supplier<String> checkPermission(
            @NotNull UUID uuid,
            @NotNull ClaimPermission permission,
            @Nullable Event event)
    {
        return callPermissionCheck(new ClaimPermissionCheckEvent(uuid, this, permission, event), null);
    }

    /**
     * Helper method for calling a ClaimPermissionCheckEvent.
     *
     * @param event the ClaimPermissionCheckEvent to call
     * @param denialOverride a message overriding the default denial for clarity
     * @return the denial reason or null if permission is granted
     */
    private @Nullable Supplier<String> callPermissionCheck(
            @NotNull ClaimPermissionCheckEvent event,
            @Nullable Supplier<String> denialOverride)
    {
        // Set denial message (if any) using default behavior.
        Supplier<String> defaultDenial = getDefaultDenial(event.getCheckedPlayer(), event.getCheckedUUID(),
                event.getRequiredPermission(), event.getTriggeringEvent());
        // If permission is denied and a clarifying override is provided, use override.
        if (defaultDenial != null && denialOverride != null) {
            defaultDenial = denialOverride;
        }

        event.setDenialReason(defaultDenial);

        Bukkit.getPluginManager().callEvent(event);

        return event.getDenialReason();
    }

    /**
     * Get the default reason for denial of a ClaimPermission.
     *
     * @param player the Player being checked for permissions
     * @param uuid the UUID being checked for permissions
     * @param permission the ClaimPermission required
     * @param event the Event triggering the permission check
     * @return the denial reason or null if permission is granted
     */
    private @Nullable Supplier<String> getDefaultDenial(
            @Nullable Player player,
            @NotNull UUID uuid,
            @NotNull ClaimPermission permission,
            @Nullable Event event)
    {
        if (player != null)
        {
            // Admin claims need adminclaims permission only.
            if (this.isAdminClaim())
            {
                if (player.hasPermission("griefprevention.adminclaims")) return null;
            }

            // Anyone with deleteclaims permission can edit non-admin claims at any time.
            else if (permission == ClaimPermission.Edit && player.hasPermission("griefprevention.deleteclaims"))
                return null;
        }

        // Claim owner and admins in ignoreclaims mode have access.
        if (uuid.equals(this.getOwnerID())
                || GriefPrevention.instance.dataStore.getPlayerData(uuid).ignoreClaims
                && hasBypassPermission(player, permission))
            return null;

        // Look for explicit individual permission.
        if (player != null)
        {
            if (this.hasExplicitPermission(player, permission)) return null;
        }
        else
        {
            if (this.hasExplicitPermission(uuid, permission)) return null;
        }

        // Check for public permission.
        if (permission.isGrantedBy(this.playerIDToClaimPermissionMap.get("public"))) return null;

        // Special building-only rules.
        if (permission == ClaimPermission.Build)
        {
            // No building while in PVP.
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(uuid);
            if (playerData.inPvpCombat())
            {
                return () -> GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildPvP);
            }

            // Allow farming crops with container trust.
            Material material = null;
            if (event instanceof BlockBreakEvent || event instanceof BlockPlaceEvent)
                material = ((BlockEvent) event).getBlock().getType();

            if (material != null && placeableForFarming(material)
                    && this.getDefaultDenial(player, uuid, ClaimPermission.Container, event) == null)
                return null;
        }

        // Permission inheritance for subdivisions.
        if (this.parent != null)
        {
            if (!inheritNothing)
                return this.parent.getDefaultDenial(player, uuid, permission, event);
        }

        // Catch-all error message for all other cases.
        return () ->
        {
            String reason = GriefPrevention.instance.dataStore.getMessage(permission.getDenialMessage(), this.getOwnerName());
            if (hasBypassPermission(player, permission))
                reason += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            return reason;
        };
    }

    /**
     * Check if the {@link Player} has bypass permissions for a {@link ClaimPermission}. Owner-exclusive edit actions
     * require {@code griefprevention.deleteclaims}. All other actions require {@code griefprevention.ignoreclaims}.
     *
     * @param player the {@code Player}
     * @param permission the {@code ClaimPermission} whose bypass permission is being checked
     * @return whether the player has the bypass node
     */
    @Contract("null, _ -> false")
    private boolean hasBypassPermission(@Nullable Player player, @NotNull ClaimPermission permission)
    {
        if (player == null) return false;

        if (permission == ClaimPermission.Edit) return player.hasPermission("griefprevention.deleteclaims");

        return player.hasPermission("griefprevention.ignoreclaims");
    }

    /**
     * @deprecated Check {@link ClaimPermission#Build} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    public @Nullable String allowBreak(@NotNull Player player, @NotNull Material material)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Build, new CompatBuildBreakEvent(material, true));
        return supplier != null ? supplier.get() : null;
    }

    /**
     * @deprecated Check {@link ClaimPermission#Access} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    public @Nullable String allowAccess(@NotNull Player player)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Access, null);
        return supplier != null ? supplier.get() : null;
    }

    /**
     * @deprecated Check {@link ClaimPermission#Container} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    public @Nullable String allowContainers(@NotNull Player player)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Container, null);
        return supplier != null ? supplier.get() : null;
    }

    /**
     * @deprecated Check {@link ClaimPermission#Manage} with {@link #checkPermission(Player, ClaimPermission, Event)}.
     * @param player the Player
     * @return the denial message, or null if the action is allowed
     */
    @Deprecated
    public @Nullable String allowGrantPermission(@NotNull Player player)
    {
        Supplier<String> supplier = checkPermission(player, ClaimPermission.Manage, null);
        return supplier != null ? supplier.get() : null;
    }

    @Contract("null -> null")
    public @Nullable ClaimPermission getPermission(@Nullable String playerID)
    {
        if (playerID == null || playerID.isEmpty()) return null;

        return this.playerIDToClaimPermissionMap.get(playerID.toLowerCase());
    }

    //grants a permission for a player or the public
    public void setPermission(@Nullable String playerID, @Nullable ClaimPermission permissionLevel)
    {
        if (permissionLevel == ClaimPermission.Edit) throw new IllegalArgumentException("Cannot add editors!");

        if (playerID == null || playerID.isEmpty()) return;

        if (permissionLevel == null)
            dropPermission(playerID);
        else if (permissionLevel == ClaimPermission.Manage)
            this.managers.add(playerID.toLowerCase());
        else
            this.playerIDToClaimPermissionMap.put(playerID.toLowerCase(), permissionLevel);
    }

    //revokes a permission for a player or the public
    public void dropPermission(@NotNull String playerID)
    {
        playerID = playerID.toLowerCase();
        this.playerIDToClaimPermissionMap.remove(playerID);
        this.managers.remove(playerID);

        for (Claim child : this.children)
        {
            child.dropPermission(playerID);
        }
    }

    //clears all permissions (except owner of course)
    public void clearPermissions()
    {
        this.playerIDToClaimPermissionMap.clear();
        this.managers.clear();

        for (Claim child : this.children)
        {
            child.clearPermissions();
        }
    }

    //gets ALL permissions
    //useful for  making copies of permissions during a claim resize and listing all permissions in a claim
    public void getPermissions(ArrayList<String> builders, ArrayList<String> containers, ArrayList<String> accessors, ArrayList<String> managers)
    {
        //loop through all the entries in the hash map
        for (Map.Entry<String, ClaimPermission> entry : this.playerIDToClaimPermissionMap.entrySet())
        {
            //build up a list for each permission level
            if (entry.getValue() == ClaimPermission.Build)
            {
                builders.add(entry.getKey());
            }
            else if (entry.getValue() == ClaimPermission.Container || entry.getValue() == ClaimPermission.Inventory)
            {
                containers.add(entry.getKey());
            }
            else
            {
                accessors.add(entry.getKey());
            }
        }

        //managers are handled a little differently
        managers.addAll(this.managers);
    }

    //returns a copy of the location representing lower x, y, z limits
    public Location getLesserBoundaryCorner()
    {
        return this.lesserBoundaryCorner.clone();
    }

    //returns a copy of the location representing upper x, y, z limits
    //NOTE: remember upper Y will always be ignored, all claims always extend to the sky
    public Location getGreaterBoundaryCorner()
    {
        return this.greaterBoundaryCorner.clone();
    }

    //returns a friendly owner name (for admin claims, returns "an administrator" as the owner)
    public String getOwnerName()
    {
        if (this.parent != null)
            return this.parent.getOwnerName();

        if (this.ownerID == null)
            return GriefPrevention.instance.dataStore.getMessage(Messages.OwnerNameForAdminClaims);

        return GriefPrevention.lookupPlayerName(this.ownerID);
    }

    public UUID getOwnerID()
    {
        if (this.parent != null)
        {
            return this.parent.ownerID;
        }
        return this.ownerID;
    }

    //whether or not a location is in a claim
    //ignoreHeight = true means location UNDER the claim will return TRUE
    //excludeSubdivisions = true means that locations inside subdivisions of the claim will return FALSE
    public boolean contains(Location location, boolean ignoreHeight, boolean excludeSubdivisions)
    {
        //not in the same world implies false
        if (!Objects.equals(location.getWorld(), this.lesserBoundaryCorner.getWorld())) return false;

        BoundingBox boundingBox = new BoundingBox(this);
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // If we're ignoring height, use 2D containment check.
        if (ignoreHeight && !boundingBox.contains2d(x, z))
        {
            return false;
        }
        // Otherwise use full containment check.
        else if (!ignoreHeight && !boundingBox.contains(x, location.getBlockY(), z))
        {
            return false;
        }

        //additional check for subdivisions
        //you're only in a subdivision when you're also in its parent claim
        //NOTE: if a player creates subdivions then resizes the parent claim, it's possible that
        //a subdivision can reach outside of its parent's boundaries.  so this check is important!
        if (this.parent != null)
        {
            return this.parent.contains(location, ignoreHeight, false);
        }

        //code to exclude subdivisions in this check
        else if (excludeSubdivisions)
        {
            //search all subdivisions to see if the location is in any of them
            for (Claim child : this.children)
            {
                //if we find such a subdivision, return false
                if (child.contains(location, ignoreHeight, true))
                {
                    return false;
                }
            }
        }

        //otherwise yes
        return true;
    }

    //whether or not two claims overlap
    //used internally to prevent overlaps when creating claims
    boolean overlaps(Claim otherClaim)
    {
        if (!Objects.equals(this.lesserBoundaryCorner.getWorld(), otherClaim.getLesserBoundaryCorner().getWorld())) return false;

        return new BoundingBox(this).intersects(new BoundingBox(otherClaim));
    }

    @Deprecated(since = "17.0.0", forRemoval = true)
    @Contract("_ -> null")
    public @Nullable String allowMoreEntities(boolean remove)
    {
        return null;
    }

    @Deprecated(since = "17.0.0", forRemoval = true)
    @Contract("-> null")
    public @Nullable String allowMoreActiveBlocks()
    {
        return null;
    }

    //implements a strict ordering of claims, used to keep the claims collection sorted for faster searching
    boolean greaterThan(Claim otherClaim)
    {
        Location thisCorner = this.getLesserBoundaryCorner();
        Location otherCorner = otherClaim.getLesserBoundaryCorner();

        if (thisCorner.getBlockX() > otherCorner.getBlockX()) return true;

        if (thisCorner.getBlockX() < otherCorner.getBlockX()) return false;

        if (thisCorner.getBlockZ() > otherCorner.getBlockZ()) return true;

        if (thisCorner.getBlockZ() < otherCorner.getBlockZ()) return false;

        return thisCorner.getWorld().getName().compareTo(otherCorner.getWorld().getName()) < 0;
    }


    public ArrayList<Chunk> getChunks()
    {
        ArrayList<Chunk> chunks = new ArrayList<>();

        World world = this.getLesserBoundaryCorner().getWorld();
        Chunk lesserChunk = this.getLesserBoundaryCorner().getChunk();
        Chunk greaterChunk = this.getGreaterBoundaryCorner().getChunk();

        for (int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++)
        {
            for (int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++)
            {
                chunks.add(world.getChunkAt(x, z));
            }
        }

        return chunks;
    }

    ArrayList<Long> getChunkHashes()
    {
        return DataStore.getChunkHashes(this);
    }
}
