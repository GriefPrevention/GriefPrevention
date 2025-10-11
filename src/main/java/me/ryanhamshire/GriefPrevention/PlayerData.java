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

import java.net.InetAddress;
import java.util.Calendar;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import org.jetbrains.annotations.Nullable;

import com.griefprevention.visualization.BoundaryVisualization;

//holds all of GriefPrevention's player-tied data
public class PlayerData
{

    //the player's ID
    public UUID playerID;

    //the player's claims
    private Vector<Claim> claims = null;

    //how many claim blocks the player has earned via play time
    private Integer accruedClaimBlocks = null;

    //temporary holding area to avoid opening data files too early
    private int newlyAccruedClaimBlocks = 0;

    //where this player was the last time we checked on him for earning claim blocks
    public Location lastAfkCheckLocation = null;

    //how many claim blocks the player has been gifted by admins, or purchased via economy integration
    private Integer bonusClaimBlocks = null;

    //what "mode" the shovel is in determines what it will do when it's used
    public ShovelMode shovelMode = ShovelMode.Basic;

    //last place the player used the shovel, useful in creating and resizing claims,
    //because the player must use the shovel twice in those instances
    public Location lastShovelLocation = null;

    //the claim this player is currently resizing
    public Claim claimResizing = null;

    //the claim this player is currently subdividing
    public Claim claimSubdividing = null;

    //whether or not the player has a pending /trapped rescue
    public boolean pendingTrapped = false;

    //whether this player was recently warned about building outside land claims
    boolean warnedAboutBuildingOutsideClaims = false;

    //whether the player was kicked (set and used during logout)
    boolean wasKicked = false;

    //visualization
    private transient @Nullable BoundaryVisualization visibleBoundaries = null;

    /** @deprecated Use {@link #getVisibleBoundaries} and {@link #setVisibleBoundaries(BoundaryVisualization)} */
    @Deprecated(forRemoval = true, since = "16.18")
    public Visualization currentVisualization = null;

    //anti-camping pvp protection
    public boolean pvpImmune = false;

    public long lastSpawn = 0;

    //ignore claims mode
    public boolean ignoreClaims = false;

    //the last claim this player was in, that we know of
    public Claim lastClaim = null;

    //pvp
    public long lastPvpTimestamp = 0;

    public String lastPvpPlayer = "";

    //safety confirmation for deleting multi-subdivision claims
    public boolean warnedAboutMajorDeletion = false;

    public InetAddress ipAddress;

    //for addons to set per-player claim limits. Any negative value will use config's value
    private int AccruedClaimBlocksLimit = -1;

    //whether or not this player has received a message about unlocking death drops since his last death
    boolean receivedDropUnlockAdvertisement = false;

    //whether or not this player's dropped items (on death) are unlocked for other players to pick up
    boolean dropsAreUnlocked = false;

    //message to send to player after he respawns
    String messageOnRespawn = null;

    //timestamp for last "you're building outside your land claims" message
    Long buildWarningTimestamp = null;

    //timestamp for last warning when placing TNT on explosion protected claim
    Long explosivesWarningTimestamp = null;

    //spot where a player can't talk, used to mute new players until they've moved a little
    //this is an anti-bot strategy.
    Location noChatLocation = null;

    //ignore list
    //true means invisible (admin-forced ignore), false means player-created ignore
    public ConcurrentHashMap<UUID, Boolean> ignoredPlayers = new ConcurrentHashMap<>();

    public boolean ignoreListChanged = false;

    //profanity warning, once per play session
    boolean profanityWarned = false;

    //whether or not this player is "in" pvp combat
    public boolean inPvpCombat()
    {
        if (this.lastPvpTimestamp == 0) return false;

        long now = Calendar.getInstance().getTimeInMillis();

        long elapsed = now - this.lastPvpTimestamp;

        if (elapsed > GriefPrevention.instance.config_pvp_combatTimeoutSeconds * 1000) //X seconds
        {
            this.lastPvpTimestamp = 0;
            return false;
        }

        return true;
    }

