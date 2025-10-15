/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2016 Ryan Hamshire

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

//sends a message to all online players
//used to send delayed messages, for example a quit message after the player has been gone a while
class BroadcastMessageTask implements Runnable
{

    private final String message;

    public BroadcastMessageTask(String message)
    {
        this.message = message;
    }

    @Override
    public void run()
    {
        Bukkit.getServer().broadcastMessage(this.message);
    }
}
