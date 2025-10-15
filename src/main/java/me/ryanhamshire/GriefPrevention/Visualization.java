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

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.griefprevention.events.BoundaryVisualizationEvent;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;

import me.ryanhamshire.GriefPrevention.util.BoundingBox;

//represents a visualization sent to a player
//FEATURE: to show players visually where claim boundaries are, we send them fake block change packets
//the result is that those players see new blocks, but the world hasn't been changed.  other players can't see the new blocks, either.

/**
 * @deprecated superseded by {@link BoundaryVisualization}
 */
@Deprecated(forRemoval = true, since = "16.18")
public class Visualization
{

    @Deprecated(forRemoval = true, since = "16.18")
    public ArrayList<VisualizationElement> elements = new ArrayList<>();

    private final Collection<Boundary> boundaries = new ArrayList<>();

    @Deprecated(forRemoval = true, since = "16.18")
    public Visualization() { }

    /**
     * Send a visualization to a {@link Player}.
     *
     * @deprecated Create a {@link BoundaryVisualizationEvent} and call
     * {@link BoundaryVisualization#callAndVisualize(BoundaryVisualizationEvent)}
     * @param player the {@code Player}
     * @param visualization the {@code Visualization}
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public static void Apply(Player player, Visualization visualization)
    {
        if (player != null) apply(player, visualization);
    }

    /**
     * Revert the active visualization for a {@link Player}.
     *
     * @deprecated Use {@link PlayerData#setVisibleBoundaries(BoundaryVisualization)} with a {@code null} parameter to
     * revert current visualization.
     * @param player the {@code Player}
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public static void Revert(Player player)
    {
        if (player != null && player.isOnline())
            GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
    }

    /**
     * convenience method to build a visualization from a claim
     * visualizationType determines the style (gold blocks, silver, red, diamond, etc)
     * @deprecated Use {@link BoundaryVisualization#visualizeClaim(Player, Claim, VisualizationType)} or
     * {@link BoundaryVisualization#visualizeClaim(Player, Claim, VisualizationType, Block)}
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public static Visualization FromClaim(
            Claim claim,
            int height,
            me.ryanhamshire.GriefPrevention.VisualizationType visualizationType,
            Location locality)
    {
        if (claim.parent != null) claim = claim.parent;

        VisualizationType type = visualizationType.convert();
        if (type == VisualizationType.CLAIM && claim.isAdminClaim()) type = VisualizationType.ADMIN_CLAIM;

        Visualization visualization = new Visualization();
        visualization.boundaries.add(new Boundary(claim, type));
        visualization.boundaries.addAll(
                claim.children.stream().map(
                        child -> new Boundary(child, com.griefprevention.visualization.VisualizationType.SUBDIVISION))
                        .collect(Collectors.toUnmodifiableSet()));

        return visualization;
    }

    /**
     * Send a visualization to a {@link Player}.
     *
     * @deprecated Create a {@link BoundaryVisualizationEvent} and call
     * {@link BoundaryVisualization#callAndVisualize(BoundaryVisualizationEvent)}
     * @param player the {@code Player}
     * @param visualization the {@code Visualization}
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public static void apply(@NotNull Player player, @Nullable Visualization visualization)
    {
        // If the visualization is null, revert existing visualizations.
        if (visualization == null)
        {
            if (player.isOnline())
            {
                GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
            }
            return;
        }

        BoundaryVisualization.callAndVisualize(
                new BoundaryVisualizationEvent(player, visualization.boundaries, player.getEyeLocation().getBlockY()));
    }

    /**
     * adds a claim's visualization to the current visualization
     * handy for combining several visualizations together, as when visualization a top level claim with several subdivisions inside
     * locality is a performance consideration.  only create visualization blocks for around 100 blocks of the locality
     * @deprecated Add all desired elements to the list of boundaries ({@link BoundaryVisualizationEvent#getBoundaries()})
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public void addClaimElements(
            Claim claim,
            int height,
            me.ryanhamshire.GriefPrevention.VisualizationType visualizationType,
            Location locality)
    {
        this.boundaries.add(new Boundary(claim, visualizationType.convert()));
    }

    /**
     * adds a claim's visualization to the current visualization
     * handy for combining several visualizations together, as when visualization a top level claim with several subdivisions inside
     * locality is a performance consideration.  only create visualization blocks for around 100 blocks of the locality
     * @deprecated Add all desired elements to the list of boundaries ({@link BoundaryVisualizationEvent#getBoundaries()})
     */
    @Deprecated(forRemoval = true, since = "16.18")
    //adds a general claim cuboid (represented by min and max) visualization to the current visualization
    public void addClaimElements(
            Location min,
            Location max,
            Location locality,
            int height,
            BlockData cornerBlockData,
            BlockData accentBlockData,
            int STEP)
    {
        this.boundaries.add(
                new Boundary(new BoundingBox(min, max),
                        me.ryanhamshire.GriefPrevention.VisualizationType.ofBlockData(accentBlockData)));
    }

    /**
     * @deprecated Add all desired elements to the list of boundaries ({@link BoundaryVisualizationEvent#getBoundaries()})
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public static Visualization fromClaims(
            Iterable<Claim> claims,
            int height,
            me.ryanhamshire.GriefPrevention.VisualizationType type,
            Location locality)
    {
        Visualization visualization = new Visualization();
        claims.forEach(claim -> visualization.addClaimElements(claim, height, type, locality));
        return visualization;
    }

}
