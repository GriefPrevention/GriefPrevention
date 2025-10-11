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

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.ryanhamshire.GriefPrevention.events.PlayerKickBanEvent;

//kicks or bans a player
//need a task for this because async threads (like the chat event handlers) can't kick or ban.
//but they CAN schedule a task to run in the main thread to do that job
class PlayerKickBanTask implements Runnable
{

    //player to kick or ban
    private final Player player;

    //message to send player.
    private final String reason;

    //source of ban
    private final String source;

    //whether to ban
    private final boolean ban;

    public PlayerKickBanTask(Player player, String reason, String source, boolean ban)
    {
        this.player = player;
        this.reason = reason;
        this.source = source;
        this.ban = ban;
    }

    @Override
    public void run()
    {
        PlayerKickBanEvent kickBanEvent = new PlayerKickBanEvent(player, reason, source, ban);
        Bukkit.getPluginManager().callEvent(kickBanEvent);

        if (kickBanEvent.isCancelled())
        {
            return; // cancelled by a plugin
        }

        if (this.ban)
        {
            //ban
            GriefPrevention.banPlayer(this.player, this.reason, this.source);
        }
        else if (this.player.isOnline())
        {
            this.player.kickPlayer(this.reason);
        }
    }
}
