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

import java.util.Collection;

import org.bukkit.entity.Player;

import me.ryanhamshire.GriefPrevention.events.AccrueClaimBlocksEvent;

//FEATURE: give players claim blocks for playing, as long as they're not away from their computer

//runs every 5 minutes in the main thread, grants blocks per hour / 12 to each online player who appears to be actively playing
class DeliverClaimBlocksTask implements Runnable
{

    private final Player player;

    private final GriefPrevention instance;

    public DeliverClaimBlocksTask(Player player, GriefPrevention instance)
    {
        this.player = player;
        this.instance = instance;
    }

    @Override
    public void run()
    {
        //if no player specified, this task will create a player-specific task for each online player, scheduled one tick apart
        if (this.player == null)
        {
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>) GriefPrevention.instance.getServer().getOnlinePlayers();

            long i = 0;
            for (Player onlinePlayer : players)
            {
                DeliverClaimBlocksTask newTask = new DeliverClaimBlocksTask(onlinePlayer, instance);
                instance.getServer().getScheduler().scheduleSyncDelayedTask(instance, newTask, i++);
            }

            return; //tasks started for each player
        }

        //deliver claim blocks to the specified player
        if (!this.player.isOnline())
        {
            return; //player is not online to receive claim blocks
        }

        DataStore dataStore = instance.dataStore;
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());

        // check if player is idle (considered idle if player's facing direction has not changed)
        boolean isIdle = playerData.lastAfkCheckLocation != null
                && playerData.lastAfkCheckLocation.getDirection().equals(player.getLocation().getDirection());

        //remember current location for next time
        playerData.lastAfkCheckLocation = player.getLocation();

        try
        {
            //determine how fast blocks accrue for this player; can be modified by addons
            int accrualRate = instance.config_claims_blocksAccruedPerHour_default;

            //fire event for addons
            AccrueClaimBlocksEvent event = new AccrueClaimBlocksEvent(player, accrualRate, isIdle);
            instance.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled())
            {
                //event is initialized as canceled if player is idle
                if (event.isIdle())
                    GriefPrevention.AddLogEntry(
                            player.getName() + " wasn't active enough to accrue claim blocks this round.",
                            CustomLogEntryTypes.Debug,
                            true);
                else
                    GriefPrevention.AddLogEntry(
                            player.getName() + " claim block delivery was canceled by another plugin.",
                            CustomLogEntryTypes.Debug,
                            true);
                return; //event was cancelled
            }

            //set actual accrual
            accrualRate = event.getBlocksToAccrue();
            if (accrualRate < 0) accrualRate = 0;
            playerData.accrueBlocks(accrualRate);
            GriefPrevention.AddLogEntry(
                    "Delivering " + event.getBlocksToAccrue() + " blocks to " + player.getName(),
                    CustomLogEntryTypes.Debug,
                    true);

            //intentionally NOT saving data here to reduce overall secondary storage access frequency
            //many other operations will cause this player's data to save, including his eventual logout
            //dataStore.savePlayerData(player.getUniqueIdentifier(), playerData);
        }
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
            e.printStackTrace();
        }
    }
}
