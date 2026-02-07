package com.griefprevention.visualization;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import com.griefprevention.events.BoundaryVisualizationEvent;
import com.griefprevention.util.IntVector;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A representation of a system for displaying rectangular {@link Boundary Boundaries} to {@link Player Players}.
 * This is used to display claim areas, conflicting claims, and more.
 */
public abstract class BoundaryVisualization
{

    private final Collection<Boundary> elements = new HashSet<>();
    protected final @NotNull World world;
    protected final @NotNull IntVector visualizeFrom;
    protected final int height;

    /**
     * Construct a new {@code BoundaryVisualization}.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     */
    protected BoundaryVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height)
    {
        this.world = world;
        this.visualizeFrom = visualizeFrom;
        this.height = height;
    }

    /**
     * Check if a {@link Player} can visualize the {@code BoundaryVisualization}.
     *
     * @param player the visualization target
     * @return true if able to visualize
     */
    @Contract("null -> false")
    protected boolean canVisualize(@Nullable Player player) {
        return player != null && player.isOnline() && !elements.isEmpty() && Objects.equals(world, player.getWorld());
    }

    /**
     * Apply the {@code BoundaryVisualization} to a {@link Player}.
     *
     * @param player the visualization target
     * @param playerData the {@link PlayerData} of the visualization target
     */
    protected void apply(@NotNull Player player, @NotNull PlayerData playerData)
    {
        // Remember the visualization so it can be reverted.
        playerData.setVisibleBoundaries(this);

        // Apply all visualization elements.
        elements.forEach(element -> draw(player, element));

        // Schedule automatic reversion.
        scheduleRevert(player, playerData);
    }

    /**
     * Draw a {@link Boundary} in the visualization for a {@link Player}.
     *
     * @param player the visualization target
     * @param boundary the {@code Boundary} to draw
     */
    protected abstract void draw(@NotNull Player player, @NotNull Boundary boundary);

    /**
     * Schedule automatic reversion of the visualization.
     *
     * <p>Some implementations may automatically revert without additional help and may wish to override this method to
     * prevent extra task scheduling.</p>
     *
     * @param player the visualization target
     * @param playerData the {@link PlayerData} of the visualization target
     */
    protected void scheduleRevert(@NotNull Player player, @NotNull PlayerData playerData)
    {
        GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(
                GriefPrevention.instance,
                () -> {
                    // Only revert if this is the active visualization.
                    if (playerData.getVisibleBoundaries() == this) playerData.setVisibleBoundaries(null);
                },
                20L * 60);
    }

    /**
     * Revert the visualization for a {@link Player}.
     *
     * @param player the visualization target
     */
    public void revert(@Nullable Player player)
    {
        // If the player cannot visualize the blocks, they should already be effectively reverted.
        if (!canVisualize(player))
        {
            return;
        }

        // Revert data as necessary for any sent elements.
        elements.forEach(element -> erase(player, element));
    }

    /**
     * Erase a {@link Boundary} in the visualization for a {@link Player}.
     *
     * @param player the visualization target
     * @param boundary the {@code Boundary} to erase
     */
    protected abstract void erase(@NotNull Player player, @NotNull Boundary boundary);

    /**
     * Helper method for quickly visualizing an area.
     *
     * @param player the {@link Player} visualizing the area
     * @param boundingBox the {@link BoundingBox} being visualized
     * @param type the {@link VisualizationType type of visualization}
     */
    public static void visualizeArea(
            @NotNull Player player,
            @NotNull BoundingBox boundingBox,
            @NotNull VisualizationType type) {
        BoundaryVisualizationEvent event = new BoundaryVisualizationEvent(player,
                Set.of(new Boundary(boundingBox, type)),
                player.getEyeLocation().getBlockY());
        callAndVisualize(event);
    }

    /**
     * Helper method for quickly visualizing a claim and all its children.
     *
     * @param player the {@link Player} visualizing the area
     * @param claim the {@link Claim} being visualized
     * @param type the {@link VisualizationType type of visualization}
     */
    public static void visualizeClaim(
            @NotNull Player player,
            @NotNull Claim claim,
            @NotNull VisualizationType type)
    {
        visualizeClaim(player, claim, type, player.getEyeLocation().getBlockY());
    }

    /**
     * Helper method for quickly visualizing a claim and all its children.
     *
     * @param player the {@link Player} visualizing the area
     * @param claim the {@link Claim} being visualized
     * @param type the {@link VisualizationType type of visualization}
     * @param block the {@link Block} on which the visualization was initiated
     */
    public static void visualizeClaim(
            @NotNull Player player,
            @NotNull Claim claim,
            @NotNull VisualizationType type,
            @NotNull Block block)
    {
        visualizeClaim(player, claim, type, block.getY());
    }

    /**
     * Helper method for quickly visualizing a claim and all its children.
     *
     * @param player the {@link Player} visualizing the area
     * @param claim the {@link Claim} being visualized
     * @param type the {@link VisualizationType}
     * @param height the height at which the visualization was initiated
     */
    private static void visualizeClaim(
            @NotNull Player player,
            @NotNull Claim claim,
            @NotNull VisualizationType type,
            int height)
    {
        BoundaryVisualizationEvent event = new BoundaryVisualizationEvent(player, defineBoundaries(claim, type), height);
        callAndVisualize(event);
    }

    /**
     * Define {@link Boundary Boundaries} for a claim and its children.
     *
     * @param claim the {@link Claim}
     * @param type the {@link VisualizationType}
     * @return the resulting {@code Boundary} values
     */
    private static Collection<Boundary> defineBoundaries(Claim claim, VisualizationType type)
    {
        if (claim == null) return Set.of();

        // When targeting a 3D claim, visualize it (and its descendants) without promoting to the parent
        // so inner 3D subdivisions render correctly.
        if (claim.is3D())
        {
            Set<Boundary> boundaries = new HashSet<>();
            addClaimWithDescendants(boundaries, claim, VisualizationType.SUBDIVISION_3D);
            return boundaries;
        }

        // For non-3D claims, promote to the highest non-3D ancestor so all subdivisions are rendered together.
        Claim root = claim;
        while (root.parent != null && !root.parent.is3D())
        {
            root = root.parent;
        }

        // Correct visualization type for claim type for simplicity.
        if (type == VisualizationType.CLAIM && root.isAdminClaim()) type = VisualizationType.ADMIN_CLAIM;

        // Gather all boundaries. It's important that children override parent so
        // that users can always find children, no matter how oddly sized or positioned.
        Set<Boundary> boundaries = new HashSet<>();
        boundaries.add(new Boundary(root, type));
        for (Claim child : root.children)
        {
            if (!child.inDataStore) continue;

            VisualizationType childType = child.is3D()
                    ? VisualizationType.SUBDIVISION_3D
                    : VisualizationType.SUBDIVISION;

            addClaimWithDescendants(boundaries, child, childType);
        }
        return boundaries;
    }

    /**
     * Recursively add a claim and all its descendants to the boundaries set.
     *
     * @param boundaries the set to add to
     * @param claim the claim to add
     * @param type the visualization type
     */
    private static void addClaimWithDescendants(Set<Boundary> boundaries, Claim claim, VisualizationType type)
    {
        boundaries.add(new Boundary(claim, type));
        for (Claim child : claim.children)
        {
            if (!child.inDataStore) continue;
            VisualizationType childType = child.is3D()
                    ? VisualizationType.SUBDIVISION_3D
                    : VisualizationType.SUBDIVISION;
            addClaimWithDescendants(boundaries, child, childType);
        }
    }

    /**
     * Helper method for quickly visualizing a collection of nearby claims.
     *
     * @param player the {@link Player} visualizing the area
     * @param claims the {@link Claim Claims} being visualized
     * @param height the height at which the visualization was initiated
     */
    public static void visualizeNearbyClaims(
            @NotNull Player player,
            @NotNull Collection<Claim> claims,
            int height)
    {
        BoundaryVisualizationEvent event = new BoundaryVisualizationEvent(
                player,
                claims.stream().map(claim -> new Boundary(
                        claim,
                        claim.isAdminClaim() ? VisualizationType.ADMIN_CLAIM :  VisualizationType.CLAIM))
                        .collect(Collectors.toSet()),
                height);
        callAndVisualize(event);
    }

    /**
     * Call a {@link BoundaryVisualizationEvent} and use the resulting values to create and apply a visualization.
     *
     * @param event the {@code BoundaryVisualizationEvent}
     */
    public static void callAndVisualize(@NotNull BoundaryVisualizationEvent event) {
        Bukkit.getPluginManager().callEvent(event);

        Player player = event.getPlayer();
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        BoundaryVisualization currentVisualization = playerData.getVisibleBoundaries();

        Collection<Boundary> boundaries = event.getBoundaries();
        boundaries.removeIf(Objects::isNull);

        if (currentVisualization != null
                && currentVisualization.elements.equals(boundaries)
                && currentVisualization.visualizeFrom.distanceSquared(event.getCenter()) < 165)
        {
            // Ignore visualizations with duplicate boundaries if the viewer has moved fewer than 15 blocks.
            return;
        }

        BoundaryVisualization visualization = event.getProvider().create(player.getWorld(), event.getCenter(), event.getHeight());
        visualization.elements.addAll(boundaries);

        // If they have a visualization active, clear it first.
        playerData.setVisibleBoundaries(null);

        // If they are online and in the same world as the visualization, display the visualization next tick.
        if (visualization.canVisualize(player))
        {
            GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(
                    GriefPrevention.instance,
                    new DelayedVisualizationTask(visualization, playerData, event),
                    1L);
        }
    }

    private record DelayedVisualizationTask(
            @NotNull BoundaryVisualization visualization,
            @NotNull PlayerData playerData,
            @NotNull BoundaryVisualizationEvent event)
            implements Runnable
    {

        @Override
        public void run()
        {
            try
            {
                visualization.apply(event.getPlayer(), playerData);
            }
            catch (Exception exception)
            {
                if (event.getProvider() == BoundaryVisualizationEvent.DEFAULT_PROVIDER)
                {
                    // If the provider is our own, log normally.
                    GriefPrevention.instance.getLogger().log(Level.WARNING, "Exception visualizing claim", exception);
                    return;
                }

                // Otherwise, add an extra hint that the problem is not with GP.
                GriefPrevention.AddLogEntry(
                        String.format(
                                "External visualization provider %s caused %s: %s",
                                event.getProvider().getClass().getName(),
                                exception.getClass().getName(),
                                exception.getCause()),
                        CustomLogEntryTypes.Exception);
                GriefPrevention.instance.getLogger().log(
                        Level.WARNING,
                        "Exception visualizing claim using external provider",
                        exception);

                // Fall through to default provider.
                BoundaryVisualization fallback = BoundaryVisualizationEvent.DEFAULT_PROVIDER
                        .create(event.getPlayer().getWorld(), event.getCenter(), event.getHeight());
                event.getBoundaries().stream().filter(Objects::nonNull).forEach(fallback.elements::add);
                fallback.apply(event.getPlayer(), playerData);
            }
        }

    }

}