    //the number of claim blocks a player has available for claiming land
    public int getRemainingClaimBlocks()
    {
        int remainingBlocks;
        try
        {
            remainingBlocks = Math.addExact(
                    Math.addExact(this.getAccruedClaimBlocks(), this.getBonusClaimBlocks()),
                    GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerID));
        }
        catch (ArithmeticException e)
        {
            // If there is an overflow adding the player's available blocks, use max value.
            remainingBlocks = Integer.MAX_VALUE;
        }
        try
        {
            for (int i = 0; i < this.getClaims().size(); i++)
            {
                Claim claim = this.getClaims().get(i);
                remainingBlocks = Math.subtractExact(remainingBlocks, claim.getArea());
            }
        }
        catch (ArithmeticException e)
        {
            // If there is an overflow subtracting the player's claims, they don't have any blocks left.
            return 0;
        }

        return remainingBlocks;
    }

    //don't load data from secondary storage until it's needed
    public synchronized int getAccruedClaimBlocks()
    {
        if (this.accruedClaimBlocks == null) this.loadDataFromSecondaryStorage();

        //update claim blocks with any he has accrued during his current play session
        if (this.newlyAccruedClaimBlocks > 0)
        {
            int accruedLimit = this.getAccruedClaimBlocksLimit();

            //if over the limit before adding blocks, leave it as-is, because the limit may have changed AFTER he accrued the blocks
            if (this.accruedClaimBlocks < accruedLimit)
            {
                //move any in the holding area
                int newTotal = this.accruedClaimBlocks + this.newlyAccruedClaimBlocks;

                //respect limits
                this.accruedClaimBlocks = Math.min(newTotal, accruedLimit);
            }

            this.newlyAccruedClaimBlocks = 0;
            return this.accruedClaimBlocks;
        }

        return accruedClaimBlocks;
    }

    public void setAccruedClaimBlocks(Integer accruedClaimBlocks)
    {
        this.accruedClaimBlocks = accruedClaimBlocks;
        this.newlyAccruedClaimBlocks = 0;
    }

    public int getBonusClaimBlocks()
    {
        if (this.bonusClaimBlocks == null) this.loadDataFromSecondaryStorage();
        return bonusClaimBlocks;
    }

    public void setBonusClaimBlocks(Integer bonusClaimBlocks)
    {
        this.bonusClaimBlocks = bonusClaimBlocks;
    }

    private void loadDataFromSecondaryStorage()
    {
        //reach out to secondary storage to get any data there
        PlayerData storageData = GriefPrevention.instance.dataStore.getPlayerDataFromStorage(this.playerID);

        if (this.accruedClaimBlocks == null)
        {
            if (storageData.accruedClaimBlocks != null)
            {
                this.accruedClaimBlocks = storageData.accruedClaimBlocks;

                //ensure at least minimum accrued are accrued (in case of settings changes to increase initial amount)
                if (GriefPrevention.instance.config_advanced_fixNegativeClaimblockAmounts
                        && (this.accruedClaimBlocks < GriefPrevention.instance.config_claims_initialBlocks))
                {
                    this.accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
                }

            }
            else
            {
                this.accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
            }
        }

        if (this.bonusClaimBlocks == null)
        {
            if (storageData.bonusClaimBlocks != null)
            {
                this.bonusClaimBlocks = storageData.bonusClaimBlocks;
            }
            else
            {
                this.bonusClaimBlocks = 0;
            }
        }
    }

    public Vector<Claim> getClaims()
    {
        if (this.claims == null)
        {
            this.claims = new Vector<>();

            //find all the claims belonging to this player and note them for future reference
            DataStore dataStore = GriefPrevention.instance.dataStore;
            int totalClaimsArea = 0;
            for (int i = 0; i < dataStore.claims.size(); i++)
            {
                Claim claim = dataStore.claims.get(i);
                if (!claim.inDataStore)
                {
                    Claim remove = dataStore.claims.remove(i--);
                    dataStore.claimIDMap.remove(remove.getID());
                    for (Claim child : remove.children)
                    {
                        dataStore.claimIDMap.remove(child.getID());
                    }
                    continue;
                }
                if (playerID.equals(claim.ownerID))
                {
                    this.claims.add(claim);
                    dataStore.claimIDMap.put(claim.getID(), claim);
                    for (Claim child : claim.children)
                    {
                        dataStore.claimIDMap.put(child.getID(), child);
                    }
                    totalClaimsArea += claim.getArea();
                }
            }

            //ensure player has claim blocks for his claims, and at least the minimum accrued
            this.loadDataFromSecondaryStorage();

            //if total claimed area is more than total blocks available
            int totalBlocks = this.accruedClaimBlocks + this.getBonusClaimBlocks()
                    + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerID);
            if (GriefPrevention.instance.config_advanced_fixNegativeClaimblockAmounts && totalBlocks < totalClaimsArea)
            {
                OfflinePlayer player = GriefPrevention.instance.getServer().getOfflinePlayer(this.playerID);
                GriefPrevention.AddLogEntry(
                        player.getName() + " has more claimed land than blocks available.  Adding blocks to fix.",
                        CustomLogEntryTypes.Debug,
                        true);
                GriefPrevention.AddLogEntry(
                        player.getName() + " Accrued blocks: " + this.getAccruedClaimBlocks() + " Bonus blocks: "
                                + this.getBonusClaimBlocks(),
                        CustomLogEntryTypes.Debug,
                        true);
                GriefPrevention.AddLogEntry(
                        "Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea,
                        CustomLogEntryTypes.Debug,
                        true);
                for (Claim claim : this.claims)
                {
                    if (!claim.inDataStore) continue;
                    GriefPrevention
                            .AddLogEntry(
                                    GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " // "
                                            + GriefPrevention.getfriendlyLocationString(
                                                    claim.getGreaterBoundaryCorner())
                                            + " = " + claim.getArea(),
                                    CustomLogEntryTypes.Debug,
                                    true);
                }

                //try to fix it by adding to accrued blocks
                this.accruedClaimBlocks = totalClaimsArea; //Set accrued blocks to equal total claims
                int accruedLimit = this.getAccruedClaimBlocksLimit();
                this.accruedClaimBlocks = Math.min(accruedLimit, this.accruedClaimBlocks); //set accrued blocks to maximum limit, if it's smaller
                GriefPrevention
                        .AddLogEntry("New accrued blocks: " + this.accruedClaimBlocks, CustomLogEntryTypes.Debug, true);

                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = this.accruedClaimBlocks + this.getBonusClaimBlocks()
                        + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerID);
                GriefPrevention.AddLogEntry("New total blocks: " + totalBlocks, CustomLogEntryTypes.Debug, true);

                //if that didn't fix it, then make up the difference with bonus blocks
                if (totalBlocks < totalClaimsArea)
                {
                    int bonusBlocksToAdd = totalClaimsArea - totalBlocks;
                    this.bonusClaimBlocks += bonusBlocksToAdd;
                    GriefPrevention.AddLogEntry(
                            "Accrued blocks weren't enough. Adding " + bonusBlocksToAdd + " bonus blocks.",
                            CustomLogEntryTypes.Debug,
                            true);
                }
                GriefPrevention.AddLogEntry(
                        player.getName() + " Accrued blocks: " + this.getAccruedClaimBlocks() + " Bonus blocks: "
                                + this.getBonusClaimBlocks() + " Group Bonus Blocks: "
                                + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerID),
                        CustomLogEntryTypes.Debug,
                        true);
                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = this.accruedClaimBlocks + this.getBonusClaimBlocks()
                        + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.playerID);
                GriefPrevention.AddLogEntry(
                        "Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea,
                        CustomLogEntryTypes.Debug,
                        true);
                GriefPrevention.AddLogEntry(
                        "Remaining claim blocks to use: " + this.getRemainingClaimBlocks() + " (should be 0)",
                        CustomLogEntryTypes.Debug,
                        true);
            }
        }

        for (int i = 0; i < this.claims.size(); i++)
        {
            if (!claims.get(i).inDataStore)
            {
                claims.remove(i--);
            }
        }

        return claims;
    }

    //Limit can be changed by addons
    public int getAccruedClaimBlocksLimit()
    {
        if (this.AccruedClaimBlocksLimit < 0) return GriefPrevention.instance.config_claims_maxAccruedBlocks_default;
        return this.AccruedClaimBlocksLimit;
    }

    public void setAccruedClaimBlocksLimit(int limit)
    {
        this.AccruedClaimBlocksLimit = limit;
    }

    public void accrueBlocks(int howMany)
    {
        this.newlyAccruedClaimBlocks += howMany;
    }

    public @Nullable BoundaryVisualization getVisibleBoundaries()
    {
        return visibleBoundaries;
    }

    public void setVisibleBoundaries(@Nullable BoundaryVisualization visibleBoundaries)
    {
        if (this.visibleBoundaries != null)
        {
            this.visibleBoundaries.revert(Bukkit.getPlayer(playerID));
        }

        this.visibleBoundaries = visibleBoundaries;
    }

}
