package me.ryanhamshire.GriefPrevention.events;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import com.griefprevention.events.BoundaryVisualizationEvent;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.Visualization;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Player} receives {@link Claim} visuals.
 *
 * @deprecated Replaced with {@link BoundaryVisualizationEvent}
 */
@Deprecated(forRemoval = true, since = "16.18")
public class VisualizationEvent extends PlayerEvent
{

    private final @Nullable Visualization visualization;

    private final @NotNull @Unmodifiable Collection<Claim> claims;

    private final boolean showSubdivides;

    private final boolean visualizingNearbyClaims;

    /**
     * Construct a new {@code VisualizationEvent} for a single {@link Claim} and its children.
     *
     * @param player the {@link Player} receiving visuals
     * @param visualization the {@link Visualization} to send
     * @param claim the {@code Claim} being visualized with subdivisions
     */
    public VisualizationEvent(@NotNull Player player, @Nullable Visualization visualization, @NotNull Claim claim)
    {
        super(player);
        this.visualization = visualization;
        this.claims = Collections.singleton(claim);
        this.showSubdivides = true;
        this.visualizingNearbyClaims = false;
    }

    /**
     * Construct a new {@code VisualizationEvent} for multiple {@link Claim Claims}.
     *
     * @param player the {@link Player} receiving visuals
     * @param visualization the {@link Visualization} to send
     * @param claims the {@code Claims} being visualized without subdivisions
     */
    public VisualizationEvent(@NotNull Player player, @Nullable Visualization visualization,
            @NotNull Collection<Claim> claims)
    {
        this(player, visualization, claims, false);
    }

    /**
     * Construct a new {@code VisualizationEvent} for multiple {@link Claim Claims}.
     *
     * @param player the {@link Player} receiving visuals
     * @param visualization the {@link Visualization} to send
     * @param claims the {@code Claims} being visualized without subdivisions
     * @param visualizingNearbyClaims whether the visualization includes area claims or just the target location
     */
    public VisualizationEvent(@NotNull Player player, @Nullable Visualization visualization,
            @NotNull Collection<Claim> claims, boolean visualizingNearbyClaims)
    {
        super(player);
        this.visualization = visualization;
        this.claims = Collections.unmodifiableCollection(new HashSet<>(claims));
        this.showSubdivides = false;
        this.visualizingNearbyClaims = visualizingNearbyClaims;
    }

    /**
     * Get the {@link Visualization}. May be {@code null} if being removed.
     *
     * @return the {@code Visualization} object
     */
    public @Nullable Visualization getVisualization()
    {
        return visualization;
    }

    /**
     * Get the {@link Claim Claims} displayed by the {@link Visualization}.
     *
     * <p>The {@link Collection} is unmodifiable, manipulation is not allowed.
     *
     * @return an unmodifiable {@code Collection} of {@code Claims}
     */
    public @NotNull @Unmodifiable Collection<Claim> getClaims()
    {
        return claims;
    }

    /**
     * Check whether {@link Claim} subdivisions (children) are being displayed.
     *
     * @return true if subdivisions are displayed
     */
    public boolean showSubdivides()
    {
        return showSubdivides;
    }

    /**
     * Check whether the {@link Player} is visualizing nearby {@link Claim Claims}
     * or just the target location.
     *
     * @return true if nearby {@code Claims} are displayed
     */
    public boolean isVisualizingNearbyClaims()
    {
        return visualizingNearbyClaims;
    }

    // Listenable event requirements
    private static final HandlerList HANDLERS = new HandlerList() {

        private final Set<String> nags = new HashSet<>();

        @Override
        public synchronized void register(@NotNull RegisteredListener listener)
        {
            Plugin plugin = listener.getPlugin();

            if (nags.add(plugin.getName()))
                plugin.getLogger().severe(
                        () -> plugin.getName()
                                + " registered a listener for the now-defunct VisualizationEvent. Please ask "
                                + plugin.getDescription().getAuthors()
                                + " to update to the BoundaryVisualizationEvent.");
        }
    };

    public static HandlerList getHandlerList()
    {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers()
    {
        return HANDLERS;
    }

}
