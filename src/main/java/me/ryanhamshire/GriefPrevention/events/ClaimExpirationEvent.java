package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} for when a {@link Claim} is deleted due to owner inactivity.
 *
 * <p>If cancelled, deletion will be prevented.
 */
public class ClaimExpirationEvent extends ClaimEvent implements Cancellable
{

    /**
     * Construct a new {@code ClaimExpirationEvent}.
     *
     * @param claim the {@link Claim} expiring
     */
    public ClaimExpirationEvent(@NotNull Claim claim)
    {
        super(claim);
    }

    // Listenable event requirements
    private static final HandlerList HANDLERS = new HandlerList();

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

}
