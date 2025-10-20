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

import com.google.common.io.Files;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.events.ClaimModifiedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimExtendEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

//singleton class which manages all GriefPrevention data (except for config options)
public abstract class DataStore
{

    //in-memory cache for player data
    protected ConcurrentHashMap<UUID, PlayerData> playerNameToPlayerDataMap = new ConcurrentHashMap<>();

    //in-memory cache for group (permission-based) data
    protected ConcurrentHashMap<String, Integer> permissionToBonusBlocksMap = new ConcurrentHashMap<>();

    //in-memory cache for claim data
    ArrayList<Claim> claims = new ArrayList<>();
    ConcurrentHashMap<Long, ArrayList<Claim>> chunksToClaimsMap = new ConcurrentHashMap<>();

    //in-memory cache for messages
    private String[] messages;

    //pattern for unique user identifiers (UUIDs)
    protected final static Pattern uuidpattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    //next claim ID
    Long nextClaimID = (long) 0;

    //path information, for where stuff stored on disk is well...  stored
    protected final static String dataLayerFolderPath = "plugins" + File.separator + "GriefPreventionData";
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
    final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
    final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
    final static String softMuteFilePath = dataLayerFolderPath + File.separator + "softMute.txt";
    final static String bannedWordsFilePath = dataLayerFolderPath + File.separator + "bannedWords.txt";

    //the latest version of the data schema implemented here
    protected static final int latestSchemaVersion = 3;

    //reading and writing the schema version to the data store
    abstract int getSchemaVersionFromStorage();

    abstract void updateSchemaVersionInStorage(int versionToSet);

    //current version of the schema of data in secondary storage
    private int currentSchemaVersion = -1;  //-1 means not determined yet

    //video links
    static final String SURVIVAL_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpuser" + ChatColor.RESET;
    static final String CREATIVE_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpcrea" + ChatColor.RESET;
    static final String SUBDIVISION_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpsub" + ChatColor.RESET;

    //list of UUIDs which are soft-muted
    ConcurrentHashMap<UUID, Boolean> softMuteMap = new ConcurrentHashMap<>();

    //world guard reference, if available
    private WorldGuardWrapper worldGuard = null;

    protected int getSchemaVersion()
    {
        if (this.currentSchemaVersion >= 0)
        {
            return this.currentSchemaVersion;
        }
        else
        {
            this.currentSchemaVersion = this.getSchemaVersionFromStorage();
            return this.currentSchemaVersion;
        }
    }

    protected void setSchemaVersion(int versionToSet)
    {
        this.currentSchemaVersion = versionToSet;
        this.updateSchemaVersionInStorage(versionToSet);
    }

    //initialization!
    void initialize() throws Exception
    {
        GriefPrevention.AddLogEntry(this.claims.size() + " total claims loaded.");

        //RoboMWM: ensure the nextClaimID is greater than any other claim ID. If not, data corruption occurred (out of storage space, usually).
        for (Claim claim : this.claims)
        {
            if (claim.id >= nextClaimID)
            {
                GriefPrevention.instance.getLogger().severe("nextClaimID was lesser or equal to an already-existing claim ID!\n" +
                        "This usually happens if you ran out of storage space.");
                GriefPrevention.AddLogEntry("Changing nextClaimID from " + nextClaimID + " to " + claim.id, CustomLogEntryTypes.Debug, false);
                nextClaimID = claim.id + 1;
            }
        }

        //ensure data folders exist
        File playerDataFolder = new File(playerDataFolderPath);
        if (!playerDataFolder.exists())
        {
            playerDataFolder.mkdirs();
        }

        //load up all the messages from messages.yml
        this.loadMessages();
        GriefPrevention.AddLogEntry("Customizable messages loaded.");

        //if converting up from an earlier schema version, write all claims back to storage using the latest format
        if (this.getSchemaVersion() < latestSchemaVersion)
        {
            GriefPrevention.AddLogEntry("Please wait.  Updating data format.");

            for (Claim claim : this.claims)
            {
                this.saveClaim(claim);

                for (Claim subClaim : claim.children)
                {
                    this.saveClaim(subClaim);
                }
            }

            //clean up any UUID conversion work
            if (UUIDFetcher.lookupCache != null)
            {
                UUIDFetcher.lookupCache.clear();
                UUIDFetcher.correctedNames.clear();
            }

            GriefPrevention.AddLogEntry("Update finished.");
        }

        //load list of soft mutes
        this.loadSoftMutes();

        //make a note of the data store schema version
        this.setSchemaVersion(latestSchemaVersion);

        //try to hook into world guard
        try
        {
            this.worldGuard = new WorldGuardWrapper();
            GriefPrevention.AddLogEntry("Successfully hooked into WorldGuard.");
        }
        //if failed, world guard compat features will just be disabled.
        catch (IllegalStateException | IllegalArgumentException | ClassCastException | NoClassDefFoundError ignored) { }
    }

    private void loadSoftMutes()
    {
        File softMuteFile = new File(softMuteFilePath);
        if (softMuteFile.exists())
        {
            BufferedReader inStream = null;
            try
            {
                //open the file
                inStream = new BufferedReader(new FileReader(softMuteFile.getAbsolutePath()));

                //while there are lines left
                String nextID = inStream.readLine();
                while (nextID != null)
                {
                    //parse line into a UUID
                    UUID playerID;
                    try
                    {
                        playerID = UUID.fromString(nextID);
                    }
                    catch (Exception e)
                    {
                        playerID = null;
                        GriefPrevention.AddLogEntry("Failed to parse soft mute entry as a UUID: " + nextID);
                    }

                    //push it into the map
                    if (playerID != null)
                    {
                        this.softMuteMap.put(playerID, true);
                    }

                    //move to the next
                    nextID = inStream.readLine();
                }
            }
            catch (Exception e)
            {
                GriefPrevention.AddLogEntry("Failed to read from the soft mute data file: " + e.toString());
                e.printStackTrace();
            }

            try
            {
                if (inStream != null) inStream.close();
            }
            catch (IOException exception) {}
        }
    }

    public List<String> loadBannedWords()
    {
        try
        {
            File bannedWordsFile = new File(bannedWordsFilePath);
            if (!bannedWordsFile.exists())
            {
                Files.touch(bannedWordsFile);
                String defaultWords =
                        "nigger\nniggers\nniger\nnigga\nnigers\nniggas\n" +
                                "fag\nfags\nfaggot\nfaggots\nfeggit\nfeggits\nfaggit\nfaggits\n" +
                                "cunt\ncunts\nwhore\nwhores\nslut\nsluts\n";
                Files.append(defaultWords, bannedWordsFile, Charset.forName("UTF-8"));
            }

            return Files.readLines(bannedWordsFile, Charset.forName("UTF-8"));
        }
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Failed to read from the banned words data file: " + e.toString());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    //updates soft mute map and data file
    boolean toggleSoftMute(UUID playerID)
    {
        boolean newValue = !this.isSoftMuted(playerID);

        this.softMuteMap.put(playerID, newValue);
        this.saveSoftMutes();

        return newValue;
    }

    public boolean isSoftMuted(UUID playerID)
    {
        Boolean mapEntry = this.softMuteMap.get(playerID);
        if (mapEntry == null || mapEntry == Boolean.FALSE)
        {
            return false;
        }

        return true;
    }

    private void saveSoftMutes()
    {
        BufferedWriter outStream = null;

        try
        {
            //open the file and write the new value
            File softMuteFile = new File(softMuteFilePath);
            softMuteFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(softMuteFile));

            for (Map.Entry<UUID, Boolean> entry : softMuteMap.entrySet())
            {
                if (entry.getValue().booleanValue())
                {
                    outStream.write(entry.getKey().toString());
                    outStream.newLine();
                }
            }

        }

        //if any problem, log it
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Unexpected exception saving soft mute data: " + e.getMessage());
            e.printStackTrace();
        }

