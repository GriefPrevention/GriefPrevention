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
import org.bukkit.entity.Player;

//tries to rescue a trapped player from a claim where he doesn't have permission to save himself
//related to the /trapped slash command
//this does run in the main thread, so it's okay to make non-thread-safe calls
class PlayerRescueTask implements Runnable
{

    //original location where /trapped was used
    private final Location location;

    //rescue destination, may be decided at instantiation or at execution
    private Location destination;

    //player data
    private final Player player;

    public PlayerRescueTask(Player player, Location location, Location destination)
    {
        this.player = player;
        this.location = location;
        this.destination = destination;
    }

    @Override
    public void run()
    {
        //if he logged out, don't do anything
        if (!player.isOnline()) return;

        //he no longer has a pending /trapped slash command, so he can try to use it again now
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        playerData.pendingTrapped = false;

        //if the player moved three or more blocks from where he used /trapped, admonish him and don't save him
        if (!player.getLocation().getWorld().equals(this.location.getWorld())
                || player.getLocation().distance(this.location) > 3)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.RescueAbortedMoved);
            return;
        }

        //otherwise find a place to teleport him
        if (this.destination == null)
        {
            this.destination = GriefPrevention.instance.ejectPlayer(this.player);
        }
        else
        {
            player.teleport(this.destination);
        }

        //log entry, in case admins want to investigate the "trap"
        GriefPrevention.AddLogEntry(
                "Rescued trapped player " + player.getName() + " from "
                        + GriefPrevention.getfriendlyLocationString(this.location) + " to "
                        + GriefPrevention.getfriendlyLocationString(this.destination) + ".");
    }
}
