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

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//sends a message to a player
//used to send delayed messages, for example help text triggered by a player's chat
class SendPlayerMessageTask implements Runnable
{

    private final Player player;

    private final ChatColor color;

    private final String message;

    public SendPlayerMessageTask(@Nullable Player player, @NotNull ChatColor color, @NotNull String message)
    {
        this.player = player;
        this.color = color;
        this.message = message;
    }

    @Override
    public void run()
    {
        if (player == null)
        {
            GriefPrevention.sendMessage(null, this.color, this.message);
            return;
        }

        //if the player is dead, save it for after his respawn
        if (this.player.isDead())
        {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(this.player.getUniqueId());
            playerData.messageOnRespawn = this.color + this.message;
        }

        //otherwise send it immediately
        else
        {
            GriefPrevention.sendMessage(this.player, this.color, this.message);
        }
    }
}
