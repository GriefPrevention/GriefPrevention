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
import org.bukkit.block.data.BlockData;

import com.griefprevention.visualization.BlockElement;

/**
 * Represents a fake {@link org.bukkit.block.Block Block} sent to a
 * {@link org.bukkit.entity.Player Player} as part of a {@link Visualization}
 * @deprecated replaced by {@link BlockElement}
 */
@Deprecated(forRemoval = true, since = "16.18")
public class VisualizationElement
{

    public Location location;

    public BlockData visualizedBlock;

    public BlockData realBlock;

    public VisualizationElement(Location location, BlockData visualizedBlock, BlockData realBlock)
    {
        this.location = location;
        this.visualizedBlock = visualizedBlock;
        this.realBlock = realBlock;
    }
}