        //close the file
        try
        {
            if (outStream != null) outStream.close();
        }
        catch (IOException exception) {}
    }

    //removes cached player data from memory
    synchronized void clearCachedPlayerData(UUID playerID)
    {
        this.playerNameToPlayerDataMap.remove(playerID);
    }

    //gets the number of bonus blocks a player has from his permissions
    //Bukkit doesn't allow for checking permissions of an offline player.
    //this will return 0 when he's offline, and the correct number when online.
    synchronized public int getGroupBonusBlocks(UUID playerID)
    {
        Player player = GriefPrevention.instance.getServer().getPlayer(playerID);

        if (player == null) return 0;

        int bonusBlocks = 0;

        for (Map.Entry<String, Integer> groupEntry : this.permissionToBonusBlocksMap.entrySet())
        {
            if (player.hasPermission(groupEntry.getKey()))
            {
                bonusBlocks += groupEntry.getValue();
            }
        }

        return bonusBlocks;
    }

    //grants a group (players with a specific permission) bonus claim blocks as long as they're still members of the group
    synchronized public int adjustGroupBonusBlocks(String groupName, int amount)
    {
        Integer currentValue = this.permissionToBonusBlocksMap.get(groupName);
        if (currentValue == null) currentValue = 0;

        currentValue += amount;
        this.permissionToBonusBlocksMap.put(groupName, currentValue);

        //write changes to storage to ensure they don't get lost
        this.saveGroupBonusBlocks(groupName, currentValue);

        return currentValue;
    }

    abstract void saveGroupBonusBlocks(String groupName, int amount);

    public class NoTransferException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        NoTransferException(String message)
        {
            super(message);
        }
    }

    synchronized public void changeClaimOwner(Claim claim, UUID newOwnerID)
    {
        //if it's a subdivision, throw an exception
        if (claim.parent != null)
        {
            throw new NoTransferException("Subdivisions can't be transferred.  Only top-level claims may change owners.");
        }

        //otherwise update information

        //determine current claim owner
        PlayerData ownerData = null;
        if (!claim.isAdminClaim())
        {
            ownerData = this.getPlayerData(claim.ownerID);
        }

        //call event
        ClaimTransferEvent event = new ClaimTransferEvent(claim, newOwnerID);
        Bukkit.getPluginManager().callEvent(event);

        //return if event is cancelled
        if (event.isCancelled()) return;

        //determine new owner
        PlayerData newOwnerData = null;

        if (event.getNewOwner() != null)
        {
            newOwnerData = this.getPlayerData(event.getNewOwner());
        }

        //transfer
        claim.ownerID = event.getNewOwner();
        this.saveClaim(claim);

        //adjust blocks and other records
        if (ownerData != null)
        {
            ownerData.getClaims().remove(claim);
        }

        if (newOwnerData != null)
        {
            newOwnerData.getClaims().add(claim);
        }
    }

    //adds a claim to the datastore, making it an effective claim
    synchronized void addClaim(Claim newClaim, boolean writeToStorage)
    {
        //subdivisions are added under their parent, not directly to the hash map for direct search
        if (newClaim.parent != null)
        {
            if (!newClaim.parent.children.contains(newClaim))
            {
                newClaim.parent.children.add(newClaim);
            }
            newClaim.inDataStore = true;
            if (writeToStorage)
            {
                this.saveClaim(newClaim);
            }
            return;
        }

        //add it and mark it as added
        this.claims.add(newClaim);
        addToChunkClaimMap(newClaim);

        newClaim.inDataStore = true;

        //except for administrative claims (which have no owner), update the owner's playerData with the new claim
        if (!newClaim.isAdminClaim() && writeToStorage)
        {
            PlayerData ownerData = this.getPlayerData(newClaim.ownerID);
            ownerData.getClaims().add(newClaim);
        }

        //make sure the claim is saved to disk
        if (writeToStorage)
        {
            this.saveClaim(newClaim);
        }
    }

    private void addToChunkClaimMap(Claim claim)
    {
        // Subclaims should not be added to chunk claim map.
        if (claim.parent != null) return;

        ArrayList<Long> chunkHashes = claim.getChunkHashes();
        for (Long chunkHash : chunkHashes)
        {
            ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkHash);
            if (claimsInChunk == null)
            {
                this.chunksToClaimsMap.put(chunkHash, claimsInChunk = new ArrayList<>());
            }

            claimsInChunk.add(claim);
        }
    }

    private void removeFromChunkClaimMap(Claim claim)
    {
        ArrayList<Long> chunkHashes = claim.getChunkHashes();
        for (Long chunkHash : chunkHashes)
        {
            ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkHash);
            if (claimsInChunk != null)
            {
                for (Iterator<Claim> it = claimsInChunk.iterator(); it.hasNext(); )
                {
                    Claim c = it.next();
                    if (c.id.equals(claim.id))
                    {
                        it.remove();
                        break;
                    }
                }
                if (claimsInChunk.isEmpty())
                { // if nothing's left, remove this chunk's cache
                    this.chunksToClaimsMap.remove(chunkHash);
                }
            }
        }
    }

    //turns a location into a string, useful in data storage
    private final String locationStringDelimiter = ";";

    String locationToString(Location location)
    {
        StringBuilder stringBuilder = new StringBuilder(location.getWorld().getName());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockX());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockY());
        stringBuilder.append(locationStringDelimiter);
        stringBuilder.append(location.getBlockZ());

        return stringBuilder.toString();
    }

    //turns a location string back into a location
    Location locationFromString(String string, List<World> validWorlds) throws Exception
    {
        //split the input string on the space
        String[] elements = string.split(locationStringDelimiter);

        //expect four elements - world name, X, Y, and Z, respectively
        if (elements.length < 4)
        {
            throw new Exception("Expected four distinct parts to the location string: \"" + string + "\"");
        }

        String worldName = elements[0];
        String xString = elements[1];
        String yString = elements[2];
        String zString = elements[3];

        //identify world the claim is in
        World world = null;
        for (World w : validWorlds)
        {
            if (w.getName().equalsIgnoreCase(worldName))
            {
                world = w;
                break;
            }
        }

        if (world == null)
        {
            throw new Exception("World not found: \"" + worldName + "\"");
        }

        //convert those numerical strings to integer values
        int x = Integer.parseInt(xString);
        int y = Integer.parseInt(yString);
        int z = Integer.parseInt(zString);

        return new Location(world, x, y, z);
    }

    //saves any changes to a claim to secondary storage
    synchronized public void saveClaim(Claim claim)
    {
        assignClaimID(claim);

        this.writeClaimToStorage(claim);
    }

    private void assignClaimID(Claim claim)
    {
        //ensure a unique identifier for the claim which will be used to name the file on disk
        if (claim.id == null || claim.id == -1)
        {
            claim.id = this.nextClaimID;
            this.incrementNextClaimID();
        }
    }

    abstract void writeClaimToStorage(Claim claim);

    //increments the claim ID and updates secondary storage to be sure it's saved
    abstract void incrementNextClaimID();

    //retrieves player data from memory or secondary storage, as necessary
    //if the player has never been on the server before, this will return a fresh player data with default values
    synchronized public PlayerData getPlayerData(UUID playerID)
    {
        //first, look in memory
        PlayerData playerData = this.playerNameToPlayerDataMap.get(playerID);

        //if not there, build a fresh instance with some blanks for what may be in secondary storage
        if (playerData == null)
        {
            playerData = new PlayerData();
            playerData.playerID = playerID;

            //shove that new player data into the hash map cache
            this.playerNameToPlayerDataMap.put(playerID, playerData);
        }

        return playerData;
    }

    abstract PlayerData getPlayerDataFromStorage(UUID playerID);

    //deletes a claim or subdivision
    synchronized public void deleteClaim(Claim claim)
    {
        this.deleteClaim(claim, true, false);
    }

    //deletes a claim or subdivision
    synchronized public void deleteClaim(Claim claim, boolean releasePets)
    {
        this.deleteClaim(claim, true, releasePets);
    }

    synchronized void deleteClaim(Claim claim, boolean fireEvent, boolean releasePets)
    {
        //delete any children
        for (int j = 1; (j - 1) < claim.children.size(); j++)
        {
            this.deleteClaim(claim.children.get(j - 1), releasePets);
        }

        //subdivisions must also be removed from the parent claim child list
        if (claim.parent != null)
        {
            Claim parentClaim = claim.parent;
            parentClaim.children.remove(claim);
        }

        //mark as deleted so any references elsewhere can be ignored
        claim.inDataStore = false;

        //remove from memory
        for (int i = 0; i < this.claims.size(); i++)
        {
            if (claims.get(i).id.equals(claim.id))
            {
                this.claims.remove(i);
                break;
            }
        }

        removeFromChunkClaimMap(claim);

        //remove from secondary storage
        this.deleteClaimFromSecondaryStorage(claim);

        //update player data
        if (claim.ownerID != null)
        {
            PlayerData ownerData = this.getPlayerData(claim.ownerID);
            for (int i = 0; i < ownerData.getClaims().size(); i++)
            {
                if (ownerData.getClaims().get(i).id.equals(claim.id))
                {
                    ownerData.getClaims().remove(i);
                    break;
                }
            }
            this.savePlayerData(claim.ownerID, ownerData);
        }

        if (fireEvent)
        {
            ClaimDeletedEvent ev = new ClaimDeletedEvent(claim);
            Bukkit.getPluginManager().callEvent(ev);
        }

        //optionally set any pets free which belong to the claim owner
        if (releasePets && claim.ownerID != null && claim.parent == null)
        {
            for (Chunk chunk : claim.getChunks())
            {
                Entity[] entities = chunk.getEntities();
                for (Entity entity : entities)
                {
                    if (entity instanceof Tameable pet && pet.isTamed())
                    {
                        AnimalTamer owner = pet.getOwner();
                        if (owner != null && owner.getUniqueId().equals(claim.ownerID))
                        {
                            pet.setTamed(false);
                            pet.setOwner(null);
                            if (pet instanceof InventoryHolder holder) {
                                holder.getInventory().clear();
                            }
                            if (pet instanceof Sittable sittable) {
                                sittable.setSitting(false);
                            }
                        }
                    }
                }
            }
        }
    }

    abstract void deleteClaimFromSecondaryStorage(Claim claim);

    //gets the claim at a specific location
    //ignoreHeight = TRUE means that a location UNDER an existing claim will return the claim
    //cachedClaim can be NULL, but will help performance if you have a reasonable guess about which claim the location is in
    synchronized public Claim getClaimAt(Location location, boolean ignoreHeight, Claim cachedClaim)
    {
        return getClaimAt(location, ignoreHeight, false, cachedClaim);
    }

    /**
     * Get the claim at a specific location.
     *
     * <p>The cached claim may be null, but will increase performance if you have a reasonable idea
     * of which claim is correct.
     *
     * @param location the location
     * @param ignoreHeight whether or not to check containment vertically
     * @param ignoreSubclaims whether or not subclaims should be returned over claims
     * @param cachedClaim the cached claim, if any
     * @return the claim containing the location or null if no claim exists there
     */
    synchronized public Claim getClaimAt(Location location, boolean ignoreHeight, boolean ignoreSubclaims, Claim cachedClaim)
    {
        //check cachedClaim guess first.  if it's in the datastore and the location is inside it, we're done
        if (cachedClaim != null && cachedClaim.inDataStore && cachedClaim.contains(location, ignoreHeight, !ignoreSubclaims))
            return cachedClaim;

        //find a top level claim
        Long chunkID = getChunkHash(location);
        ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkID);
        if (claimsInChunk == null) return null;

        for (Claim claim : claimsInChunk)
        {
            if (claim.inDataStore && claim.contains(location, ignoreHeight, false))
            {
                // If ignoring subclaims, claim is a match.
                if (ignoreSubclaims) return claim;

                //when we find a top level claim, if the location is in one of its subdivisions,
                //return the SUBDIVISION, not the top level claim
                for (int j = 0; j < claim.children.size(); j++)
                {
                    Claim subdivision = claim.children.get(j);
                    if (subdivision.inDataStore && subdivision.contains(location, ignoreHeight, false))
                        return subdivision;
                }

                return claim;
            }
        }

        //if no claim found, return null
        return null;
    }

    //finds a claim by ID
    public synchronized Claim getClaim(long id)
    {
        for (Claim claim : this.claims)
        {
            if (claim.inDataStore)
            {
                if (claim.getID() == id)
                    return claim;
                for (Claim subClaim : claim.children)
                {
                    if (subClaim.getID() == id)
                    return subClaim;
                }
            }
        }

        return null;
    }

    //returns a read-only access point for the list of all land claims
    //if you need to make changes, use provided methods like .deleteClaim() and .createClaim().
    //this will ensure primary memory (RAM) and secondary memory (disk, database) stay in sync
    public Collection<Claim> getClaims()
    {
        return Collections.unmodifiableCollection(this.claims);
    }

    public Collection<Claim> getClaims(int chunkx, int chunkz)
    {
        ArrayList<Claim> chunkClaims = this.chunksToClaimsMap.get(getChunkHash(chunkx, chunkz));
        if (chunkClaims != null)
        {
            return Collections.unmodifiableCollection(chunkClaims);
        }
        else
        {
            return Collections.unmodifiableCollection(new ArrayList<>());
        }
    }

    //gets an almost-unique, persistent identifier for a chunk
    public static Long getChunkHash(long chunkx, long chunkz)
    {
        return (chunkz ^ (chunkx << 32));
    }

    //gets an almost-unique, persistent identifier for a chunk
    public static Long getChunkHash(Location location)
    {
        return getChunkHash(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static ArrayList<Long> getChunkHashes(Claim claim) {
        return getChunkHashes(claim.getLesserBoundaryCorner(), claim.getGreaterBoundaryCorner());
    }

    public static ArrayList<Long> getChunkHashes(Location min, Location max) {
        ArrayList<Long> hashes = new ArrayList<>();
        int smallX = min.getBlockX() >> 4;
        int smallZ = min.getBlockZ() >> 4;
        int largeX = max.getBlockX() >> 4;
        int largeZ = max.getBlockZ() >> 4;

        for (int x = smallX; x <= largeX; x++)
        {
            for (int z = smallZ; z <= largeZ; z++)
            {
                hashes.add(getChunkHash(x, z));
            }
        }

        return hashes;
    }

    /*
     * Creates a claim and flags it as being new....throwing a create claim event;
     */
    synchronized public CreateClaimResult createClaim(World world, int x1, int x2, int y1, int y2, int z1, int z2, UUID ownerID, Claim parent, Long id, Player creatingPlayer)
    {
        return createClaim(world, x1, x2, y1, y2, z1, z2, ownerID, parent, id, creatingPlayer, false);
    }

    //creates a claim.
    //if the new claim would overlap an existing claim, returns a failure along with a reference to the existing claim
    //if the new claim would overlap a WorldGuard region where the player doesn't have permission to build, returns a failure with NULL for claim
    //otherwise, returns a success along with a reference to the new claim
    //use ownerName == "" for administrative claims
    //for top level claims, pass parent == NULL
    //DOES adjust claim blocks available on success (players can go into negative quantity available)
    //DOES check for world guard regions where the player doesn't have permission
    //does NOT check a player has permission to create a claim, or enough claim blocks.
    //does NOT check minimum claim size constraints
    //does NOT visualize the new claim for any players
    synchronized public CreateClaimResult createClaim(World world, int x1, int x2, int y1, int y2, int z1, int z2, UUID ownerID, Claim parent, Long id, Player creatingPlayer, boolean dryRun)
    {
        CreateClaimResult result = new CreateClaimResult();

        int smallx, bigx, smally, bigy, smallz, bigz;

        int worldMinY = world.getMinHeight();
        y1 = Math.max(worldMinY, Math.max(GriefPrevention.instance.getMaxDepth(world), y1));
        y2 = Math.max(worldMinY, Math.max(GriefPrevention.instance.getMaxDepth(world), y2));

        //determine small versus big inputs
        if (x1 < x2)
        {
            smallx = x1;
            bigx = x2;
        }
        else
        {
            smallx = x2;
            bigx = x1;
        }

        if (y1 < y2)
        {
            smally = y1;
            bigy = y2;
        }
        else
        {
            smally = y2;
            bigy = y1;
        }

        if (z1 < z2)
        {
            smallz = z1;
            bigz = z2;
        }
        else
        {
            smallz = z2;
            bigz = z1;
        }

        if (parent != null)
        {
            Location lesser = parent.getLesserBoundaryCorner();
            Location greater = parent.getGreaterBoundaryCorner();
            if (smallx < lesser.getX() || smallz < lesser.getZ() || bigx > greater.getX() || bigz > greater.getZ())
            {
                result.succeeded = false;
                result.claim = parent;
                return result;
            }
            smally = sanitizeClaimDepth(parent, smally);
        }

        //claims can't be made outside the world border
        final Location smallerBoundaryCorner = new Location(world, smallx, smally, smallz);
        final Location greaterBoundaryCorner = new Location(world, bigx, bigy, bigz);
        if(!world.getWorldBorder().isInside(smallerBoundaryCorner) || !world.getWorldBorder().isInside(greaterBoundaryCorner)){
            result.succeeded = false;
            return result;
        }


        //creative mode claims always go to bedrock
        if (GriefPrevention.instance.config_claims_worldModes.get(world) == ClaimsMode.Creative)
        {
            smallerBoundaryCorner.setY(world.getMinHeight());
        }

        //create a new claim instance (but don't save it, yet)
        Claim newClaim = new Claim(
                smallerBoundaryCorner,
                greaterBoundaryCorner,
                ownerID,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                id);

        newClaim.parent = parent;

        //ensure this new claim won't overlap any existing claims
        ArrayList<Claim> claimsToCheck;
        if (newClaim.parent != null)
        {
            claimsToCheck = newClaim.parent.children;
        }
        else
        {
            claimsToCheck = this.claims;
        }

        for (Claim otherClaim : claimsToCheck)
        {
            //if we find an existing claim which will be overlapped
            if (otherClaim.id != newClaim.id && otherClaim.inDataStore && otherClaim.overlaps(newClaim))
            {
                //result = fail, return conflicting claim
                result.succeeded = false;
                result.claim = otherClaim;
                return result;
            }
        }

        //if worldguard is installed, also prevent claims from overlapping any worldguard regions
        if (GriefPrevention.instance.config_claims_respectWorldGuard && this.worldGuard != null && creatingPlayer != null)
        {
            if (!this.worldGuard.canBuild(newClaim.lesserBoundaryCorner, newClaim.greaterBoundaryCorner, creatingPlayer))
            {
                result.succeeded = false;
                result.claim = null;
                return result;
            }
        }
        if (dryRun)
        {
            // since this is a dry run, just return the unsaved claim as is.
            result.succeeded = true;
            result.claim = newClaim;
            return result;
        }
        assignClaimID(newClaim); // assign a claim ID before calling event, in case a plugin wants to know the ID.
        ClaimCreatedEvent event = new ClaimCreatedEvent(newClaim, creatingPlayer);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
        {
            result.succeeded = false;
            result.claim = null;
            return result;

        }
        //otherwise add this new claim to the data store to make it effective
        this.addClaim(newClaim, true);

        //then return success along with reference to new claim
        result.succeeded = true;
        result.claim = newClaim;
        return result;
    }

    //saves changes to player data to secondary storage.  MUST be called after you're done making changes, otherwise a reload will lose them
    public void savePlayerDataSync(UUID playerID, PlayerData playerData)
    {
        //ensure player data is already read from file before trying to save
        playerData.getAccruedClaimBlocks();
        playerData.getClaims();

        this.asyncSavePlayerData(playerID, playerData);
    }

    //saves changes to player data to secondary storage.  MUST be called after you're done making changes, otherwise a reload will lose them
    public void savePlayerData(UUID playerID, PlayerData playerData)
    {
        new SavePlayerDataThread(playerID, playerData).start();
    }

    public void asyncSavePlayerData(UUID playerID, PlayerData playerData)
    {
        //save everything except the ignore list
        this.overrideSavePlayerData(playerID, playerData);

        //save the ignore list
        if (playerData.ignoreListChanged)
        {
            StringBuilder fileContent = new StringBuilder();
            try
            {
                for (UUID uuidKey : playerData.ignoredPlayers.keySet())
                {
                    Boolean value = playerData.ignoredPlayers.get(uuidKey);
                    if (value == null) continue;

                    //admin-enforced ignores begin with an asterisk
                    if (value)
                    {
                        fileContent.append("*");
                    }

                    fileContent.append(uuidKey);
                    fileContent.append("\n");
                }

                //write data to file
                File playerDataFile = new File(playerDataFolderPath + File.separator + playerID + ".ignore");
                Files.write(fileContent.toString().trim().getBytes("UTF-8"), playerDataFile);
            }

            //if any problem, log it
            catch (Exception e)
            {
                GriefPrevention.AddLogEntry("GriefPrevention: Unexpected exception saving data for player \"" + playerID.toString() + "\": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    abstract void overrideSavePlayerData(UUID playerID, PlayerData playerData);

    //extends a claim to a new depth
    //respects the max depth config variable
    synchronized public void extendClaim(Claim claim, int newDepth)
    {
        if (claim.parent != null) claim = claim.parent;

        newDepth = sanitizeClaimDepth(claim, newDepth);

        //call event and return if event got cancelled
        ClaimExtendEvent event = new ClaimExtendEvent(claim, newDepth);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        //adjust to new depth
        setNewDepth(claim, event.getNewDepth());
    }

    /**
     * Helper method for sanitizing claim depth to find the minimum expected value.
     *
     * @param claim the claim
     * @param newDepth the new depth
     * @return the sanitized new depth
     */
    private int sanitizeClaimDepth(Claim claim, int newDepth) {
        if (claim.parent != null) claim = claim.parent;

        // Get the old depth including the depth of the lowest subdivision.
        int oldDepth = Math.min(
                claim.getLesserBoundaryCorner().getBlockY(),
                claim.children.stream().mapToInt(child -> child.getLesserBoundaryCorner().getBlockY())
                        .min().orElse(Integer.MAX_VALUE));

        // Use the lowest of the old and new depths.
        newDepth = Math.min(newDepth, oldDepth);
        // Cap the depth to the world's minimum height.
        World world = Objects.requireNonNull(claim.getLesserBoundaryCorner().getWorld());
        // Cap depth to maximum depth allowed by the configuration.
        newDepth = Math.max(newDepth, GriefPrevention.instance.getMaxDepth(world));
        newDepth = Math.max(newDepth, world.getMinHeight());

        return newDepth;
    }

    /**
     * Helper method for sanitizing and setting claim depth. Saves affected claims.
     *
     * @param claim the claim
     * @param newDepth the new depth
     */
    private void setNewDepth(Claim claim, int newDepth) {
        if (claim.parent != null) claim = claim.parent;

        final int depth = sanitizeClaimDepth(claim, newDepth);

        Stream.concat(Stream.of(claim), claim.children.stream()).forEach(localClaim -> {
            localClaim.lesserBoundaryCorner.setY(depth);
            localClaim.greaterBoundaryCorner.setY(Math.max(localClaim.greaterBoundaryCorner.getBlockY(), depth));
            this.saveClaim(localClaim);
        });
    }

    //starts a siege on a claim
    //does NOT check siege cooldowns, see onCooldown() below
    synchronized public void startSiege(Player attacker, Player defender, Claim defenderClaim)
    {
        //fill-in the necessary SiegeData instance
        SiegeData siegeData = new SiegeData(attacker, defender, defenderClaim);
        PlayerData attackerData = this.getPlayerData(attacker.getUniqueId());
        PlayerData defenderData = this.getPlayerData(defender.getUniqueId());
        attackerData.siegeData = siegeData;
        defenderData.siegeData = siegeData;
        defenderClaim.siegeData = siegeData;

        //start a task to monitor the siege
        //why isn't this a "repeating" task?
        //because depending on the status of the siege at the time the task runs, there may or may not be a reason to run the task again
        SiegeCheckupTask task = new SiegeCheckupTask(siegeData);
        siegeData.checkupTaskID = GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L * 30);
    }

    //ends a siege
    //either winnerName or loserName can be null, but not both
    synchronized public void endSiege(SiegeData siegeData, String winnerName, String loserName, List<ItemStack> drops)
    {
        boolean grantAccess = false;

        //determine winner and loser
        if (winnerName == null && loserName != null)
        {
            if (siegeData.attacker.getName().equals(loserName))
            {
                winnerName = siegeData.defender.getName();
            }
            else
            {
                winnerName = siegeData.attacker.getName();
            }
        }
        else if (winnerName != null && loserName == null)
        {
            if (siegeData.attacker.getName().equals(winnerName))
            {
                loserName = siegeData.defender.getName();
            }
            else
            {
                loserName = siegeData.attacker.getName();
            }
        }

        //if the attacker won, plan to open the doors for looting
        if (siegeData.attacker.getName().equals(winnerName))
        {
            grantAccess = true;
        }

        PlayerData attackerData = this.getPlayerData(siegeData.attacker.getUniqueId());
        attackerData.siegeData = null;

        PlayerData defenderData = this.getPlayerData(siegeData.defender.getUniqueId());
        defenderData.siegeData = null;
        defenderData.lastSiegeEndTimeStamp = System.currentTimeMillis();

        //start a cooldown for this attacker/defender pair
        Long now = Calendar.getInstance().getTimeInMillis();
        Long cooldownEnd = now + 1000 * 60 * GriefPrevention.instance.config_siege_cooldownEndInMinutes;  //one hour from now
        this.siegeCooldownRemaining.put(siegeData.attacker.getName() + "_" + siegeData.defender.getName(), cooldownEnd);

        //start cooldowns for every attacker/involved claim pair
        for (int i = 0; i < siegeData.claims.size(); i++)
        {
            Claim claim = siegeData.claims.get(i);
            claim.siegeData = null;
            this.siegeCooldownRemaining.put(siegeData.attacker.getName() + "_" + claim.getOwnerName(), cooldownEnd);

            //if doors should be opened for looting, do that now
            if (grantAccess)
            {
                claim.doorsOpen = true;
            }
        }

        //cancel the siege checkup task
        GriefPrevention.instance.getServer().getScheduler().cancelTask(siegeData.checkupTaskID);

        //notify everyone who won and lost
        if (winnerName != null && loserName != null)
        {
            GriefPrevention.instance.getServer().broadcastMessage(winnerName + " defeated " + loserName + " in siege warfare!");
        }

        //if the claim should be opened to looting
        if (grantAccess)
        {

            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);
            if (winner != null)
            {
                //notify the winner
                GriefPrevention.sendMessage(winner, TextMode.Success, Messages.SiegeWinDoorsOpen);

                //schedule a task to secure the claims in about 5 minutes
                SecureClaimTask task = new SecureClaimTask(siegeData);

                GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(
                        GriefPrevention.instance, task, 20L * GriefPrevention.instance.config_siege_doorsOpenSeconds
                );
            }
        }

        //if the siege ended due to death, transfer inventory to winner
        if (drops != null)
        {

            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);

            Player loser = GriefPrevention.instance.getServer().getPlayer(loserName);
            if (winner != null && loser != null)
            {
                //try to add any drops to the winner's inventory
                for (ItemStack stack : drops)
                {
                    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() == 0) continue;

                    HashMap<Integer, ItemStack> wontFitItems = winner.getInventory().addItem(stack);

                    //drop any remainder on the ground at his feet
                    Object[] keys = wontFitItems.keySet().toArray();
                    Location winnerLocation = winner.getLocation();
                    for (Map.Entry<Integer, ItemStack> wontFitItem : wontFitItems.entrySet())
                    {
                        winner.getWorld().dropItemNaturally(winnerLocation, wontFitItem.getValue());
                    }
                }

                drops.clear();
            }
        }
    }

    //timestamp for each siege cooldown to end
    private final HashMap<String, Long> siegeCooldownRemaining = new HashMap<>();

    //whether or not a sieger can siege a particular victim or claim, considering only cooldowns
    synchronized public boolean onCooldown(Player attacker, Player defender, Claim defenderClaim)
    {
        Long cooldownEnd = null;

        //look for an attacker/defender cooldown
        if (this.siegeCooldownRemaining.get(attacker.getName() + "_" + defender.getName()) != null)
        {
            cooldownEnd = this.siegeCooldownRemaining.get(attacker.getName() + "_" + defender.getName());

            if (Calendar.getInstance().getTimeInMillis() < cooldownEnd)
            {
                return true;
            }

            //if found but expired, remove it
            this.siegeCooldownRemaining.remove(attacker.getName() + "_" + defender.getName());
        }

        //look for genderal defender cooldown
        PlayerData defenderData = this.getPlayerData(defender.getUniqueId());
        if (defenderData.lastSiegeEndTimeStamp > 0)
        {
            long now = System.currentTimeMillis();
            if (now - defenderData.lastSiegeEndTimeStamp > 1000 * 60 * 15) //15 minutes in milliseconds
            {
                return true;
            }
        }

        //look for an attacker/claim cooldown
        if (cooldownEnd == null && this.siegeCooldownRemaining.get(attacker.getName() + "_" + defenderClaim.getOwnerName()) != null)
        {
            cooldownEnd = this.siegeCooldownRemaining.get(attacker.getName() + "_" + defenderClaim.getOwnerName());

            if (Calendar.getInstance().getTimeInMillis() < cooldownEnd)
            {
                return true;
            }

            //if found but expired, remove it
            this.siegeCooldownRemaining.remove(attacker.getName() + "_" + defenderClaim.getOwnerName());
        }

        return false;
    }

    //extend a siege, if it's possible to do so
    synchronized void tryExtendSiege(Player player, Claim claim)
    {
        PlayerData playerData = this.getPlayerData(player.getUniqueId());

        //player must be sieged
        if (playerData.siegeData == null) return;

        //claim isn't already under the same siege
        if (playerData.siegeData.claims.contains(claim)) return;

        //admin claims can't be sieged
        if (claim.isAdminClaim()) return;

        //player must have some level of permission to be sieged in a claim
        Claim currentClaim = claim;
        while (!currentClaim.hasExplicitPermission(player, ClaimPermission.Access))
        {
            if (currentClaim.parent == null) return;
            currentClaim = currentClaim.parent;
        }

        //otherwise extend the siege
        playerData.siegeData.claims.add(claim);
        claim.siegeData = playerData.siegeData;
    }

    //deletes all claims owned by a player
    synchronized public void deleteClaimsForPlayer(UUID playerID, boolean releasePets)
    {
        //make a list of the player's claims
        ArrayList<Claim> claimsToDelete = new ArrayList<>();
        for (Claim claim : this.claims)
        {
            if ((playerID == claim.ownerID || (playerID != null && playerID.equals(claim.ownerID))))
                claimsToDelete.add(claim);
        }

        //delete them one by one
        for (Claim claim : claimsToDelete)
        {
            claim.removeSurfaceFluids(null);

            this.deleteClaim(claim, releasePets);

            //if in a creative mode world, delete the claim
            if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner()))
            {
                GriefPrevention.instance.restoreClaim(claim, 0);
            }
        }
    }

    //tries to resize a claim
    //see CreateClaim() for details on return value
    synchronized public CreateClaimResult resizeClaim(Claim claim, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2, Player resizingPlayer)
    {
        //try to create this new claim, ignoring the original when checking for overlap
        CreateClaimResult result = this.createClaim(claim.getLesserBoundaryCorner().getWorld(), newx1, newx2, newy1, newy2, newz1, newz2, claim.ownerID, claim.parent, claim.id, resizingPlayer, true);

        //if succeeded
        if (result.succeeded)
        {
            removeFromChunkClaimMap(claim); // remove the old boundary from the chunk cache
            // copy the boundary from the claim created in the dry run of createClaim() to our existing claim
            claim.lesserBoundaryCorner = result.claim.lesserBoundaryCorner;
            claim.greaterBoundaryCorner = result.claim.greaterBoundaryCorner;
            // Sanitize claim depth, expanding parent down to the lowest subdivision and subdivisions down to parent.
            // Also saves affected claims.
            setNewDepth(claim, claim.getLesserBoundaryCorner().getBlockY());
            result.claim = claim;
            addToChunkClaimMap(claim); // add the new boundary to the chunk cache
        }

        return result;
    }

    void resizeClaimWithChecks(Player player, PlayerData playerData, int newx1, int newx2, int newy1, int newy2, int newz1, int newz2)
    {
        //for top level claims, apply size rules and claim blocks requirement
        if (playerData.claimResizing.parent == null)
        {
            //measure new claim, apply size rules
            int newWidth;
            int newHeight;
            try
            {
                newWidth = Math.abs(Math.subtractExact(newx1, newx2)) + 1;
                newHeight = Math.abs(Math.subtractExact(newz1, newz2)) + 1;
            }
            catch (ArithmeticException e)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeNeedMoreBlocks, String.valueOf(Integer.MAX_VALUE));
                return;
            }

            boolean smaller = newWidth < playerData.claimResizing.getWidth() || newHeight < playerData.claimResizing.getHeight();

            if (!player.hasPermission("griefprevention.adminclaims") && !playerData.claimResizing.isAdminClaim() && smaller)
            {
                if (newWidth < GriefPrevention.instance.config_claims_minWidth || newHeight < GriefPrevention.instance.config_claims_minWidth)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeClaimTooNarrow, String.valueOf(GriefPrevention.instance.config_claims_minWidth));
                    return;
                }

                int newArea;
                try
                {
                    newArea = Math.multiplyExact(newWidth, newHeight);
                }
                catch (ArithmeticException e)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeNeedMoreBlocks, String.valueOf(Integer.MAX_VALUE));
                    return;
                }
                if (newArea < GriefPrevention.instance.config_claims_minArea)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeClaimInsufficientArea, String.valueOf(GriefPrevention.instance.config_claims_minArea));
                    return;
                }
            }

            //make sure player has enough blocks to make up the difference
            if (!playerData.claimResizing.isAdminClaim() && player.getName().equals(playerData.claimResizing.getOwnerName()))
            {
                int newArea;
                int blocksRemainingAfter;
                try
                {
                    newArea = Math.multiplyExact(newWidth, newHeight);
                    blocksRemainingAfter = playerData.getRemainingClaimBlocks() + (playerData.claimResizing.getArea() - newArea);
                }
                catch (ArithmeticException e)
                {
                    blocksRemainingAfter = Integer.MIN_VALUE + 1;
                }

                if (blocksRemainingAfter < 0)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeNeedMoreBlocks, String.valueOf(Math.abs(blocksRemainingAfter)));
                    this.tryAdvertiseAdminAlternatives(player);
                    return;
                }
            }
        }

        Claim oldClaim = playerData.claimResizing;
        Claim newClaim = new Claim(oldClaim);
        World world = newClaim.getLesserBoundaryCorner().getWorld();
        newClaim.lesserBoundaryCorner = new Location(world, newx1, newy1, newz1);
        newClaim.greaterBoundaryCorner = new Location(world, newx2, newy2, newz2);

        //call event here to check if it has been cancelled
        ClaimResizeEvent event = new ClaimModifiedEvent(oldClaim, newClaim, player); // Swap to ClaimResizeEvent when ClaimModifiedEvent is removed
        Bukkit.getPluginManager().callEvent(event);

        //return here if event is cancelled
        if (event.isCancelled()) return;

        //special rule for making a top-level claim smaller.  to check this, verifying the old claim's corners are inside the new claim's boundaries.
        //rule: in any mode, shrinking a claim removes any surface fluids
        boolean smaller = false;
        if (oldClaim.parent == null)
        {
            //if the new claim is smaller
            if (!newClaim.contains(oldClaim.getLesserBoundaryCorner(), true, false) || !newClaim.contains(oldClaim.getGreaterBoundaryCorner(), true, false))
            {
                smaller = true;

                //remove surface fluids about to be unclaimed
                oldClaim.removeSurfaceFluids(newClaim);
            }
        }

        //ask the datastore to try and resize the claim, this checks for conflicts with other claims
        CreateClaimResult result = GriefPrevention.instance.dataStore.resizeClaim(
                playerData.claimResizing,
                newClaim.getLesserBoundaryCorner().getBlockX(),
                newClaim.getGreaterBoundaryCorner().getBlockX(),
                newClaim.getLesserBoundaryCorner().getBlockY(),
                newClaim.getGreaterBoundaryCorner().getBlockY(),
                newClaim.getLesserBoundaryCorner().getBlockZ(),
                newClaim.getGreaterBoundaryCorner().getBlockZ(),
                player);

        if (result.succeeded && result.claim != null)
        {
            //decide how many claim blocks are available for more resizing
            int claimBlocksRemaining = 0;
            if (!playerData.claimResizing.isAdminClaim())
            {
                UUID ownerID = playerData.claimResizing.ownerID;
                if (playerData.claimResizing.parent != null)
                {
                    ownerID = playerData.claimResizing.parent.ownerID;
                }
                if (ownerID == player.getUniqueId())
                {
                    claimBlocksRemaining = playerData.getRemainingClaimBlocks();
                }
                else
                {
                    PlayerData ownerData = this.getPlayerData(ownerID);
                    claimBlocksRemaining = ownerData.getRemainingClaimBlocks();
                    OfflinePlayer owner = GriefPrevention.instance.getServer().getOfflinePlayer(ownerID);
                    if (!owner.isOnline())
                    {
                        this.clearCachedPlayerData(ownerID);
                    }
                }
            }

            //inform about success, visualize, communicate remaining blocks available
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.ClaimResizeSuccess, String.valueOf(claimBlocksRemaining));
            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);

            //if resizing someone else's claim, make a log entry
            if (!player.getUniqueId().equals(playerData.claimResizing.ownerID) && playerData.claimResizing.parent == null)
            {
                GriefPrevention.AddLogEntry(player.getName() + " resized " + playerData.claimResizing.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.lesserBoundaryCorner) + ".");
            }

            //if increased to a sufficiently large size and no subdivisions yet, send subdivision instructions
            if (oldClaim.getArea() < 1000 && result.claim.getArea() >= 1000 && result.claim.children.size() == 0 && !player.hasPermission("griefprevention.adminclaims"))
            {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.BecomeMayor, 200L);
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, 201L, DataStore.SUBDIVISION_VIDEO_URL);
            }

            //if in a creative mode world and shrinking an existing claim, restore any unclaimed area
            if (smaller && GriefPrevention.instance.creativeRulesApply(oldClaim.getLesserBoundaryCorner()))
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                GriefPrevention.instance.restoreClaim(oldClaim, 20L * 60 * 2);  //2 minutes
                GriefPrevention.AddLogEntry(player.getName() + " shrank a claim @ " + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.getLesserBoundaryCorner()));
            }

            //clean up
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;
        }
        else
        {
            if (result.claim != null)
            {
                //inform player
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlap);

                //show the player the conflicting claim
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);

                //log conflicting claim, if debug logs are enabled
                GriefPrevention.AddLogEntry(player.getName() + " attempted to create a claim, but conflicted with claim " + result.claim.id, CustomLogEntryTypes.Debug);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapRegion);
            }
        }
    }

    //educates a player about /adminclaims and /acb, if he can use them 
    void tryAdvertiseAdminAlternatives(Player player)
    {
        if (player.hasPermission("griefprevention.adminclaims") && player.hasPermission("griefprevention.adjustclaimblocks"))
        {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.AdvertiseACandACB);
        }
        else if (player.hasPermission("griefprevention.adminclaims"))
        {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.AdvertiseAdminClaims);
        }
        else if (player.hasPermission("griefprevention.adjustclaimblocks"))
        {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.AdvertiseACB);
        }
    }

    protected void loadMessages()
    {
        Messages[] messageIDs = Messages.values();
        this.messages = new String[Messages.values().length];

        HashMap<String, CustomizableMessage> defaults = new HashMap<>();

        //initialize defaults
        this.addDefault(defaults, Messages.RespectingClaims, "Now respecting claims.", null);
        this.addDefault(defaults, Messages.IgnoringClaims, "Now ignoring claims.", null);
        this.addDefault(defaults, Messages.NoCreativeUnClaim, "You can't unclaim this land.  You can only make this claim larger or create additional claims.", null);
        this.addDefault(defaults, Messages.SuccessfulAbandon, "Claims abandoned.  You now have {0} available claim blocks.", "0: remaining blocks");
        this.addDefault(defaults, Messages.RestoreNatureActivate, "Ready to restore some nature!  Right click to restore nature, and use /basicclaims to stop.", null);
        this.addDefault(defaults, Messages.RestoreNatureAggressiveActivate, "Aggressive mode activated.  Do NOT use this underneath anything you want to keep!  Right click to aggressively restore nature, and use /basicclaims to stop.", null);
        this.addDefault(defaults, Messages.FillModeActive, "Fill mode activated with radius {0}.  Right click an area to fill.", "0: fill radius");
        this.addDefault(defaults, Messages.TransferClaimPermission, "That command requires the administrative claims permission.", null);
        this.addDefault(defaults, Messages.TransferClaimMissing, "There's no claim here.  Stand in the administrative claim you want to transfer.", null);
        this.addDefault(defaults, Messages.TransferClaimAdminOnly, "Only administrative claims may be transferred to a player.", null);
        this.addDefault(defaults, Messages.PlayerNotFound2, "No player by that name has logged in recently.", null);
        this.addDefault(defaults, Messages.TransferTopLevel, "Only top level claims (not subdivisions) may be transferred.  Stand outside of the subdivision and try again.", null);
        this.addDefault(defaults, Messages.TransferSuccess, "Claim transferred.", null);
        this.addDefault(defaults, Messages.TrustListNoClaim, "Stand inside the claim you're curious about.", null);
        this.addDefault(defaults, Messages.ClearPermsOwnerOnly, "Only the claim owner can clear all permissions.", null);
        this.addDefault(defaults, Messages.UntrustIndividualAllClaims, "Revoked {0}'s access to ALL your claims.  To set permissions for a single claim, stand inside it.", "0: untrusted player");
        this.addDefault(defaults, Messages.UntrustEveryoneAllClaims, "Cleared permissions in ALL your claims.  To set permissions for a single claim, stand inside it.", null);
        this.addDefault(defaults, Messages.NoPermissionTrust, "You don't have {0}'s permission to manage permissions here.", "0: claim owner's name");
        this.addDefault(defaults, Messages.ClearPermissionsOneClaim, "Cleared permissions in this claim.  To set permission for ALL your claims, stand outside them.", null);
        this.addDefault(defaults, Messages.UntrustIndividualSingleClaim, "Revoked {0}'s access to this claim.  To set permissions for a ALL your claims, stand outside them.", "0: untrusted player");
        this.addDefault(defaults, Messages.OnlySellBlocks, "Claim blocks may only be sold, not purchased.", null);
        this.addDefault(defaults, Messages.BlockPurchaseCost, "Each claim block costs {0}.  Your balance is {1}.", "0: cost of one block; 1: player's account balance");
        this.addDefault(defaults, Messages.ClaimBlockLimit, "You've reached your claim block limit.  You can't purchase more.", null);
        this.addDefault(defaults, Messages.InsufficientFunds, "You don't have enough money.  You need {0}, but you only have {1}.", "0: total cost; 1: player's account balance");
        this.addDefault(defaults, Messages.MaxBonusReached, "Can't purchase {0} more claim blocks. The server has a limit of {1} bonus claim blocks.", "0: block count; 1: bonus claims limit");
        this.addDefault(defaults, Messages.PurchaseConfirmation, "Withdrew {0} from your account.  You now have {1} available claim blocks.", "0: total cost; 1: remaining blocks");
        this.addDefault(defaults, Messages.OnlyPurchaseBlocks, "Claim blocks may only be purchased, not sold.", null);
        this.addDefault(defaults, Messages.BlockSaleValue, "Each claim block is worth {0}.  You have {1} available for sale.", "0: block value; 1: available blocks");
        this.addDefault(defaults, Messages.NotEnoughBlocksForSale, "You don't have that many claim blocks available for sale.", null);
        this.addDefault(defaults, Messages.BlockSaleConfirmation, "Deposited {0} in your account.  You now have {1} available claim blocks.", "0: amount deposited; 1: remaining blocks");
        this.addDefault(defaults, Messages.AdminClaimsMode, "Administrative claims mode active.  Any claims created will be free and editable by other administrators.", null);
        this.addDefault(defaults, Messages.BasicClaimsMode, "Returned to basic claim creation mode.", null);
        this.addDefault(defaults, Messages.SubdivisionMode, "Subdivision mode.  Use your shovel to create subdivisions in your existing claims.  Use /basicclaims to exit.", null);
        this.addDefault(defaults, Messages.SubdivisionVideo2, "Click for Subdivision Help: {0}", "0:video URL");
        this.addDefault(defaults, Messages.DeleteClaimMissing, "There's no claim here.", null);
        this.addDefault(defaults, Messages.DeletionSubdivisionWarning, "This claim includes subdivisions.  If you're sure you want to delete it, use /deleteclaim again.", null);
        this.addDefault(defaults, Messages.DeleteSuccess, "Claim deleted.", null);
        this.addDefault(defaults, Messages.CantDeleteAdminClaim, "You don't have permission to delete administrative claims.", null);
        this.addDefault(defaults, Messages.DeleteAllSuccess, "Deleted all of {0}'s claims.", "0: owner's name");
        this.addDefault(defaults, Messages.NoDeletePermission, "You don't have permission to delete claims.", null);
        this.addDefault(defaults, Messages.AllAdminDeleted, "Deleted all administrative claims.", null);
        this.addDefault(defaults, Messages.AdjustBlocksSuccess, "Adjusted {0}'s bonus claim blocks by {1}.  New total bonus blocks: {2}.", "0: player; 1: adjustment; 2: new total");
        this.addDefault(defaults, Messages.AdjustBlocksAllSuccess, "Adjusted all online players' bonus claim blocks by {0}.", "0: adjustment amount");
        this.addDefault(defaults, Messages.NotTrappedHere, "You can build here.  Save yourself.", null);
        this.addDefault(defaults, Messages.RescuePending, "If you stay put for 10 seconds, you'll be teleported out.  Please wait.", null);
        this.addDefault(defaults, Messages.NonSiegeWorld, "Siege is disabled here.", null);
        this.addDefault(defaults, Messages.AlreadySieging, "You're already involved in a siege.", null);
        this.addDefault(defaults, Messages.AlreadyUnderSiegePlayer, "{0} is already under siege.  Join the party!", "0: defending player");
        this.addDefault(defaults, Messages.NotSiegableThere, "{0} isn't protected there.", "0: defending player");
        this.addDefault(defaults, Messages.SiegeTooFarAway, "You're too far away to siege.", null);
        this.addDefault(defaults, Messages.NoSiegeYourself, "You cannot siege yourself, don't be silly", null);
        this.addDefault(defaults, Messages.NoSiegeDefenseless, "That player is defenseless.  Go pick on somebody else.", null);
        this.addDefault(defaults, Messages.AlreadyUnderSiegeArea, "That area is already under siege.  Join the party!", null);
        this.addDefault(defaults, Messages.NoSiegeAdminClaim, "Siege is disabled in this area.", null);
        this.addDefault(defaults, Messages.SiegeOnCooldown, "You're still on siege cooldown for this defender or claim.  Find another victim.", null);
        this.addDefault(defaults, Messages.SiegeAlert, "You're under siege!  If you log out now, you will die.  You must defeat {0}, wait for him to give up, or escape.", "0: attacker name");
        this.addDefault(defaults, Messages.SiegeConfirmed, "The siege has begun!  If you log out now, you will die.  You must defeat {0}, chase him away, or admit defeat and walk away.", "0: defender name");
        this.addDefault(defaults, Messages.AbandonClaimMissing, "Stand in the claim you want to delete, or consider /abandonallclaims.", null);
        this.addDefault(defaults, Messages.NotYourClaim, "This isn't your claim.", null);
        this.addDefault(defaults, Messages.DeleteTopLevelClaim, "To delete a subdivision, stand inside it.  Otherwise, use /abandontoplevelclaim to delete this claim and all subdivisions.", null);
        this.addDefault(defaults, Messages.AbandonSuccess, "Claim abandoned.  You now have {0} available claim blocks.", "0: remaining claim blocks");
        this.addDefault(defaults, Messages.ConfirmAbandonAllClaims, "Are you sure you want to abandon ALL of your claims?  Please confirm with /abandonallclaims confirm", null);
        this.addDefault(defaults, Messages.CantGrantThatPermission, "You can't grant a permission you don't have yourself.", null);
        this.addDefault(defaults, Messages.GrantPermissionNoClaim, "Stand inside the claim where you want to grant permission.", null);
        this.addDefault(defaults, Messages.GrantPermissionConfirmation, "Granted {0} permission to {1} {2}.", "0: target player; 1: permission description; 2: scope (changed claims)");
        this.addDefault(defaults, Messages.ManageUniversalPermissionsInstruction, "To manage permissions for ALL your claims, stand outside them.", null);
        this.addDefault(defaults, Messages.ManageOneClaimPermissionsInstruction, "To manage permissions for a specific claim, stand inside it.", null);
        this.addDefault(defaults, Messages.CollectivePublic, "the public", "as in 'granted the public permission to...'");
        this.addDefault(defaults, Messages.BuildPermission, "build", null);
        this.addDefault(defaults, Messages.ContainersPermission, "access containers and animals", null);
        this.addDefault(defaults, Messages.AccessPermission, "use buttons and levers", null);
        this.addDefault(defaults, Messages.PermissionsPermission, "manage permissions", null);
        this.addDefault(defaults, Messages.LocationCurrentClaim, "in this claim", null);
        this.addDefault(defaults, Messages.LocationAllClaims, "in all your claims", null);
        this.addDefault(defaults, Messages.PvPImmunityStart, "You're protected from attack by other players as long as your inventory is empty.", null);
        this.addDefault(defaults, Messages.SiegeNoDrop, "You can't give away items while involved in a siege.", null);
        this.addDefault(defaults, Messages.DonateItemsInstruction, "To give away the item(s) in your hand, left-click the chest again.", null);
        this.addDefault(defaults, Messages.ChestFull, "This chest is full.", null);
        this.addDefault(defaults, Messages.DonationSuccess, "Item(s) transferred to chest!", null);
        this.addDefault(defaults, Messages.PlayerTooCloseForFire2, "You can't start a fire this close to another player.", null);
        this.addDefault(defaults, Messages.TooDeepToClaim, "This chest can't be protected because it's too deep underground.  Consider moving it.", null);
        this.addDefault(defaults, Messages.ChestClaimConfirmation, "This chest is protected.", null);
        this.addDefault(defaults, Messages.AutomaticClaimNotification, "This chest and nearby blocks are protected from breakage and theft.", null);
        this.addDefault(defaults, Messages.AutomaticClaimOtherClaimTooClose, "Cannot create a claim for your chest, there is another claim too close!", null);
        this.addDefault(defaults, Messages.UnprotectedChestWarning, "This chest is NOT protected.  Consider using a golden shovel to expand an existing claim or to create a new one.", null);
        this.addDefault(defaults, Messages.ThatPlayerPvPImmune, "You can't injure defenseless players.", null);
        this.addDefault(defaults, Messages.CantFightWhileImmune, "You can't fight someone while you're protected from PvP.", null);
        this.addDefault(defaults, Messages.NoDamageClaimedEntity, "That belongs to {0}.", "0: owner name");
        this.addDefault(defaults, Messages.ShovelBasicClaimMode, "Shovel returned to basic claims mode.", null);
        this.addDefault(defaults, Messages.RemainingBlocks, "You may claim up to {0} more blocks.", "0: remaining blocks");
        this.addDefault(defaults, Messages.CreativeBasicsVideo2, "Click for Land Claim Help: {0}", "{0}: video URL");
        this.addDefault(defaults, Messages.SurvivalBasicsVideo2, "Click for Land Claim Help: {0}", "{0}: video URL");
        this.addDefault(defaults, Messages.TrappedChatKeyword, "trapped;stuck", "When mentioned in chat, players get information about the /trapped command (multiple words can be separated with semi-colons)");
        this.addDefault(defaults, Messages.TrappedInstructions, "Are you trapped in someone's land claim?  Try the /trapped command.", null);
        this.addDefault(defaults, Messages.PvPNoDrop, "You can't drop items while in PvP combat.", null);
        this.addDefault(defaults, Messages.SiegeNoTeleport, "You can't teleport out of a besieged area.", null);
        this.addDefault(defaults, Messages.BesiegedNoTeleport, "You can't teleport into a besieged area.", null);
        this.addDefault(defaults, Messages.SiegeNoContainers, "You can't access containers while involved in a siege.", null);
        this.addDefault(defaults, Messages.PvPNoContainers, "You can't access containers during PvP combat.", null);
        this.addDefault(defaults, Messages.PvPImmunityEnd, "Now you can fight with other players.", null);
        this.addDefault(defaults, Messages.NoBedPermission, "{0} hasn't given you permission to sleep here.", "0: claim owner");
        this.addDefault(defaults, Messages.NoWildernessBuckets, "You may only dump buckets inside your claim(s) or underground.", null);
        this.addDefault(defaults, Messages.NoLavaNearOtherPlayer, "You can't place lava this close to {0}.", "0: nearby player");
        this.addDefault(defaults, Messages.TooFarAway, "That's too far away.", null);
        this.addDefault(defaults, Messages.BlockNotClaimed, "No one has claimed this block.", null);
        this.addDefault(defaults, Messages.BlockClaimed, "That block has been claimed by {0}.", "0: claim owner");
        this.addDefault(defaults, Messages.SiegeNoShovel, "You can't use your shovel tool while involved in a siege.", null);
        this.addDefault(defaults, Messages.RestoreNaturePlayerInChunk, "Unable to restore.  {0} is in that chunk.", "0: nearby player");
        this.addDefault(defaults, Messages.NoCreateClaimPermission, "You don't have permission to claim land.", null);
        this.addDefault(defaults, Messages.ResizeClaimTooNarrow, "This new size would be too small.  Claims must be at least {0} blocks wide.", "0: minimum claim width");
        this.addDefault(defaults, Messages.ResizeNeedMoreBlocks, "You don't have enough blocks for this size.  You need {0} more.", "0: how many needed");
        this.addDefault(defaults, Messages.ClaimResizeSuccess, "Claim resized.  {0} available claim blocks remaining.", "0: remaining blocks");
        this.addDefault(defaults, Messages.ResizeFailOverlap, "Can't resize here because it would overlap another nearby claim.", null);
        this.addDefault(defaults, Messages.ResizeStart, "Resizing claim.  Use your shovel again at the new location for this corner.", null);
        this.addDefault(defaults, Messages.ResizeFailOverlapSubdivision, "You can't create a subdivision here because it would overlap another subdivision.  Consider /abandonclaim to delete it, or use your shovel at a corner to resize it.", null);
        this.addDefault(defaults, Messages.SubdivisionStart, "Subdivision corner set!  Use your shovel at the location for the opposite corner of this new subdivision.", null);
        this.addDefault(defaults, Messages.CreateSubdivisionOverlap, "Your selected area overlaps another subdivision.", null);
        this.addDefault(defaults, Messages.SubdivisionSuccess, "Subdivision created!  Use /trust to share it with friends.", null);
        this.addDefault(defaults, Messages.CreateClaimFailOverlap, "You can't create a claim here because it would overlap your other claim.  Use /abandonclaim to delete it, or use your shovel at a corner to resize it.", null);
        this.addDefault(defaults, Messages.CreateClaimFailOverlapOtherPlayer, "You can't create a claim here because it would overlap {0}'s claim.", "0: other claim owner");
        this.addDefault(defaults, Messages.ClaimsDisabledWorld, "Land claims are disabled in this world.", null);
        this.addDefault(defaults, Messages.ClaimStart, "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away.", null);
        this.addDefault(defaults, Messages.NewClaimTooNarrow, "This claim would be too small.  Any claim must be at least {0} blocks wide.", "0: minimum claim width");
        this.addDefault(defaults, Messages.ResizeClaimInsufficientArea, "This claim would be too small.  Any claim must use at least {0} total claim blocks.", "0: minimum claim area");
        this.addDefault(defaults, Messages.CreateClaimInsufficientBlocks, "You don't have enough blocks to claim that entire area.  You need {0} more blocks.", "0: additional blocks needed");
        this.addDefault(defaults, Messages.AbandonClaimAdvertisement, "To delete another claim and free up some blocks, use /abandonclaim.", null);
        this.addDefault(defaults, Messages.CreateClaimFailOverlapShort, "Your selected area overlaps an existing claim.", null);
        this.addDefault(defaults, Messages.CreateClaimSuccess, "Claim created!  Use /trust to share it with friends.", null);
        this.addDefault(defaults, Messages.SiegeWinDoorsOpen, "Congratulations!  Buttons and levers are temporarily unlocked.", null);
        this.addDefault(defaults, Messages.RescueAbortedMoved, "You moved!  Rescue cancelled.", null);
        this.addDefault(defaults, Messages.SiegeDoorsLockedEjection, "Looting time is up!  Ejected from the claim.", null);
        this.addDefault(defaults, Messages.NoModifyDuringSiege, "Claims can't be modified while under siege.", null);
        this.addDefault(defaults, Messages.OnlyOwnersModifyClaims, "Only {0} can modify this claim.", "0: owner name");
        this.addDefault(defaults, Messages.NoBuildUnderSiege, "This claim is under siege by {0}.  No one can build here.", "0: attacker name");
        this.addDefault(defaults, Messages.NoBuildPvP, "You can't build in claims during PvP combat.", null);
        this.addDefault(defaults, Messages.NoBuildPermission, "You don't have {0}'s permission to build here.", "0: owner name");
        this.addDefault(defaults, Messages.NonSiegeMaterial, "That material is too tough to break.", null);
        this.addDefault(defaults, Messages.NoOwnerBuildUnderSiege, "You can't make changes while under siege.", null);
        this.addDefault(defaults, Messages.NoAccessPermission, "You don't have {0}'s permission to use that.", "0: owner name.  access permission controls buttons, levers, and beds");
        this.addDefault(defaults, Messages.NoContainersSiege, "This claim is under siege by {0}.  No one can access containers here right now.", "0: attacker name");
        this.addDefault(defaults, Messages.NoContainersPermission, "You don't have {0}'s permission to use that.", "0: owner's name.  containers also include crafting blocks");
        this.addDefault(defaults, Messages.OwnerNameForAdminClaims, "an administrator", "as in 'You don't have an administrator's permission to build here.'");
        this.addDefault(defaults, Messages.UnknownPlayerName, "someone", "Name used for unknown players. UUID will be appended if available: \"someone (01234567-0123-0123-0123-0123456789ab)\"");
        this.addDefault(defaults, Messages.ClaimTooSmallForEntities, "This claim isn't big enough for that.  Try enlarging it.", null);
        this.addDefault(defaults, Messages.TooManyEntitiesInClaim, "This claim has too many entities already.  Try enlarging the claim or removing some animals, monsters, paintings, or minecarts.", null);
        this.addDefault(defaults, Messages.YouHaveNoClaims, "You don't have any land claims.", null);
        this.addDefault(defaults, Messages.ConfirmFluidRemoval, "Abandoning this claim will remove lava inside the claim.  If you're sure, use /abandonclaim again.", null);
        this.addDefault(defaults, Messages.AutoBanNotify, "Auto-banned {0}({1}).  See logs for details.", null);
        this.addDefault(defaults, Messages.AdjustGroupBlocksSuccess, "Adjusted bonus claim blocks for players with the {0} permission by {1}.  New total: {2}.", "0: permission; 1: adjustment amount; 2: new total bonus");
        this.addDefault(defaults, Messages.InvalidPermissionID, "Please specify a player name, or a permission in [brackets].", null);
        this.addDefault(defaults, Messages.HowToClaimRegex, "(^|.*\\W)how\\W.*\\W(claim|protect|lock)(\\W.*|$)", "This is a Java Regular Expression.  Look it up before editing!  It's used to tell players about the demo video when they ask how to claim land.");
        this.addDefault(defaults, Messages.NoBuildOutsideClaims, "You can't build here unless you claim some land first.", null);
        this.addDefault(defaults, Messages.PlayerOfflineTime, "  Last login: {0} days ago.", "0: number of full days since last login");
        this.addDefault(defaults, Messages.BuildingOutsideClaims, "Other players can build here, too.  Consider creating a land claim to protect your work!", null);
        this.addDefault(defaults, Messages.TrappedWontWorkHere, "Sorry, unable to find a safe location to teleport you to.  Contact an admin.", null);
        this.addDefault(defaults, Messages.CommandBannedInPvP, "You can't use that command while in PvP combat.", null);
        this.addDefault(defaults, Messages.UnclaimCleanupWarning, "The land you've unclaimed may be changed by other players or cleaned up by administrators.  If you've built something there you want to keep, you should reclaim it.", null);
        this.addDefault(defaults, Messages.BuySellNotConfigured, "Sorry, buying and selling claim blocks is disabled.", null);
        this.addDefault(defaults, Messages.NoTeleportPvPCombat, "You can't teleport while fighting another player.", null);
        this.addDefault(defaults, Messages.NoTNTDamageAboveSeaLevel, "Warning: TNT will not destroy blocks above sea level.", null);
        this.addDefault(defaults, Messages.NoTNTDamageClaims, "Warning: TNT will not destroy claimed blocks.", null);
        this.addDefault(defaults, Messages.IgnoreClaimsAdvertisement, "To override, use /ignoreclaims.", null);
        this.addDefault(defaults, Messages.NoPermissionForCommand, "You don't have permission to do that.", null);
        this.addDefault(defaults, Messages.ClaimsListNoPermission, "You don't have permission to get information about another player's land claims.", null);
        this.addDefault(defaults, Messages.ExplosivesDisabled, "This claim is now protected from explosions.  Use /claimexplosions again to disable.", null);
        this.addDefault(defaults, Messages.ExplosivesEnabled, "This claim is now vulnerable to explosions.  Use /claimexplosions again to re-enable protections.", null);
        this.addDefault(defaults, Messages.ClaimExplosivesAdvertisement, "To allow explosives to destroy blocks in this land claim, use /claimexplosions.", null);
        this.addDefault(defaults, Messages.PlayerInPvPSafeZone, "That player is in a PvP safe zone.", null);
        this.addDefault(defaults, Messages.NoPistonsOutsideClaims, "Warning: Pistons won't move blocks outside land claims.", null);
        this.addDefault(defaults, Messages.SoftMuted, "Soft-muted {0}.", "0: The changed player's name.");
        this.addDefault(defaults, Messages.UnSoftMuted, "Un-soft-muted {0}.", "0: The changed player's name.");
        this.addDefault(defaults, Messages.DropUnlockAdvertisement, "Other players can't pick up your dropped items unless you /unlockdrops first.", null);
        this.addDefault(defaults, Messages.PickupBlockedExplanation, "You can't pick this up unless {0} uses /unlockdrops.", "0: The item stack's owner.");
        this.addDefault(defaults, Messages.DropUnlockConfirmation, "Unlocked your drops.  Other players may now pick them up (until you die again).", null);
        this.addDefault(defaults, Messages.DropUnlockOthersConfirmation, "Unlocked {0}'s drops.", "0: The owner of the unlocked drops.");
        this.addDefault(defaults, Messages.AdvertiseACandACB, "You may use /acb to give yourself more claim blocks, or /adminclaims to create a free administrative claim.", null);
        this.addDefault(defaults, Messages.AdvertiseAdminClaims, "You could create an administrative land claim instead using /adminclaims, which you'd share with other administrators.", null);
        this.addDefault(defaults, Messages.AdvertiseACB, "You may use /acb to give yourself more claim blocks.", null);
        this.addDefault(defaults, Messages.NotYourPet, "That belongs to {0} until it's given to you with /givepet.", "0: owner name");
        this.addDefault(defaults, Messages.PetGiveawayConfirmation, "Pet transferred.", null);
        this.addDefault(defaults, Messages.PetTransferCancellation, "Pet giveaway cancelled.", null);
        this.addDefault(defaults, Messages.ReadyToTransferPet, "Ready to transfer!  Right-click the pet you'd like to give away, or cancel with /givepet cancel.", null);
        this.addDefault(defaults, Messages.AvoidGriefClaimLand, "Prevent grief!  If you claim your land, you will be grief-proof.", null);
        this.addDefault(defaults, Messages.BecomeMayor, "Subdivide your land claim and become a mayor!", null);
        this.addDefault(defaults, Messages.ClaimCreationFailedOverClaimCountLimit, "You've reached your limit on land claims.  Use /abandonclaim to remove one before creating another.", null);
        this.addDefault(defaults, Messages.CreateClaimFailOverlapRegion, "You can't claim all of this because you're not allowed to build here.", null);
        this.addDefault(defaults, Messages.ResizeFailOverlapRegion, "You don't have permission to build there, so you can't claim that area.", null);
        this.addDefault(defaults, Messages.ShowNearbyClaims, "Found {0} land claims.", "0: Number of claims found.");
        this.addDefault(defaults, Messages.NoChatUntilMove, "Sorry, but you have to move a little more before you can chat.  We get lots of spam bots here.  :)", null);
        this.addDefault(defaults, Messages.SiegeImmune, "That player is immune to /siege.", null);
        this.addDefault(defaults, Messages.SetClaimBlocksSuccess, "Updated accrued claim blocks.", null);
        this.addDefault(defaults, Messages.IgnoreConfirmation, "You're now ignoring chat messages from that player.", null);
        this.addDefault(defaults, Messages.UnIgnoreConfirmation, "You're no longer ignoring chat messages from that player.", null);
        this.addDefault(defaults, Messages.NotIgnoringPlayer, "You're not ignoring that player.", null);
        this.addDefault(defaults, Messages.SeparateConfirmation, "Those players will now ignore each other in chat.", null);
        this.addDefault(defaults, Messages.UnSeparateConfirmation, "Those players will no longer ignore each other in chat.", null);
        this.addDefault(defaults, Messages.NotIgnoringAnyone, "You're not ignoring anyone.", null);
        this.addDefault(defaults, Messages.TrustListHeader, "Explicit permissions here:", "0: The claim's owner");
        this.addDefault(defaults, Messages.Manage, "Manage", null);
        this.addDefault(defaults, Messages.Build, "Build", null);
        this.addDefault(defaults, Messages.Containers, "Containers", null);
        this.addDefault(defaults, Messages.Access, "Access", null);
        this.addDefault(defaults, Messages.HasSubclaimRestriction, "This subclaim does not inherit permissions from the parent", null);
        this.addDefault(defaults, Messages.StartBlockMath, "{0} blocks from play + {1} bonus = {2} total.", null);
        this.addDefault(defaults, Messages.ClaimsListHeader, "Claims:", null);
        this.addDefault(defaults, Messages.ContinueBlockMath, " (-{0} blocks)", null);
        this.addDefault(defaults, Messages.EndBlockMath, " = {0} blocks left to spend", null);
        this.addDefault(defaults, Messages.NoClaimDuringPvP, "You can't claim lands during PvP combat.", null);
        this.addDefault(defaults, Messages.UntrustAllOwnerOnly, "Only the claim owner can clear all its permissions.", null);
        this.addDefault(defaults, Messages.ManagersDontUntrustManagers, "Only the claim owner can demote a manager.", null);
        this.addDefault(defaults, Messages.PlayerNotIgnorable, "You can't ignore that player.", null);
        this.addDefault(defaults, Messages.NoEnoughBlocksForChestClaim, "Because you don't have any claim blocks available, no automatic land claim was created for you.  You can use /claimslist to monitor your available claim block total.", null);
        this.addDefault(defaults, Messages.MustHoldModificationToolForThat, "You must be holding a golden shovel to do that.", null);
        this.addDefault(defaults, Messages.StandInClaimToResize, "Stand inside the land claim you want to resize.", null);
        this.addDefault(defaults, Messages.ClaimsExtendToSky, "Land claims always extend to max build height.", null);
        this.addDefault(defaults, Messages.ClaimsAutoExtendDownward, "Land claims auto-extend deeper into the ground when you place blocks under them.", null);
        this.addDefault(defaults, Messages.MinimumRadius, "Minimum radius is {0}.", "0: minimum radius");
        this.addDefault(defaults, Messages.RadiusRequiresGoldenShovel, "You must be holding a golden shovel when specifying a radius.", null);
        this.addDefault(defaults, Messages.ClaimTooSmallForActiveBlocks, "This claim isn't big enough to support any active block types (hoppers, spawners, beacons...).  Make the claim bigger first.", null);
        this.addDefault(defaults, Messages.TooManyActiveBlocksInClaim, "This claim is at its limit for active block types (hoppers, spawners, beacons...).  Either make it bigger, or remove other active blocks first.", null);

        this.addDefault(defaults, Messages.BookAuthor, "BigScary", null);
        this.addDefault(defaults, Messages.BookTitle, "How to Claim Land", null);
        this.addDefault(defaults, Messages.BookLink, "Click: {0}", "{0}: video URL");
        this.addDefault(defaults, Messages.BookIntro, "Claim land to protect your stuff!  Click the link above to learn land claims in 3 minutes or less.  :)", null);
        this.addDefault(defaults, Messages.BookTools, "Our claim tools are {0} and {1}.", "0: claim modification tool name; 1:claim information tool name");
        this.addDefault(defaults, Messages.BookDisabledChestClaims, "  On this server, placing a chest will NOT claim land for you.", null);
        this.addDefault(defaults, Messages.BookUsefulCommands, "Useful Commands:", null);
        this.addDefault(defaults, Messages.NoProfanity, "Please moderate your language.", null);
        this.addDefault(defaults, Messages.IsIgnoringYou, "That player is ignoring you.", null);
        this.addDefault(defaults, Messages.ConsoleOnlyCommand, "That command may only be executed from the server console.", null);
        this.addDefault(defaults, Messages.WorldNotFound, "World not found.", null);
        this.addDefault(defaults, Messages.TooMuchIpOverlap, "Sorry, there are too many players logged in with your IP address.", null);

        this.addDefault(defaults, Messages.StandInSubclaim, "You need to be standing in a subclaim to restrict it", null);
        this.addDefault(defaults, Messages.SubclaimRestricted, "This subclaim's permissions will no longer inherit from the parent claim", null);
        this.addDefault(defaults, Messages.SubclaimUnrestricted, "This subclaim's permissions will now inherit from the parent claim", null);

        this.addDefault(defaults, Messages.NetherPortalTrapDetectionMessage, "It seems you might be stuck inside a nether portal. We will rescue you in a few seconds if that is the case!", "Sent to player on join, if they left while inside a nether portal.");

        //load the config file
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

        //for each message ID
        for (Messages messageID : messageIDs)
        {
            //get default for this message
            CustomizableMessage messageData = defaults.get(messageID.name());

            //if default is missing, log an error and use some fake data for now so that the plugin can run
            if (messageData == null)
            {
                GriefPrevention.AddLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
                messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
            }

            //read the message from the file, use default if necessary
            this.messages[messageID.ordinal()] = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
            config.set("Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);

            //support color codes
            if (messageID != Messages.HowToClaimRegex)
            {
                this.messages[messageID.ordinal()] = this.messages[messageID.ordinal()].replace('$', (char) 0x00A7);
            }

            if (messageData.notes != null)
            {
                messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
                config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
            }
        }

        //save any changes
        try
        {
            config.options().header("Use a YAML editor like NotepadPlusPlus to edit this file.  \nAfter editing, back up your changes before reloading the server in case you made a syntax error.  \nUse dollar signs ($) for formatting codes, which are documented here: http://minecraft.gamepedia.com/Formatting_codes");
            config.save(DataStore.messagesFilePath);
        }
        catch (IOException exception)
        {
            GriefPrevention.AddLogEntry("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
        }

        defaults.clear();
        System.gc();
    }

    private void addDefault(HashMap<String, CustomizableMessage> defaults,
                            Messages id, String text, String notes)
    {
        CustomizableMessage message = new CustomizableMessage(id, text, notes);
        defaults.put(id.name(), message);
    }

    synchronized public String getMessage(Messages messageID, String... args)
    {
        String message = messages[messageID.ordinal()];

        for (int i = 0; i < args.length; i++)
        {
            String param = args[i];
            message = message.replace("{" + i + "}", param);
        }

        return message;
    }

    //used in updating the data schema from 0 to 1.
    //converts player names in a list to uuids
    protected List<String> convertNameListToUUIDList(List<String> names)
    {
        //doesn't apply after schema has been updated to version 1
        if (this.getSchemaVersion() >= 1) return names;

        //list to build results
        List<String> resultNames = new ArrayList<>();

        for (String name : names)
        {
            //skip non-player-names (groups and "public"), leave them as-is
            if (name.startsWith("[") || name.equals("public"))
            {
                resultNames.add(name);
                continue;
            }

            //otherwise try to convert to a UUID
            UUID playerID = null;
            try
            {
                playerID = UUIDFetcher.getUUIDOf(name);
            }
            catch (Exception ex) { }

            //if successful, replace player name with corresponding UUID
            if (playerID != null)
            {
                resultNames.add(playerID.toString());
            }
        }

        return resultNames;
    }

    abstract void close();

    private class SavePlayerDataThread extends Thread
    {
        private final UUID playerID;
        private final PlayerData playerData;

        SavePlayerDataThread(UUID playerID, PlayerData playerData)
        {
            this.playerID = playerID;
            this.playerData = playerData;
        }

        public void run()
        {
            //ensure player data is already read from file before trying to save
            playerData.getAccruedClaimBlocks();
            playerData.getClaims();
            asyncSavePlayerData(this.playerID, this.playerData);
        }
    }

    //gets all the claims "near" a location
    Set<Claim> getNearbyClaims(Location location)
    {
        Set<Claim> claims = new HashSet<>();

        Chunk lesserChunk = location.getWorld().getChunkAt(location.subtract(150, 0, 150));
        Chunk greaterChunk = location.getWorld().getChunkAt(location.add(300, 0, 300));

        for (int chunk_x = lesserChunk.getX(); chunk_x <= greaterChunk.getX(); chunk_x++)
        {
            for (int chunk_z = lesserChunk.getZ(); chunk_z <= greaterChunk.getZ(); chunk_z++)
            {
                Chunk chunk = location.getWorld().getChunkAt(chunk_x, chunk_z);
                Long chunkID = getChunkHash(chunk.getBlock(0, 0, 0).getLocation());
                ArrayList<Claim> claimsInChunk = this.chunksToClaimsMap.get(chunkID);
                if (claimsInChunk != null)
                {
                    for (Claim claim : claimsInChunk)
                    {
                        if (claim.inDataStore && claim.getLesserBoundaryCorner().getWorld().equals(location.getWorld()))
                        {
                            claims.add(claim);
                        }
                    }
                }
            }
        }

        return claims;
    }

    //deletes all the land claims in a specified world
    void deleteClaimsInWorld(World world, boolean deleteAdminClaims)
    {
        for (int i = 0; i < claims.size(); i++)
        {
            Claim claim = claims.get(i);
            if (claim.getLesserBoundaryCorner().getWorld().equals(world))
            {
                if (!deleteAdminClaims && claim.isAdminClaim()) continue;
                this.deleteClaim(claim, false, false);
                i--;
            }
        }
    }
}
