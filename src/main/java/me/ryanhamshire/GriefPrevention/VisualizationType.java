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

import org.bukkit.block.data.BlockData;

/**
 * @deprecated {@link com.griefprevention.visualization.VisualizationType}
 */
@Deprecated(forRemoval = true, since = "16.18")
public enum VisualizationType {

    Claim, Subdivision, ErrorClaim, AdminClaim;

    @Deprecated(forRemoval = true, since = "16.18")
    com.griefprevention.visualization.VisualizationType convert()
    {
        return switch (this)
        {
            case Claim -> com.griefprevention.visualization.VisualizationType.CLAIM;
            case Subdivision -> com.griefprevention.visualization.VisualizationType.SUBDIVISION;
            case ErrorClaim -> com.griefprevention.visualization.VisualizationType.CONFLICT_ZONE;
            case AdminClaim -> com.griefprevention.visualization.VisualizationType.ADMIN_CLAIM;
        };
    }

    @Deprecated(forRemoval = true, since = "16.18")
    static com.griefprevention.visualization.VisualizationType ofBlockData(BlockData accent)
    {
        return switch (accent.getMaterial())
        {
            case WHITE_WOOL -> com.griefprevention.visualization.VisualizationType.SUBDIVISION;
            case NETHERRACK -> com.griefprevention.visualization.VisualizationType.CONFLICT_ZONE;
            case PUMPKIN -> com.griefprevention.visualization.VisualizationType.ADMIN_CLAIM;
            default -> com.griefprevention.visualization.VisualizationType.CLAIM;
        };
    }

}
