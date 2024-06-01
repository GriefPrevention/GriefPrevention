package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link org.bukkit.event.Event Event} called when a player starts the claim process by clicking with the claim tool.
 *
 * <p>If cancelled, the value returned by getMaxClaims() should not be used.
 *
 * @author codespunk on 5/04/2024.
 */
public class ClaimStartEvent extends Event implements Cancellable
{

    private final @NotNull Player player;
    private @NotNull Boolean claimsEnabledForWorld;
    private  @NotNull Integer maxClaims;

    /**
     * Construct a new {@code ClaimStartEvent}.
     *
     * @param maxClaims the maximum number of claims allowed for this player
     * @param player the {@link Player} starting the claim process
     */
    public ClaimStartEvent(@NotNull Boolean claimsEnabledForWorld, @NotNull Integer maxClaims, @NotNull Player player)
    {
        this.player = player;
        this.claimsEnabledForWorld = claimsEnabledForWorld;
        this.maxClaims = maxClaims;
    }

    /**
     * Get the {@link Player} who initiated the claim process with the claim tool.
     *
     * @return the player starting the claim process
     */
    public @NotNull Player getPlayer()
    {
        return player;
    }

    /**
     * Get whether claims are enabled for the player in their current world
     *
     * @return whether claims are enabled for this world
     */
    public @NotNull Boolean isEnabledForWorld()
    {
        return claimsEnabledForWorld;
    }

    /**
     * Get the maximum number of claims allowed for this player
     *
     * @return the maximum allowed claims
     */
    public @NotNull Integer getMaxClaims()
    {
        return maxClaims;
    }

    // Listenable event requirements
    private static final @NotNull HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList()
    {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers()
    {
        return HANDLERS;
    }

    // Cancellable requirements
    private boolean cancelled = false;

    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled)
    {
        this.cancelled = cancelled;
    }

    /**
     * Set whether claims are enabled for the player in their current world
     */
    public void setEnabledForWorld(@NotNull Boolean isEnabled)
    {
        this.claimsEnabledForWorld = isEnabled;
    }

    /**
     * Set the maximum number of claims allowed for this player
     */
    public void setMaxClaims(@NotNull Integer maxClaims)
    {
        this.maxClaims = maxClaims;
    }

}
